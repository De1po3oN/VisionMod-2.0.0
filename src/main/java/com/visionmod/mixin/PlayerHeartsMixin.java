
package com.visionmod.mixin;

import com.visionmod.VisionModClient;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Avatar;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.HashMap;

@Mixin(AvatarRenderer.class)
public abstract class PlayerHeartsMixin {

    @Unique
    private static final Map<AvatarRenderState, float[]>
        visionmod$health = new WeakHashMap<>();

    // Most players have unchanged health across many frames. Reuse immutable
    // formatted labels instead of rebuilding Component trees every render.
    @Unique private static final Map<Long, Component> visionmod$labels = new HashMap<>();

    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
        at = @At("TAIL"),
        require = 0
    )
    private void visionmod$captureHealth(
            Avatar player,
            AvatarRenderState state,
            float partialTick,
            CallbackInfo ci
    ) {
        // On busy spawn locations this runs once for every player render.
        // Avoid allocating even the two-float array when the feature is off.
        if (!VisionModClient.isHeartsEnabled() || player.isInvisible()) {
            visionmod$health.remove(state);
            return;
        }
        float[] values = visionmod$health.get(state);
        if (values == null) {
            values = new float[2];
            visionmod$health.put(state, values);
        }
        values[0] = player.getHealth();
        values[1] = player.getMaxHealth();
    }

    @Inject(
        method = "submitNameTag(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
        at = @At("TAIL"),
        require = 0
    )
    private void visionmod$drawHearts(
            AvatarRenderState state,
            PoseStack matrices,
            SubmitNodeCollector queue,
            CameraRenderState camera,
            CallbackInfo ci
    ) {
        if (!VisionModClient.isHeartsEnabled()) {
            return;
        }

        if (state.nameTagAttachment == null
                || state.isInvisible) {
            return;
        }

        float[] data = visionmod$health.get(state);

        if (data == null) {
            return;
        }

        float hp = Math.max(0.0F, data[0]);
        float maxHp = Math.max(1.0F, data[1]);

        int hpTenth = Math.max(0, Math.round(hp * 10.0F));
        int maxTenth = Math.max(1, Math.round(maxHp * 10.0F));
        long key = ((long) hpTenth << 32) | (maxTenth & 0xFFFFFFFFL);
        Component label = visionmod$labels.get(key);
        if (label == null) {
            int slots = Math.min(20, (int) Math.ceil(maxHp / 2.0F));
            int filled = Math.min(slots, (int) Math.ceil(hp / 2.0F));
            Component value = Component.literal("♥".repeat(filled))
                .withStyle(ChatFormatting.RED)
                .append(Component.literal("♡".repeat(slots - filled)).withStyle(ChatFormatting.GRAY))
                .append(Component.literal("  " + hpTenth / 10.0F + "/"
                    + maxTenth / 10.0F + " HP").withStyle(ChatFormatting.WHITE));
            // Cap memory use even if a server sends frequently changing values.
            if (visionmod$labels.size() >= 96) visionmod$labels.clear();
            visionmod$labels.put(key, value);
            label = value;
        }

        queue.submitNameTag(
            matrices,
            state.nameTagAttachment.add(
                0.0, 0.30, 0.0
            ),
            0,
            label,
            !state.isDiscrete,
            state.lightCoords,
            state.distanceToCameraSq,
            camera
        );
    }
}
