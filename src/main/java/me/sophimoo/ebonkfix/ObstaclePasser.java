package me.sophimoo.ebonkfix;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import meteordevelopment.meteorclient.systems.modules.player.Rotation;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Port of ElytraFlyPlusPlus's highway obstacle passer for the ElytraFly Bounce mode.
 * Uses Baritone purely through reflection so the addon works fine when Baritone is not installed.
 * Source: https://github.com/miles352/meteor-stashhunting-addon/blob/1.21.1/src/main/java/com/stash/hunt/modules/ElytraFlyPlusPlus.java
 */
public class ObstaclePasser {
    // Settings (registered by ElytraFlyMixin into ElytraFly's "Obstacle Passer" group)
    public static Setting<Boolean> highwayObstaclePasser;
    public static Setting<Boolean> useCustomStartPos;
    public static Setting<BlockPos> startPos;
    public static Setting<Boolean> awayFromStartPos;
    public static Setting<Double> distance;
    public static Setting<Integer> targetY;
    public static Setting<Boolean> setbackTrigger;
    public static Setting<Boolean> avoidPortalTraps;
    public static Setting<Double> portalAvoidDistance;
    public static Setting<Integer> portalScanWidth;

    private static final double maxDistance = 16 * 5; // 5 chunks forwards

    private static boolean paused = false;
    private static BlockPos portalTrap = null;
    private static int stuckTimer = 0;
    // Concurrent: written from packet-receive (Netty thread), iterated/cleared from onTick (client thread)
    private static final Queue<Long> setbackTimes = new ConcurrentLinkedQueue<>();
    private static BlockPos tempPath = null;
    private static boolean waitingForChunksToLoad = false;
    private static Double travelYaw = null;

    // Lazy-loaded Baritone API (reflection only - no compile-time dependency, no class loading
    // unless Baritone is actually present)
    private static Object baritoneAPI;
    private static boolean baritoneChecked = false;

    private static Object invoke(Object target, String methodName) {
        if (target == null) return null;

        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (Throwable t) {
            // Catch Throwable: a missing or conflicting Baritone (e.g. another mod shading
            // baritone.api) surfaces as linkage errors, not Exceptions - never crash the tick loop.
            return null;
        }
    }

    private static boolean invokeBoolean(Object target, String methodName) {
        return Boolean.TRUE.equals(invoke(target, methodName));
    }

    private static Object getBaritoneAPI() {
        if (!baritoneChecked) {
            baritoneChecked = true;
            try {
                Class<?> baritoneClass = Class.forName("baritone.api.BaritoneAPI");
                baritoneAPI = baritoneClass.getMethod("getProvider").invoke(null);
            } catch (Throwable t) {
                baritoneAPI = null;
            }
        }
        return baritoneAPI;
    }

    private static Object getPrimaryBaritone() {
        return invoke(getBaritoneAPI(), "getPrimaryBaritone");
    }

    private static Object getCustomGoalProcess() {
        return invoke(getPrimaryBaritone(), "getCustomGoalProcess");
    }

    private static Object getElytraProcess() {
        return invoke(getPrimaryBaritone(), "getElytraProcess");
    }

    private static Object getElytraCurrentDestination(Object elytraProcess) {
        return invoke(elytraProcess, "currentDestination");
    }

    private static boolean isBaritonePathing() {
        return invokeBoolean(invoke(getPrimaryBaritone(), "getPathingBehavior"), "isPathing");
    }

    private static void setGoal(Object goalProcess, Object goal) {
        if (goalProcess == null) return;
        try {
            goalProcess.getClass().getMethod("setGoal", Class.forName("baritone.api.pathing.goals.Goal")).invoke(goalProcess, goal);
        } catch (Throwable t) {
            MeteorClient.LOG.warn("Failed to set goal", t);
        }
    }

