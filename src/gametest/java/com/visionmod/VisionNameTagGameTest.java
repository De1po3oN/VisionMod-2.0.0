package com.visionmod;

import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import static com.visionmod.GuiTestInput.check;

/** Verify real render submissions while operating the switch from the dashboard. */
final class VisionNameTagGameTest {
    static void run(ClientGameTestContext context) throws Exception {
        context.runOnClient(mc -> verifyNameTags(mc, true));
        context.setScreen(VisionDashboardScreen::new);
        context.waitTicks(2);
        double[] position = context.computeOnClient(mc -> {
            var screen = mc.screen;
            var layout = DashboardLayout.of(screen.width, screen.height);
            int left = (screen.width-layout.width())/2, top = (screen.height-layout.height())/2;
            return new double[]{left+layout.contentLeft()+35, top+layout.searchTop()+13,
                left+layout.cardLeft(0)+15, top+layout.cardTop(0)+15};
        });
        GuiTestInput.click(context, position[0], position[1], 0);
        context.getInput().typeChars("бирки");
        context.waitTicks(2);
        GuiTestInput.click(context, position[2], position[3], 0);
        context.waitTicks(1);
        context.runOnClient(mc -> {
            check(VisionAppearance.hideEntityNames, "Dashboard did not enable hiding entity names");
            verifyNameTags(mc, false);
            VisionModClient.enabled = false;
            try {
                verifyNameTags(mc, true);
            } finally {
                VisionModClient.enabled = true;
            }
        });
        VisualWorldGameTest.capture(context, "37-hide-name-tags-setting");
        GuiTestInput.click(context, position[2], position[3], 0);
        context.waitTicks(1);
        context.runOnClient(mc -> {
            check(!VisionAppearance.hideEntityNames, "Dashboard did not disable hiding entity names");
            verifyNameTags(mc, true);
        });
        context.setScreen(() -> null);
        System.out.println("[VisionMod][NAME_TAGS] PASS: dashboard toggle, avatar and named pig submissions, TAB, master switch and restoration");
    }

    private static void verifyNameTags(Minecraft mc, boolean visible) {
        try {
            var pig = EntityType.PIG.create(mc.level, EntitySpawnReason.COMMAND);
            check(pig != null, "Could not create a pig for name tag rendering");
            pig.setCustomName(Component.literal("Vision Pig"));
            pig.setCustomNameVisible(true);
            pig.setPos(mc.player.getX()+1, mc.player.getY(), mc.player.getZ()+2);
            var pigRenderer = mc.getEntityRenderDispatcher().getRenderer(pig);
            var pigState = pigRenderer.createRenderState();
            EntityRenderer.class.getMethod("extractRenderState", Entity.class, EntityRenderState.class, float.class)
                .invoke(pigRenderer, pig, pigState, 1f);
            check(pigState.nameTag != null && pigState.nameTag.getString().equals("Vision Pig"), "Pig must have a vanilla render label");
            verifySubmission(pigRenderer, EntityRenderer.class, EntityRenderState.class, pigState, visible);

            var avatar = new RemotePlayer(mc.level, mc.player.getGameProfile());
            avatar.setPos(mc.player.getX()+1, mc.player.getY(), mc.player.getZ()+2);
            var renderer = mc.getEntityRenderDispatcher().getRenderer(avatar);
            var state = new AvatarRenderState();
            renderer.getClass().getMethod("extractRenderState", Avatar.class, AvatarRenderState.class, float.class)
                .invoke(renderer, avatar, state, 1f);
            check(state.nameTag != null, "Avatar must have a vanilla render label");
            verifySubmission(renderer, renderer.getClass(), AvatarRenderState.class, state, visible);

            var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            check(info != null && mc.gui.getTabList().getNameForDisplay(info).getString().contains(mc.player.getGameProfile().name()),
                "In-world switch removed the nickname from TAB");
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not exercise entity name tag renderer", failure);
        }
    }

    private static void verifySubmission(Object renderer, Class<?> owner, Class<?> stateType, EntityRenderState state,
                                         boolean visible) throws ReflectiveOperationException {
        var submitted = new AtomicInteger();
        var collector = (SubmitNodeCollector) Proxy.newProxyInstance(SubmitNodeCollector.class.getClassLoader(),
            new Class<?>[]{SubmitNodeCollector.class}, (proxy, method, arguments) -> {
                if (method.getName().equals("submitNameTag")) submitted.incrementAndGet();
                if (method.getName().equals("order")) return proxy;
                return null;
            });
        Method submit = owner.getDeclaredMethod("submitNameTag", stateType, PoseStack.class, SubmitNodeCollector.class, CameraRenderState.class);
        submit.setAccessible(true);
        submit.invoke(renderer, state, new PoseStack(), collector, new CameraRenderState());
        check((submitted.get() > 0) == visible, "Unexpected name tag submission for " + stateType.getSimpleName());
    }
}
