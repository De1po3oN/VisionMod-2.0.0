package com.visionmod;

import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.util.Properties;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;
import static com.visionmod.GuiTestInput.check;

/** Exercise nearby discovery, adding from a visible row, and removing a saved friend. */
final class VisionFriendZoneGameTest {
    private static final int NEAR_ID = 1_567_801;
    private static final int FAR_ID = 1_567_802;

    static void run(ClientGameTestContext context) throws Exception {
        String previous = context.computeOnClient(mc -> VisionExtendedFeatures.friendNames());
        try {
            context.runOnClient(mc -> {
                VisionExtendedFeatures.setFriendNames("SavedFriend, savedfriend");
                addAvatar(mc, NEAR_ID, "NearbyFriend", 6);
                addAvatar(mc, FAR_ID, "DistantPlayer", 55);
                var nearby = VisionExtendedFeatures.nearbyPlayers(mc);
                check(nearby.size() == 1 && nearby.getFirst().name().equals("NearbyFriend") && nearby.getFirst().distance() == 6,
                    "Nearby list must contain only other players within 48 blocks");
            });
            context.setScreen(VisionDashboardScreen::new);
            context.waitTicks(2);
            double[] dashboard = context.computeOnClient(mc -> {
                var screen = mc.screen;
                var layout = DashboardLayout.of(screen.width, screen.height);
                int left = (screen.width-layout.width())/2, top = (screen.height-layout.height())/2;
                return new double[]{left+layout.contentLeft()+35, top+layout.searchTop()+13,
                    left+layout.cardLeft(0)+16, top+layout.cardTop(0)+12,
                    left+44+(layout.width()-58)/2, top+layout.height()-59};
            });
            GuiTestInput.click(context, dashboard[0], dashboard[1], 0);
            context.getInput().typeChars("Friend Zone");
            context.waitTicks(2);
            GuiTestInput.click(context, dashboard[2], dashboard[3], 1);
            context.waitTicks(2);
            GuiTestInput.click(context, dashboard[4], dashboard[5], 0);
            context.waitTicks(2);
            double[] panel = context.computeOnClient(mc -> {
                check(mc.screen instanceof VisionFriendZoneScreen, "Friend Zone settings did not open from the module card");
                var screen = (VisionFriendZoneScreen) mc.screen;
                int top = screen.layout.bodyTop();
                return new double[]{screen.x+screen.w-55, top+42,
                    screen.x+screen.w-48, top+11};
            });
            GuiTestInput.click(context, panel[0], panel[1], 0);
            context.waitTicks(2);
            context.runOnClient(mc -> {
                check(VisionExtendedFeatures.friendList().equals(java.util.List.of("SavedFriend", "NearbyFriend")),
                    "Clicking the nearby row did not preserve existing friends and add exactly one nickname");
                mc.level.removeEntity(NEAR_ID, Entity.RemovalReason.DISCARDED);
            });
            verifySaved("SavedFriend, NearbyFriend");
            GuiTestInput.click(context, panel[2], panel[3], 0);
            context.waitTicks(12);
            VisualWorldGameTest.capture(context, "39-friend-zone-saved-friends");
            GuiTestInput.click(context, panel[0], panel[1], 0);
            context.waitTicks(2);
            context.runOnClient(mc -> check(VisionExtendedFeatures.friendList().equals(java.util.List.of("NearbyFriend")),
                "The saved tab could not remove an offline friend"));
            verifySaved("NearbyFriend");
            context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
            context.waitTicks(2);
            check(context.computeOnClient(mc -> mc.screen == null), "Friend settings did not close by the menu key");
            context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
            context.waitTicks(2);
            context.runOnClient(mc -> check(mc.screen instanceof VisionFriendZoneScreen
                && (boolean)GuiTestInput.field(mc.screen, "savedTab"), "Reopening lost Friend Zone's selected tab"));
            System.out.println("[VisionMod][FRIEND_ZONE] PASS: distance and self filtering, add from nearby list, saved nicks, offline removal, menu resume");
        } finally {
            context.runOnClient(mc -> {
                if (mc.level != null) {
                    mc.level.removeEntity(NEAR_ID, Entity.RemovalReason.DISCARDED);
                    mc.level.removeEntity(FAR_ID, Entity.RemovalReason.DISCARDED);
                }
                VisionExtendedFeatures.setFriendNames(previous);
            });
            context.setScreen(() -> null);
        }
    }

    private static void addAvatar(Minecraft mc, int id, String name, int distance) {
        var avatar = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), name));
        avatar.setId(id);
        avatar.setPos(mc.player.getX()+distance, mc.player.getY(), mc.player.getZ());
        mc.level.addEntity(avatar);
    }

    private static void verifySaved(String expected) throws Exception {
        Properties settings = new Properties();
        try (var input = Files.newInputStream(FabricLoader.getInstance().getConfigDir().resolve("visionmod/extended-2.properties"))) {
            settings.load(input);
        }
        check(expected.equals(settings.getProperty("friends")), "Friend changes did not reach the settings file");
    }
}
