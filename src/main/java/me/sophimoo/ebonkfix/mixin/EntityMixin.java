package me.sophimoo.ebonkfix.mixin;

import me.sophimoo.ebonkfix.BounceSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {

    // Bounce needs to always count as sprinting so jumps get the sprint boost; setSprinting alone
    // gets reset by vanilla before the jump runs.
    // Adapted from https://github.com/miles352/meteor-stashhunting-addon/blob/1.21.1/src/main/java/com/stash/hunt/mixin/EntityMixin.java#L37-L44
    @Inject(method = "isSprinting", at = @At("RETURN"), cancellable = true)
    private void bounceForceSprint(CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || (Object) this != mc.player) return;

        if (BounceSettings.isInputActive()) cir.setReturnValue(true);
    }

    // Prevents entity collision from knocking the player off course while bouncing.
    // Adapted from https://github.com/miles352/meteor-stashhunting-addon/blob/1.21.1/src/main/java/com/stash/hunt/mixin/EntityMixin.java#L46-L53
    @Inject(method = "pushAwayFrom", at = @At("HEAD"), cancellable = true)
    private void bounceNoPush(Entity entity, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || (Object) this != mc.player) return;

        if (BounceSettings.isInputActive()) ci.cancel();
    }
}