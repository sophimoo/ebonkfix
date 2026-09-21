package me.sophimoo.ebonkfix.mixin;

import me.sophimoo.ebonkfix.BounceMode;
import me.sophimoo.ebonkfix.BounceSettings;
import me.sophimoo.ebonkfix.ObstaclePasser;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.modes.Bounce;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Bounce.class)
public abstract class BounceMixin {

    @Unique
    private boolean prevWantJump = false;

    // Meteor's LivingEntityMixin#recastOnLand calls Bounce.recastElytra() whenever the gliding flag
    // flips off while ElytraFly is in Bounce mode, which sends START_FALL_FLYING with no jump edge.
    // Grim flags that as ElytraB "no jump", and the resulting resync re-triggers the recast,
    // causing a self-sustaining flag cascade. Disable the recast while bounce mode is on.
    @Inject(method = "recastElytra", remap = false, at = @At("HEAD"), cancellable = true)
    private static void bounceCancelRecast(CallbackInfoReturnable<Boolean> cir) {
        if (cir.isCancelled()) return; // another injector already took over - defer to it

        if (BounceSettings.bounceMode != null) {
            cir.setReturnValue(false);
        }
    }

    // ElytraFly dispatches mode.onPreTick() on TickEvent.Pre and mode.onTick() on TickEvent.Post.
    // The bounce input MUST run at Pre (before the player movement tick) so vanilla consumes the
    // key states in the same tick - running at Post desyncs from Grim's movement simulation.
    // Jump and forward are forced through KeyBindingMixin, vanilla consumes them like real keys.
    @Inject(method = "onPreTick", remap = false, at = @At("HEAD"), cancellable = true)
    private void bouncePreTick(CallbackInfo ci) {
        if (ci.isCancelled()) return; // another injector already took over - defer to it
        if (BounceSettings.bounceMode == null) return;
        ci.cancel();

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;
        ClientPlayerEntity player = mc.player;

        // While the obstacle passer has Baritone pathing, the bounce input is released so Baritone
        // has full control of the player.
        boolean enabled = BounceSettings.isInputActive();
        boolean wantJump = false;

        if (enabled) {
            if (BounceSettings.bounceMode.get() == BounceMode.Packet) {
                // jump is held on the ground and while airborne but not gliding,
                // released once fall-flying. Holding jump means vanilla's rising-edge
                // check never passes, so the redeploy packet is sent manually instead.
                if (player.isOnGround()) {
                    wantJump = true;
                } else if (!player.isGliding()) {
                    wantJump = true;
                    sendStartFlyingPacket(mc, player);
                }
            } else {
                // Emulates how a vanilla player bounces: jump is held on the ground to bounce,
                // released while gliding, and tapped midair to redeploy. Vanilla only sends
                // START_FALL_FLYING on a fresh midair jump press (rising edge), which is also the
                // only sequence Grim's ElytraB/ElytraC checks accept.
                wantJump = player.isOnGround() || (!player.isGliding() && !prevWantJump);
            }
        }

        prevWantJump = wantJump;
        BounceSettings.wantJump = wantJump;

        // Mirror the forced keys into Meteor's Input state as vanilla Bounce.setPressed did. GUIMove
        // re-derives key states from Input.isPressed while a screen is open, so without this it
        // overwrites the bounce's input and pauses it.
        Input.setKeyState(mc.options.forwardKey, enabled);
        Input.setKeyState(mc.options.jumpKey, enabled && wantJump);

        if (enabled) player.setSprinting(true);

        ObstaclePasser.onTick(mc, player);

        if (!BounceSettings.isActive() || ObstaclePasser.isPaused()) return;

        Modules modules = Modules.get();
        ElytraFly elytraFly = modules == null ? null : modules.get(ElytraFly.class);
        if (elytraFly == null) return;

        float yaw = switch (elytraFly.yawLockMode.get()) {
            case None -> player.getYaw();
            case Smart -> Math.round((player.getYaw() + 1f) / 45f) * 45f;
            case Simple -> elytraFly.yaw.get().floatValue();
        };
        player.setYaw(yaw);
        player.setPitch(elytraFly.pitch.get().floatValue());
    }

    // Vanilla Bounce.onTick (Post) would send START_FALL_FLYING whenever jump is held and not
    // gliding - including on the ground right after a bounce - which Grim flags. Suppress the whole
    // Post handler while bounce mode is on.
    @Inject(method = "onTick", remap = false, at = @At("HEAD"), cancellable = true)
    private void bounceCancelPostTick(CallbackInfo ci) {
        if (ci.isCancelled()) return; // another injector already took over - defer to it

        if (BounceSettings.bounceMode != null) {
            ci.cancel();
        }
    }

    // Vanilla Bounce stops gliding client-side on every rubberband; bounce mode relies on the
    // client gliding state matching vanilla's own tracking, so suppress that while it is on.
    @Inject(method = "onPacketReceive", remap = false, at = @At("HEAD"), cancellable = true)
    private void bounceCancelPacketReceive(PacketEvent.Receive event, CallbackInfo ci) {
        if (ci.isCancelled()) return; // another injector already took over - defer to it
        if (BounceSettings.bounceMode == null) return;
        ObstaclePasser.onPacketReceive(event);
        ci.cancel();
    }

    @Inject(method = "onActivate", remap = false, at = @At("TAIL"))
    private void bounceOnActivate(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;
        ObstaclePasser.onActivate(mc.player);
    }

    @Inject(method = "onDeactivate", remap = false, at = @At("HEAD"))
    private void bounceOnDeactivate(CallbackInfo ci) {
        ObstaclePasser.onDeactivate();
        prevWantJump = false;
        BounceSettings.wantJump = false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.options != null) {
            Input.setKeyState(mc.options.forwardKey, false);
            Input.setKeyState(mc.options.jumpKey, false);
        }
    }

    // https://github.com/miles352/meteor-stashhunting-addon/blob/1.21.1/src/main/java/com/stash/hunt/modules/ElytraFlyPlusPlus.java#L563-L569
    @Unique
    private static void sendStartFlyingPacket(MinecraftClient mc, ClientPlayerEntity player) {
        if (player.isOnGround() || player.isGliding()) return;
        if (mc.getNetworkHandler() == null) return; // disconnecting / world unloading
        player.startGliding();
        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
    }
}
