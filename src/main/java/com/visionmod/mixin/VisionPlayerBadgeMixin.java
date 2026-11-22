package com.visionmod.mixin;

import com.visionmod.VisionAppearance;
import com.visionmod.VisionPresenceClient;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class VisionPlayerBadgeMixin {
    // The avatar override also submits scoreboard text and the optional health label.
    @Inject(
        method = "submitNameTag(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void visionmod$hidePlayerNameTag(CallbackInfo ci) {
        if (VisionAppearance.entityNamesHidden()) ci.cancel();
    }

    @Inject(method="extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",at=@At("TAIL"))
    private void visionmod$badge(Avatar player,AvatarRenderState state,float partial,CallbackInfo ci){
        state.nameTag=VisionPresenceClient.decorate(player.getUUID(),state.nameTag);
    }
}
