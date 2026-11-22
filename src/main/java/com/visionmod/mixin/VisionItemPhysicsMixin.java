package com.visionmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.visionmod.VisionItemPhysicsState;
import com.visionmod.VisionMotionEffects;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemClusterRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drop animation changes the model pose, with collision and pickup handled by Minecraft. */
@Mixin(ItemEntityRenderer.class)
public abstract class VisionItemPhysicsMixin {
    private static final String SUBMIT = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V";
    private static final int ITEM_PHYSICS = 1;

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
        at = @At("TAIL"), require = 1)
    private void visionmod$recordMotion(ItemEntity entity, ItemEntityRenderState state, float partialTick, CallbackInfo ci) {
        ((VisionItemPhysicsState) state).visionmod$setMotion(entity.onGround(), entity.getDeltaMovement().lengthSqr());
    }

    @WrapOperation(method = SUBMIT, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"), require = 1)
    private void visionmod$groundHeight(PoseStack pose, float x, float y, float z,
                                       Operation<Void> original, ItemEntityRenderState state) {
        if (VisionMotionEffects.enabled(ITEM_PHYSICS) && ((VisionItemPhysicsState) state).visionmod$isGrounded()) {
            // With the face turned up, the model's minimum Z becomes its bottom.
            y = (float) -state.item.getModelBoundingBox().minZ + .006f;
        }
        original.call(pose, x, y, z);
    }

    @WrapOperation(method = SUBMIT, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V"), require = 1)
    private void visionmod$dropRotation(PoseStack pose, Quaternionfc vanillaRotation,
                                       Operation<Void> original, ItemEntityRenderState state) {
        if (!VisionMotionEffects.enabled(ITEM_PHYSICS)) {
            original.call(pose, vanillaRotation);
            return;
        }
        var physics = (VisionItemPhysicsState) state;
        if (physics.visionmod$isGrounded()) {
            original.call(pose, Axis.YP.rotation(state.bobOffset));
            pose.mulPose(Axis.XP.rotationDegrees(-90f));
            return;
        }
        original.call(pose, vanillaRotation);
        float speed = (float) Math.min(1, Math.sqrt(physics.visionmod$speed()) * 5);
        float spin = state.ageInTicks * (.4f + speed * 2.4f);
        pose.mulPose(Axis.XP.rotationDegrees(18f + (float) Math.sin(spin * .035f) * 23f));
        pose.mulPose(Axis.ZP.rotationDegrees(spin));
    }

    @WrapOperation(method = SUBMIT, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemEntityRenderer;submitMultipleFromCount(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ItemClusterRenderState;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/phys/AABB;)V"), require = 1)
    private void visionmod$stackOnGround(PoseStack pose, SubmitNodeCollector collector, int light,
                                        ItemClusterRenderState cluster, RandomSource random, AABB bounds,
                                        Operation<Void> original, ItemEntityRenderState state) {
        if (!VisionMotionEffects.enabled(ITEM_PHYSICS) || !((VisionItemPhysicsState) state).visionmod$isGrounded()) {
            original.call(pose, collector, light, cluster, random, bounds);
            return;
        }
        // Flat items form a small pile. Thicker items spread at the same floor level.
        double depth = bounds.getZsize();
        boolean flat = depth < .0625;
        for (int i = 0; i < cluster.count; i++) {
            pose.pushPose();
            try {
                if (i > 0) {
                    double angle = i * 2.4;
                    double spread = flat ? .042 : .10;
                    pose.translate(Math.cos(angle) * spread, Math.sin(angle) * spread, flat ? i * (depth + .003) : 0);
                }
                cluster.item.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, cluster.outlineColor);
            } finally {
                pose.popPose();
            }
        }
    }
}
