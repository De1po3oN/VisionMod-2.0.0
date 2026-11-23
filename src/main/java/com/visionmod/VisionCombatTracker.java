package com.visionmod;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Lightweight client-side observation of *our own* entity attack.
 *
 * No server packets, no entity-list scans and no damage attribution claims:
 * a hurt animation is observed only after a local attack was attempted.
 * A target dying shortly afterward is labelled "Target down", not a
 * server-confirmed kill by the user.
 */
public final class VisionCombatTracker {
    private static LivingEntity target;
    private static ClientLevel targetLevel;
    private static int ticksLeft;
    private static boolean sawHurt;
    private static int hitSequence;
    private static ClientLevel eventLevel;
    private static LivingEntity lastHitTarget;
    private static LivingEntity lastDeathTarget;
    private static int deathSequence;
    private static String deathName = "";

    private VisionCombatTracker() { }

    static LivingEntity crosshairTarget(Minecraft mc) {
        return mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living
            && living.isAlive() && !living.isInvisible() ? living : null;
    }

    public static void init() {
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            Minecraft mc = Minecraft.getInstance();
            if (VisionModClient.enabled && level.isClientSide() && mc.player == player &&
                    entity instanceof LivingEntity living && living.isAlive() && !living.isInvisible()) {
                target = living;
                targetLevel = mc.level;
                ticksLeft = 60;
                sawHurt = false;
            }
            return InteractionResult.PASS; // Never cancel or change vanilla combat.
        });
        ClientTickEvents.END_CLIENT_TICK.register(VisionCombatTracker::tick);
    }

    private static void tick(Minecraft mc) {
        if (eventLevel != mc.level) {
            eventLevel = mc.level;
            lastHitTarget = lastDeathTarget = null;
        }
        if (!VisionModClient.enabled || mc.player == null || mc.level == null ||
                target == null || target.isInvisible() || targetLevel != mc.level || ticksLeft-- <= 0) {
            clear();
            return;
        }
        // The real entity hurt animation is the signal, not "still aiming at it".
        if (target.hurtTime > 0 && !sawHurt) {
            sawHurt = true;
            lastHitTarget = target;
            hitSequence++;
        }
        if (target.isDeadOrDying()) {
            if (sawHurt) {
                lastDeathTarget = target;
                deathName = target.getName().getString();
                deathSequence++;
            }
            clear();
        }
    }

    private static void clear() {
        target = null;
        targetLevel = null;
        ticksLeft = 0;
        sawHurt = false;
    }

    public static int hitSequence() { return hitSequence; }
    static boolean hitVisible() { return lastHitTarget != null && !lastHitTarget.isInvisible(); }
    static boolean deathVisible() { return lastDeathTarget != null && !lastDeathTarget.isInvisible(); }
    public static int deathSequence() { return deathSequence; }
    public static String deathName() { return deathName; }
}
