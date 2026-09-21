package me.sophimoo.ebonkfix.mixin;

import me.sophimoo.ebonkfix.BounceMode;
import me.sophimoo.ebonkfix.BounceSettings;
import me.sophimoo.ebonkfix.ObstaclePasser;
import meteordevelopment.meteorclient.settings.BlockPosSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightModes;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ElytraFly.class)
public abstract class ElytraFlyMixin {

    @Inject(method = "<init>()V", remap = false, at = @At("TAIL"))
    private void addBounceModeSettings(CallbackInfo ci) {
        ElytraFly self = (ElytraFly) (Object) this;
        SettingGroup sg = self.settings.getDefaultGroup();

        BounceSettings.bounceMode = sg.add(new EnumSetting.Builder<BounceMode>()
            .name("bounce-mode")
            // Vanilla emulates vanilla jump taps (no Grim flags, ~24 bps at low ping).
            // Packet restarts gliding every airborne tick via START_FALL_FLYING (~40 bps at any ping),
            // but WILL flag Grim ElytraA/B/C.
            .description("Packet requires vf -> 1.20.5/6 or lower")
            .defaultValue(BounceMode.Vanilla)
            .visible(() -> self.flightMode.get() == ElytraFlightModes.Bounce)
            .build()
        );

        BounceSettings.smartPitch = sg.add(new BoolSetting.Builder()
            .name("smart-pitch")
            // Pitches fully down (90) while descending and up (4) otherwise, getting up to ~48bps
            // while bouncing instead of the ~40bps a fixed pitch gives.
            .description("Calculates the best pitch for bounce, ignoring the pitch setting.")
            .defaultValue(false)
            .visible(() -> self.flightMode.get() == ElytraFlightModes.Bounce && BounceSettings.bounceMode != null)
            .build()
        );

        BounceSettings.spoofView = sg.add(new BoolSetting.Builder()
            .name("spoof-view")
            // The locked pitch/yaw is only applied for the movement tick (and its packets), then the
            // real view is restored before the frame renders, so you can look around freely.
            .description("Lets you look around freely while the locked bounce angles stay on the server.")
            .defaultValue(false)
            .visible(() -> self.flightMode.get() == ElytraFlightModes.Bounce && BounceSettings.bounceMode != null)
            .build()
        );

        // Obstacle Passer settings ported from ElytraFlyPlusPlus:
        // https://github.com/miles352/meteor-stashhunting-addon/blob/1.21.1/src/main/java/com/stash/hunt/modules/ElytraFlyPlusPlus.java#L131-L205
        SettingGroup sgObstaclePasser = self.settings.createGroup("Obstacle Passer");

        ObstaclePasser.highwayObstaclePasser = sgObstaclePasser.add(new BoolSetting.Builder()
            .name("highway-obstacle-passer")
            .description("Uses baritone to pass obstacles.")
            .defaultValue(false)
            .visible(() -> self.flightMode.get() == ElytraFlightModes.Bounce && BounceSettings.bounceMode != null)
            .build()
        );

        ObstaclePasser.useCustomStartPos = sgObstaclePasser.add(new BoolSetting.Builder()
            .name("use-custom-start-position")
            .description("More reliable for long distance automated travel")
            .defaultValue(false)
            .visible(() -> obstaclePasserVisible(self))
            .build()
        );

        ObstaclePasser.startPos = sgObstaclePasser.add(new BlockPosSetting.Builder()
            .name("start-position")
            .description("The start position to use when using a custom start position.")
            .defaultValue(new BlockPos(0, 0, 0))
            .visible(() -> obstaclePasserVisible(self) && ObstaclePasser.useCustomStartPos.get())
            .build()
        );

        ObstaclePasser.awayFromStartPos = sgObstaclePasser.add(new BoolSetting.Builder()
            .name("away-from-start-position")
            .description("When disabled startpos becomes Goal")
            .defaultValue(true)
            .visible(() -> obstaclePasserVisible(self))
            .build()
        );

        ObstaclePasser.distance = sgObstaclePasser.add(new DoubleSetting.Builder()
            .name("distance")
            .description("The distance to set the baritone goal for path realignment.")
            .defaultValue(10.0)
            .visible(() -> obstaclePasserVisible(self))
            .build()
        );

        ObstaclePasser.targetY = sgObstaclePasser.add(new IntSetting.Builder()
            .name("y-level")
            .description("The Y level to bounce at. This must be correct or bounce will not start properly.")
            .defaultValue(120)
            .visible(() -> obstaclePasserVisible(self))
            .build()
        );

        ObstaclePasser.setbackTrigger = sgObstaclePasser.add(new BoolSetting.Builder()
            .name("setback-trigger")
            .description("Triggers obstacle passer after more than 2 setbacks.")
            .defaultValue(false)
            .visible(() -> obstaclePasserVisible(self))
            .build()
        );

        ObstaclePasser.avoidPortalTraps = sgObstaclePasser.add(new BoolSetting.Builder()
            .name("avoid-portal-traps")
            .description("Will attempt to detect portal traps on chunk load and avoid them.")
            .defaultValue(false)
            .visible(() -> obstaclePasserVisible(self))
            .build()
        );

        ObstaclePasser.portalAvoidDistance = sgObstaclePasser.add(new DoubleSetting.Builder()
            .name("portal-avoid-distance")
            .description("The distance to a portal trap where the obstacle passer will takeover and go around it.")
            .defaultValue(20)
            .min(0)
            .sliderMax(50)
            .visible(() -> obstaclePasserVisible(self) && ObstaclePasser.avoidPortalTraps.get())
            .build()
        );

        ObstaclePasser.portalScanWidth = sgObstaclePasser.add(new IntSetting.Builder()
            .name("portal-scan-width")
            .description("The width on the axis of the highway that will be scanned for portal traps.")
            .defaultValue(5)
            .min(3)
            .sliderMax(10)
            .visible(() -> obstaclePasserVisible(self) && ObstaclePasser.avoidPortalTraps.get())
            .build()
        );
    }

    private static boolean obstaclePasserVisible(ElytraFly self) {
        return self.flightMode.get() == ElytraFlightModes.Bounce
            && BounceSettings.bounceMode != null
            && ObstaclePasser.highwayObstaclePasser.get();
    }

    // ElytraFly.onPlayerMove runs autoTakeoff() (sends START_FALL_FLYING after 8 held jump ticks,
    // which Grim flags as ElytraB) and zeroes movement when entering unloaded chunks (a movement
    // desync seed for Grim). Neither exists in the bounce mode design, so skip the handler.
    @Inject(method = "onPlayerMove", remap = false, at = @At("HEAD"), cancellable = true)
    private void bounceCancelPlayerMove(CallbackInfo ci) {
        if (ci.isCancelled()) return; // another injector already took over - defer to it

        // Only in Bounce mode - cancelling unconditionally would gut every other flight mode
        // (speed control, auto-takeoff, autopilot all live in this handler).
        ElytraFly self = (ElytraFly) (Object) this;
        if (BounceSettings.bounceMode != null && self.flightMode.get() == ElytraFlightModes.Bounce) {
            ci.cancel();
        }
    }
}
