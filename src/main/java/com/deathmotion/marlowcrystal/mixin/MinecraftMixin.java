package com.deathmotion.marlowcrystal.mixin;

import com.deathmotion.marlowcrystal.MarlowCrystal;
import com.deathmotion.marlowcrystal.cache.OptOutCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Removes the vanilla cooldown that throttles how quickly end crystals can be
 * placed.
 * <p>
 * After every use-item, {@link Minecraft#startUseItem()} arms
 * {@code rightClickDelay} with {@code 4} ticks, so holding use only places a
 * crystal five times a second. When the player is holding an end crystal we
 * collapse that delay to {@code 0}, letting a held use-key place on every client
 * tick instead. Every other item keeps the vanilla cooldown, so normal building
 * and eating are untouched, and the optimization yields to a server opt-out the
 * same way the crystal-removal path does.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Shadow
    public LocalPlayer player;

    @Unique
    private OptOutCache marlowcrystal$optOutCache;

    @ModifyConstant(method = "startUseItem", constant = @Constant(intValue = 4))
    private int marlowcrystal$fastCrystalPlace(int vanillaDelay) {
        LocalPlayer player = this.player;
        if (player == null) {
            return vanillaDelay;
        }

        // Yield to the server opt-out, identical to the crystal-removal path, so
        // a server that disables the mod still gets vanilla placement timing.
        if (marlowcrystal$optOutCache == null) {
            marlowcrystal$optOutCache = MarlowCrystal.getInstance().getOptOutCache();
        }
        if (marlowcrystal$optOutCache.isOptedOut()) {
            return vanillaDelay;
        }

        // Only end crystals skip the cooldown; anything else keeps vanilla timing.
        boolean holdingCrystal =
                player.getItemInHand(InteractionHand.MAIN_HAND).is(Items.END_CRYSTAL)
                        || player.getItemInHand(InteractionHand.OFF_HAND).is(Items.END_CRYSTAL);

        return holdingCrystal ? 0 : vanillaDelay;
    }
}