    private static void setGoalAndPath(Object goalProcess, BlockPos pos) {
        if (goalProcess == null) return;
        try {
            Class<?> goalBlockClass = Class.forName("baritone.api.pathing.goals.GoalBlock");
            Object goal = goalBlockClass.getConstructor(BlockPos.class).newInstance(pos);
            goalProcess.getClass().getMethod("setGoalAndPath", Class.forName("baritone.api.pathing.goals.Goal")).invoke(goalProcess, goal);
        } catch (Throwable t) {
            MeteorClient.LOG.warn("Failed to set goal and path", t);
        }
    }

    // https://github.com/miles352/meteor-stashhunting-addon/blob/1.21.1/src/main/java/com/stash/hunt/modules/ElytraFlyPlusPlus.java#L270-L273
    private static void clearCustomGoalIfIdle() {
        if (getElytraCurrentDestination(getElytraProcess()) == null) {
            setGoal(getCustomGoalProcess(), null);
        }
    }

    public static boolean isPaused() {
        return paused;
    }

    /** Bounce mode on, ElytraFly active in Bounce mode, player present and not in creative flight. */
    private static boolean moduleActive() {
        // No elytra check: the passer must keep working while Baritone swaps chest pieces.
        return BounceSettings.isActive(false);
    }

    private static boolean obstaclePasserActive() {
        return moduleActive() && highwayObstaclePasser != null && highwayObstaclePasser.get();
    }

    // https://github.com/miles352/meteor-stashhunting-addon/blob/1.21.1/src/main/java/com/stash/hunt/modules/ElytraFlyPlusPlus.java#L253-L303
    public static void onActivate(ClientPlayerEntity player) {
        paused = false;
        tempPath = null;
        portalTrap = null;
        stuckTimer = 0;
        setbackTimes.clear();
        waitingForChunksToLoad = false;
        travelYaw = null;

        if (BounceSettings.bounceMode == null) return;
        if (highwayObstaclePasser == null || !highwayObstaclePasser.get()) return;
        if (new Vec3d(player.getX(), 0, player.getZ()).length() < 100) return;

        clearCustomGoalIfIdle();

        if (!useCustomStartPos.get()) {
            startPos.set(player.getBlockPos());
        }

        Modules modules = Modules.get();
        ElytraFly elytraFly = modules == null ? null : modules.get(ElytraFly.class);
        if (elytraFly == null) return;

        if (elytraFly.yawLockMode.get() == Rotation.LockMode.Simple) {
            travelYaw = elytraFly.yaw.get();
        } else if (player.getBlockPos().getSquaredDistance(startPos.get()) < 10_000) {
            // If less than 100 blocks from the start pos, angle calculation may be wrong, so just use players yaw
            travelYaw = angleOnAxis(player.getYaw());
        } else {
            // Otherwise use the angle from the starting position to the players position
            BlockPos directionVec = player.getBlockPos().subtract(startPos.get());
            double angle = Math.toDegrees(Math.atan2(-directionVec.getX(), directionVec.getZ()));
            double angleNormalized = angleOnAxis(angle);
            if (!awayFromStartPos.get()) {
                angleNormalized += 180;
            }
            travelYaw = angleNormalized;
        }
    }

    // https://github.com/miles352/meteor-stashhunting-addon/blob/1.21.1/src/main/java/com/stash/hunt/modules/ElytraFlyPlusPlus.java#L336-L346
    public static void onDeactivate() {
        paused = false;
        tempPath = null;
        waitingForChunksToLoad = false;
        stuckTimer = 0;

        if (BounceSettings.bounceMode != null
            && highwayObstaclePasser != null && highwayObstaclePasser.get()) {
            clearCustomGoalIfIdle();
        }
    }

