package com.deathmotion.marlowcrystal.mixin;

import com.deathmotion.marlowcrystal.MarlowCrystal;
import com.deathmotion.marlowcrystal.cache.OptOutCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ClientConnectionMixin {

    @Unique
    private OptOutCache optOutCache;

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
    private void onPacketSend(Packet<?> packet, CallbackInfo ci) {
        if (!(packet instanceof ServerboundAttackPacket(int entityId))) {
            return;
        }

        if (optOutCache == null) {
            optOutCache = MarlowCrystal.getInstance().getOptOutCache();
        }
        if (optOutCache.isOptedOut()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        Entity target = mc.level.getEntity(entityId);
        if (!(target instanceof EndCrystal crystal)) {
            return;
        }

        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        if (canDestroyCrystal(player, crystal)) {
            destroyCrystal(crystal);
            retargetCrosshair(mc, crystal);
        }
    }

    @Unique
    private void retargetCrosshair(Minecraft mc, EndCrystal crystal) {
        LocalPlayer player = mc.player;
        if (player == null || mc.hitResult == null || mc.crosshairPickEntity != crystal) {
            return;
        }

        HitResult retraced = player.pick(player.blockInteractionRange(), 1.0F, false);
        mc.crosshairPickEntity = null;
        mc.hitResult = retraced;
    }

    @Unique
    private boolean canDestroyCrystal(LocalPlayer player, EndCrystal crystal) {
        // Only mirror the server when we are certain the attack would land. A
        // crystal that is already gone or flagged invulnerable will not be
        // removed server-side, and a spectator deals no damage at all. Removing
        // it locally in those cases would desync and the crystal would "ghost"
        // back, so we leave it untouched.
        if (crystal.isRemoved() || crystal.isInvulnerable() || player.isSpectator()) {
            return false;
        }

        // This is the exact value Player#attack uses to decide whether a hit
        // deals damage. getAttributeValue already folds in the held weapon and
        // the Strength/Weakness effects through the attribute system, so it
        // stays correct as gear and effects change without re-deriving the math
        // by hand. End crystals are destroyed by any positive attack damage.
        return player.getAttributeValue(Attributes.ATTACK_DAMAGE) > 0.0D;
    }

    @Unique
    private void destroyCrystal(EndCrystal crystal) {
        crystal.remove(Entity.RemovalReason.KILLED);
        crystal.gameEvent(GameEvent.ENTITY_DIE);
    }
}
