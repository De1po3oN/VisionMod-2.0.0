package com.visionmod;

import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import static com.visionmod.GuiTestInput.check;

/** Checks the matrices that Minecraft actually submits for a dropped stack. */
final class VisionItemPhysicsGameTest {
    private static final int MODULE = 1;

    static void run(ClientGameTestContext context, TestSingleplayerContext world) throws Exception {
        boolean previous = context.computeOnClient(mc -> VisionMotionEffects.selected(MODULE));
        world.getServer().runCommand("tp @p 8 -60 -1 0 30");
        context.waitTicks(3);
        int[] ids = new int[3];
        world.getServer().runOnServer(server -> {
            var level = server.overworld();
            ItemStack[] stacks = {new ItemStack(Items.IRON_SWORD), new ItemStack(Items.DIAMOND, 32), new ItemStack(Items.DIAMOND_BLOCK)};
            for (int i = 0; i < stacks.length; i++) {
                var drop = new ItemEntity(level, 7.6+i*.9, -57, 1.6, stacks[i], 0, 0, 0);
                drop.setNeverPickUp();
                level.addFreshEntity(drop);
                ids[i] = drop.getId();
            }
        });
        try {
            context.runOnClient(mc -> {
                if (!previous) VisionMotionEffects.toggle(MODULE);
            });
            context.waitFor(mc -> mc.level.getEntity(ids[0]) instanceof ItemEntity, 100);
            context.waitFor(mc -> {
                for (int id : ids) {
                    if (!(mc.level.getEntity(id) instanceof ItemEntity item) || !item.onGround()) return false;
                }
                return true;
            }, 140);
            context.runOnClient(mc -> {
                for (int id : ids) {
                    var item = (ItemEntity) mc.level.getEntity(id);
                    List<Matrix4f> first = submittedPoses(mc, item, 20);
                    assertStationary(first, submittedPoses(mc, item, 123));
                    Vector3f face = first.getFirst().transformDirection(new Vector3f(0, 0, 1)).normalize();
                    check(Math.abs(face.y) > .98, "Ground item face did not lie flat: " + face);
                }
                var stack = (ItemEntity) mc.level.getEntity(ids[1]);
                check(submittedPoses(mc, stack, 20).size() > 1, "Stack copies disappeared");
                var falling = new ItemEntity(mc.level, stack.getX(), stack.getY()+2, stack.getZ(), new ItemStack(Items.IRON_SWORD), 0, -.2, 0);
                check(!submittedPoses(mc, falling, 20).getFirst().equals(submittedPoses(mc, falling, 123).getFirst(), .0001f),
                    "Airborne item did not tumble");
            });
            VisualWorldGameTest.capture(context, "38-item-physics-resting");
            context.runOnClient(mc -> {
                VisionMotionEffects.toggle(MODULE);
                var item = (ItemEntity) mc.level.getEntity(ids[0]);
                check(!submittedPoses(mc, item, 20).getFirst().equals(submittedPoses(mc, item, 123).getFirst(), .0001f),
                    "Disabling the module did not restore vanilla animation");
            });
            System.out.println("[VisionMod][ITEM_PHYSICS] PASS: real drops settle flat, all stack poses stay fixed, falling items tumble and OFF restores vanilla spin");
        } finally {
            context.runOnClient(mc -> {
                if (VisionMotionEffects.selected(MODULE) != previous) VisionMotionEffects.toggle(MODULE);
            });
            world.getServer().runOnServer(server -> {
                for (int id : ids) {
                    if (server.overworld().getEntity(id) instanceof ItemEntity item) item.discard();
                }
            });
        }
    }

    private static void assertStationary(List<Matrix4f> first, List<Matrix4f> later) {
        check(first.size() == later.size(), "Number of rendered items changed over time");
        for (int i = 0; i < first.size(); i++) {
            check(first.get(i).equals(later.get(i), .0001f), "Landed item still spins or bobs");
        }
    }

    private static List<Matrix4f> submittedPoses(Minecraft mc, ItemEntity item, float age) {
        var renderer = (ItemEntityRenderer) mc.getEntityRenderDispatcher().getRenderer(item);
        var state = renderer.createRenderState();
        renderer.extractRenderState(item, state, 0);
        state.ageInTicks = age;
        List<Matrix4f> result = new ArrayList<>();
        var collector = (SubmitNodeCollector) Proxy.newProxyInstance(SubmitNodeCollector.class.getClassLoader(),
            new Class<?>[]{SubmitNodeCollector.class}, (proxy, method, arguments) -> {
                if (method.getName().equals("order")) return proxy;
                if (method.getName().equals("submitItem")) {
                    for (Object arg : arguments) {
                        if (arg instanceof PoseStack pose) {
                            result.add(new Matrix4f(pose.last().pose()));
                            break;
                        }
                        if (arg instanceof PoseStack.Pose pose) {
                            result.add(new Matrix4f(pose.pose()));
                            break;
                        }
                    }
                }
                return null;
            });
        renderer.submit(state, new PoseStack(), collector, new CameraRenderState());
        check(!result.isEmpty(), "No item draw was submitted");
        return result;
    }
}
