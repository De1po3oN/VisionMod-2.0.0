package com.visionmod.mixin;

import com.visionmod.VisionAppearance;
import net.minecraft.client.renderer.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class HideEntityNameTagMixin {
    @Inject(method = "submitNameTag", at = @At("HEAD"), cancellable = true)
    private void visionmod$hideEntityNameTag(CallbackInfo ci) {
        if (VisionAppearance.entityNamesHidden()) ci.cancel();
    }
}
