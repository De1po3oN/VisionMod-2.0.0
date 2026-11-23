package com.visionmod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayDeque;

/** Bounded world geometry for actual spear actions, with ordinary terrain depth testing. */
public final class VisionSpearEffects {
    private record Burst(Vec3 origin, Vec3 direction, long born, Entity target) {
        boolean pierce() { return target != null; }
    }
    private static final ArrayDeque<Burst> BURSTS = new ArrayDeque<>();
    private static ClientLevel level;
    private static long renderedFrames;
    private static long lunges, pierces;
    private VisionSpearEffects() { }
    public static void reset() { BURSTS.clear(); level = null; }
    static int activeBursts() { return BURSTS.size(); }
    static long renderedFrames() { return renderedFrames; }
    static long lungeEvents() { return lunges; }
    static long pierceEvents() { return pierces; }
    static void emit(Vec3 point, Vec3 direction, Entity target) {
        boolean pierce = target != null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !VisionMotionEffects.enabled(pierce ? 3 : 2) || direction.lengthSqr() < 1e-6) return;
        if (level != mc.level) { reset(); level = mc.level; }
        if (BURSTS.size() >= 16) BURSTS.removeFirst();
        BURSTS.addLast(new Burst(point, direction.normalize(), System.nanoTime(), target));
        if (pierce) pierces++; else lunges++;
    }
    public static void init() {
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            Minecraft mc = Minecraft.getInstance();
            if (!VisionModClient.enabled || mc.level == null || mc.player == null || level != mc.level) { reset(); return; }
            long now = System.nanoTime();
            BURSTS.removeIf(b -> (b.target != null && b.target.isInvisible()) || !VisionMotionEffects.enabled(b.pierce() ? 3 : 2) || now - b.born > VisionMotionEffects.spearDuration() * 1_000_000L);
            if (BURSTS.isEmpty() || ctx.matrices() == null || ctx.consumers() == null) return;
            PoseStack pose = ctx.matrices();
            Vec3 camera = mc.gameRenderer.getMainCamera().position();
            pose.pushPose();
            try {
                pose.translate(-camera.x, -camera.y, -camera.z);
                VertexConsumer out = ctx.consumers().getBuffer(RenderTypes.entityTranslucent(VisionGeometry.WHITE_TEXTURE));
                for (Burst burst : BURSTS) draw(out, pose, burst, (now - burst.born) / (VisionMotionEffects.spearDuration() * 1_000_000f));
                renderedFrames++;
            } finally { pose.popPose(); }
        });
    }
    private static void draw(VertexConsumer out, PoseStack pose, Burst b, float age) {
        float fade = (1 - age) * (1 - age), size = VisionMotionEffects.spearSize();
        int color = VisionGeometry.withAlpha(VisionMotionEffects.spearColor(), Math.round(190 * fade));
        Vec3 side = b.direction.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < .001) side = new Vec3(1, 0, 0); else side = side.normalize();
        Vec3 up = side.cross(b.direction).normalize();
        if (b.pierce()) {
            Vec3 start = b.origin.subtract(b.direction.scale(.8 * size));
            Vec3 end = b.origin.add(b.direction.scale((1.4 + age * .35) * size));
            strip(out, pose, start, end, side.scale(.045 * size * (1 - age)), color);
            strip(out, pose, start, end, up.scale(.045 * size * (1 - age)), color);
        } else {
            for (int i = -1; i <= 1; i++) {
                Vec3 offset = side.scale(i * .23 * size);
                Vec3 start = b.origin.add(offset).subtract(b.direction.scale((.2 + age * .8) * size));
                Vec3 end = b.origin.add(offset).add(b.direction.scale((1.6 - age * .5) * size));
                strip(out, pose, start, end, up.scale(.035 * size * (1 - age)), color);
            }
        }
        double radius = (.14 + Math.sqrt(age) * (b.pierce() ? .65 : .9)) * size;
        for (int i = 0; i < 32; i++) {
            double a = i * Math.PI / 16, c = (i + 1) * Math.PI / 16;
            Vec3 p = b.origin.add(side.scale(Math.cos(a) * radius)).add(up.scale(Math.sin(a) * radius));
            Vec3 q = b.origin.add(side.scale(Math.cos(c) * radius)).add(up.scale(Math.sin(c) * radius));
            Vec3 edge = side.scale(Math.cos(a)).add(up.scale(Math.sin(a))).scale(.018 * size);
            strip(out, pose, p, q, edge, color);
        }
    }
    private static void strip(VertexConsumer out, PoseStack pose, Vec3 a, Vec3 b, Vec3 edge, int color) {
        Vec3 p = a.subtract(edge), q = b.subtract(edge), r = b.add(edge), s = a.add(edge);
        VisionGeometry.quad(out, pose, (float)p.x,(float)p.y,(float)p.z, (float)q.x,(float)q.y,(float)q.z,
            (float)r.x,(float)r.y,(float)r.z, (float)s.x,(float)s.y,(float)s.z, color,0,1,0);
        VisionGeometry.quad(out, pose, (float)s.x,(float)s.y,(float)s.z, (float)r.x,(float)r.y,(float)r.z,
            (float)q.x,(float)q.y,(float)q.z, (float)p.x,(float)p.y,(float)p.z, color,0,-1,0);
    }
}
