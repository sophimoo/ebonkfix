package me.sophimoo.ebonkfix;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightModes;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;

public final class BounceSettings {
    public static Setting<BounceMode> bounceMode;
    public static Setting<Boolean> smartPitch;
    public static Setting<Boolean> spoofView;

    /** Set by BounceMixin each pre-tick; consumed by KeyBindingMixin to force the jump key. */
    public static boolean wantJump = false;

    private BounceSettings() {}

    /**
     * True when the bounce mode should be actively controlling the player right now:
     * ElytraFly on, Bounce mode selected, elytra equipped.
     * Mirrors ElytraFlyPlusPlus#enabled().
     * https://github.com/miles352/meteor-stashhunting-addon/blob/1.21.1/src/main/java/com/stash/hunt/modules/ElytraFlyPlusPlus.java#L504-L507
     */
    public static boolean isActive() {
        return isActive(true);
    }

    /**
     * @param requireElytra whether an equipped elytra is required (the obstacle passer must keep
     *                      working while Baritone swaps chest pieces, so it passes false)
     */
    public static boolean isActive(boolean requireElytra) {
        if (bounceMode == null) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return false;
        if (mc.player.getAbilities().allowFlying) return false;

        Modules modules = Modules.get();
        if (modules == null) return false;

        ElytraFly elytraFly = modules.get(ElytraFly.class);
        if (elytraFly == null || !elytraFly.isActive()) return false;
        if (elytraFly.flightMode.get() != ElytraFlightModes.Bounce) return false;

        return !requireElytra || mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
    }

    /**
     * True when the bounce mode should be actively driving the player's input right now.
     * False while the obstacle passer has Baritone pathing, so Baritone gets full control.
     */
    public static boolean isInputActive() {
        return isActive() && !ObstaclePasser.isPaused();
    }
}