    public static void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket && obstaclePasserActive() && setbackTrigger.get()) {
            setbackTimes.add(System.currentTimeMillis());
        }
    }

    // https://github.com/miles352/meteor-stashhunting-addon/blob/1.21.1/src/main/java/com/stash/hunt/modules/ElytraFlyPlusPlus.java#L386-L464
    public static void onTick(MinecraftClient mc, ClientPlayerEntity player) {
        if (!moduleActive()) return;

        if (!highwayObstaclePasser.get() || getBaritoneAPI() == null) {
            paused = false;
            return;
        }

        Object goalProcess = getCustomGoalProcess();

        if (tempPath != null && player.getBlockPos().getSquaredDistance(tempPath) < 500) {
            tempPath = null;
            stuckTimer = 0;
            setGoal(goalProcess, null);
        } else if (tempPath != null) {
            paused = true;
            if (!isBaritonePathing()) setGoalAndPath(goalProcess, tempPath);
            return;
        }

        // if still pathing, wait for that to complete
        if (isBaritonePathing()) {
            paused = true;
            return;
        }

        if (setbackTrigger.get()) {
            long cutoff = System.currentTimeMillis() - 2000;
            setbackTimes.removeIf(time -> time < cutoff);
        }

        Vec3d velocity = player.getVelocity();
        if (Math.hypot(velocity.x, velocity.z) < 0.2) {
            stuckTimer++;
        } else {
            stuckTimer = 0;
        }

        // > 100 check needed bc server sends queue coordinates when joining in first tick causing goal coordinates to be set to (0, 0)
        if (new Vec3d(player.getX(), player.getY(), player.getZ()).length() > 100 &&
            (player.getY() < targetY.get() || player.getY() > targetY.get() + 2 || (player.horizontalCollision && !player.collidedSoftly) // collisions / out of highway
            || (portalTrap != null && portalTrap.getSquaredDistance(player.getBlockPos()) < portalAvoidDistance.get() * portalAvoidDistance.get()) // portal trap detection
            || waitingForChunksToLoad // waiting for chunks to load
            || stuckTimer > 20
            || setbackTimes.size() > 2)) {
            waitingForChunksToLoad = false;
            stuckTimer = 0;
            setbackTimes.clear();
            paused = true;
            BlockPos goal = player.getBlockPos();
            double currDistance = distance.get(); // Keep checking farther distances until a goal is found that has a block beneath it

            if (portalTrap != null) {
                currDistance += new Vec3d(player.getX(), player.getY(), player.getZ()).distanceTo(portalTrap.toCenterPos());
                portalTrap = null;
                ChatUtils.info("Pathing around portal.");
            }

            double yaw = travelYaw(player);
            do {
                if (currDistance > maxDistance) {
                    tempPath = goal;
                    setGoalAndPath(goalProcess, goal);
                    return;
                }
                Vec3d unitYawVec = yawToDirection(yaw);
                Vec3d travelVec = new Vec3d(player.getX(), player.getY(), player.getZ()).subtract(startPos.get().toCenterPos());

                double parallelCurrPosDot = travelVec.multiply(new Vec3d(1, 0, 1)).dotProduct(unitYawVec);
                Vec3d parallelCurrPosComponent = unitYawVec.multiply(parallelCurrPosDot);

                Vec3d pos = startPos.get().toCenterPos().add(parallelCurrPosComponent);
                pos = positionInDirection(pos, yaw, currDistance);

                goal = new BlockPos((int) (Math.floor(pos.x)), targetY.get(), (int) Math.floor(pos.z));
                currDistance++;

                // Blocks in unloaded chunks are void air, for some reason checking if the chunk is loaded was always true, so I check this instead
                if (mc.world.getBlockState(goal).getBlock() == Blocks.VOID_AIR) {
                    waitingForChunksToLoad = true;
                    return;
                }
            }
            // avoid pathing on air cause baritone freaks out, and dont path into portals in case a mod is avoiding portals
            while (!mc.world.getBlockState(goal.down()).isSolidBlock(mc.world, goal.down()) ||
                mc.world.getBlockState(goal).getBlock() == Blocks.NETHER_PORTAL ||
                !mc.world.getBlockState(goal).isAir());
            setGoalAndPath(goalProcess, goal);
        } else {
            paused = false;
        }
    }

    // https://github.com/miles352/meteor-stashhunting-addon/blob/1.21.1/src/main/java/com/stash/hunt/modules/ElytraFlyPlusPlus.java#L587-L630
    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (!obstaclePasserActive() || !avoidPortalTraps.get()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null) return;

        ChunkPos pos = event.chunk().getPos();
        BlockPos centerPos = pos.getCenterAtY(targetY.get());

        // Check if chunk is on the players path
        Vec3d moveDir = yawToDirection(travelYaw(player));
        double distanceToHighway = distancePointToDirection(Vec3d.of(centerPos), moveDir, new Vec3d(player.getX(), player.getY(), player.getZ()));

        if (distanceToHighway > 21) return;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = targetY.get(); y < targetY.get() + 3; y++) {
                    BlockPos position = new BlockPos(pos.x * 16 + x, y, pos.z * 16 + z);

                    if (distancePointToDirection(Vec3d.of(position), moveDir, new Vec3d(player.getX(), player.getY(), player.getZ())) > portalScanWidth.get()) continue;

                    if (mc.world.getBlockState(position).getBlock().equals(Blocks.NETHER_PORTAL)) {
                        BlockPos posBehind = new BlockPos((int) Math.floor(position.getX() + moveDir.x), position.getY(), (int) Math.floor(position.getZ() + moveDir.z));

                        // Trap is detected when a portal has a solid block or another portal behind it
                        if (mc.world.getBlockState(posBehind).isSolidBlock(mc.world, posBehind) ||
                            mc.world.getBlockState(posBehind).getBlock() == Blocks.NETHER_PORTAL) {
                            if (portalTrap == null || (
                                portalTrap.getSquaredDistance(posBehind) > 100 &&
                                    player.getBlockPos().getSquaredDistance(posBehind) < player.getBlockPos().getSquaredDistance(portalTrap))
                            ) {
                                portalTrap = posBehind;
                            }
                        }
                    }
                }
            }
        }
    }

    private static double travelYaw(ClientPlayerEntity player) {
        if (travelYaw == null) travelYaw = angleOnAxis(player.getYaw());
        return travelYaw;
    }

    // https://github.com/miles352/meteor-stashhunting-addon/blob/1.21.1/src/main/java/com/stash/hunt/Utils.java#L89-L93
    private static Vec3d positionInDirection(Vec3d pos, double yaw, double distance) {
        Vec3d offset = yawToDirection(yaw).multiply(distance);
        return pos.add(offset);
    }

    // https://github.com/miles352/meteor-stashhunting-addon/blob/1.21.1/src/main/java/com/stash/hunt/Utils.java#L100-L106
    private static Vec3d yawToDirection(double yaw) {
        yaw = yaw * Math.PI / 180;
        double x = -Math.sin(yaw);
        double z = Math.cos(yaw);
        return new Vec3d(x, 0, z);
    }

    // https://github.com/miles352/meteor-stashhunting-addon/blob/1.21.1/src/main/java/com/stash/hunt/Utils.java#L115-L128
    private static double distancePointToDirection(Vec3d point, Vec3d direction, @Nullable Vec3d start) {
        if (start == null) start = Vec3d.ZERO;

        point = point.multiply(new Vec3d(1, 0, 1));
        start = start.multiply(new Vec3d(1, 0, 1));
        direction = direction.multiply(new Vec3d(1, 0, 1));

        Vec3d directionVec = point.subtract(start);

        double projectionLength = directionVec.dotProduct(direction) / direction.lengthSquared();
        Vec3d projection = direction.multiply(projectionLength);
        Vec3d perp = directionVec.subtract(projection);
        return perp.length();
    }

    // https://github.com/miles352/meteor-stashhunting-addon/blob/1.21.1/src/main/java/com/stash/hunt/Utils.java#L135-L139
    private static double angleOnAxis(double yaw) {
        if (yaw < 0) yaw += 360;
        return Math.round(yaw / 45.0f) * 45;
    }
}
