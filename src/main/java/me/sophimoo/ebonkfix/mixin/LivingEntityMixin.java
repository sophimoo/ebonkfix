package me.sophimoo.ebonkfix.mixin;

import me.sophimoo.ebonkfix.BounceSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Shadow
    private int jumpingCooldown;

    // Vanilla imposes a 10 tick cooldown between jumps; without clearing it the bounce cannot
    // chain (you land, can't jump, lose all speed).
    // Adapted from https://github.com/miles352/meteor-stashhunting-addon/blob/1.21.1/src/main/java/com/stash/hunt/mixin/LivingEntityMixin.java#L37-L44
    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void bounceNoJumpCooldown(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || (Object) this != mc.player) return;

        if (BounceSettings.isInputActive()) this.jumpingCooldown = 0;
    }
}