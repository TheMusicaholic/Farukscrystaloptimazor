package com.deathmotion.marlowcrystal.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Mirrors the server's crystal removal on the client the moment an attack is
 * sent.
 * <p>
 * On 1.21.11 the client reports attacks through {@link ServerboundInteractPacket}
 * rather than a dedicated attack packet, and that packet keeps its action and
 * target private. The only public way to learn whether the interaction was an
 * attack is to {@link ServerboundInteractPacket#dispatch(ServerboundInteractPacket.Handler)
 * dispatch} it to a handler, so the attack target is taken from the crosshair
 * ({@link Minecraft#hitResult}) which is exactly what the vanilla attack used.
 */
public class InteractHandler implements ServerboundInteractPacket.Handler {

    private final Minecraft client;

    public InteractHandler(Minecraft client) {
        this.client = client;
    }

    @Override
    public void onInteraction(@NotNull InteractionHand hand) {
    }

    @Override
    public void onInteraction(@NotNull InteractionHand hand, @NotNull Vec3 location) {
    }

    @Override
    public void onAttack() {
        if (!(client.hitResult instanceof EntityHitResult entityHitResult)) {
            return;
        }

        if (!(entityHitResult.getEntity() instanceof EndCrystal crystal)) {
            return;
        }

        LocalPlayer player = client.player;
        if (player == null) {
            return;
        }

        if (canDestroyCrystal(player, crystal)) {
            destroyCrystal(crystal);
            retargetCrosshair(crystal);
        }
    }

    private void retargetCrosshair(EndCrystal crystal) {
        LocalPlayer player = client.player;
        if (player == null || client.hitResult == null || client.crosshairPickEntity != crystal) {
            return;
        }

        // The crystal is already removed, so re-run vanilla's crosshair pick the
        // same way GameRenderer#pick does. Unlike a block-only retrace this also
        // considers entities, so a crystal stacked or queued directly behind the
        // one we just broke becomes the new target and can be destroyed on the
        // same input instead of waiting a frame for the next pick. With nothing in
        // the way it still falls back to the block behind, so placing the next
        // crystal is unchanged. This only moves the crosshair; the follow-up
        // attack is re-validated through canDestroyCrystal, so nothing is removed
        // speculatively.
        Entity camera = client.getCameraEntity();
        HitResult retraced = player.raycastHitResult(1.0F, camera != null ? camera : player);
        client.hitResult = retraced;
        client.crosshairPickEntity = retraced instanceof EntityHitResult hit ? hit.getEntity() : null;
    }

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

    private void destroyCrystal(EndCrystal crystal) {
        crystal.remove(Entity.RemovalReason.KILLED);
        crystal.gameEvent(GameEvent.ENTITY_DIE);
    }
}
