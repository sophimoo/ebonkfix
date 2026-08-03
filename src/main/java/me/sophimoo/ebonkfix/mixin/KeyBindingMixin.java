package me.sophimoo.ebonkfix.mixin;

import me.sophimoo.ebonkfix.BounceSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyBinding.class)
public abstract class KeyBindingMixin {

    // Vanilla reads movement through KeyBinding#isPressed each input tick (KeyboardInput#tick), so
    // forcing the read is all the bounce needs - no physical key state is ever touched, and there
    // is nothing to release on deactivate.
    // Adapted from https://github.com/miles352/meteor-stashhunting-addon/blob/1.21.1/src/main/java/com/stash/hunt/mixin/KeyBindingMixin.java#L24-L33
    @Inject(method = "isPressed", at = @At("RETURN"), cancellable = true)
    private void bounceForceKeys(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.options == null) return;

        Object self = this;
        if (self == mc.options.forwardKey) {
            if (BounceSettings.isInputActive()) cir.setReturnValue(true);
        } else if (self == mc.options.jumpKey) {
            if (BounceSettings.wantJump && BounceSettings.isInputActive()) cir.setReturnValue(true);
        }
    }
}
