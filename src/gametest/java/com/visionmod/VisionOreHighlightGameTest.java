package com.visionmod;

import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import static com.visionmod.GuiTestInput.check;

final class VisionOreHighlightGameTest {
    private static final BlockPos NEAR = new BlockPos(5, -59, 8);
    private static final BlockPos FAR_Z = new BlockPos(0, -59, 64);
    private static final BlockPos FAR_X = new BlockPos(64, -59, 0);
    private static final BlockPos OUTSIDE = new BlockPos(65, -59, 0);

    static void run(ClientGameTestContext context, TestSingleplayerContext world) throws Exception {
        int oldDistance = context.computeOnClient(mc -> mc.options.renderDistance().get());
        int oldRadius = context.computeOnClient(mc -> VisionPerformance.oreRadius);
        int oldColor = context.computeOnClient(mc -> OreColors.getColor(0));
        boolean oldOre = context.computeOnClient(mc -> VisionModClient.oreHighlight);
        boolean oldSelected = context.computeOnClient(mc -> OreSelection.enabled(0));
        boolean oldHud = context.computeOnClient(mc -> mc.options.hideGui);
        try {
            context.runOnClient(mc -> {
                mc.options.renderDistance().set(8);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.hideGui = true;
                VisionModClient.oreHighlight = true;
                VisionPerformance.oreRadius = 48;
                OreSelection.setEnabled(0, true);
                OreColors.setColor(0, 0xFFFF00FF);
            });
            world.getServer().runCommand("tp @p 0.5 -60 0.5 0 0");
            context.waitFor(mc -> mc.player.blockPosition().equals(new BlockPos(0, -60, 0))
                && mc.level.hasChunkAt(FAR_X) && mc.level.hasChunkAt(FAR_Z), 150);
            for (BlockPos pos : new BlockPos[]{NEAR, FAR_Z, FAR_X, OUTSIDE}) {
                world.getServer().runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " minecraft:diamond_ore");
                context.waitFor(mc -> mc.level.getBlockState(pos).is(Blocks.DIAMOND_ORE), 40);
            }
            context.waitFor(mc -> OreHighlight.outlined(NEAR), 70);
            context.runOnClient(mc -> {
                check(!OreHighlight.outlined(FAR_X) && !OreHighlight.outlined(FAR_Z), "Ore exceeded the selected 48-block radius");
                VisionHotkeys.cyclePerformanceValue(0);
                check(VisionPerformance.oreRadius == 64, "The radius setting could not reach 64 blocks");
                VisionPerformance.load();
                check(VisionPerformance.oreRadius == 64, "The 64-block radius did not survive a settings reload");
            });
            context.waitFor(mc -> OreHighlight.outlined(FAR_X) && OreHighlight.outlined(FAR_Z), 150);
            context.runOnClient(mc -> check(!OreHighlight.outlined(OUTSIDE), "Ore at 65 blocks was selected"));

            world.getServer().runCommand("setblock 5 -59 8 air");
            context.waitFor(mc -> !OreHighlight.outlined(NEAR) && OreHighlight.outlined(FAR_Z), 40);
            context.waitTicks(4);
            VisualWorldGameTest.capture(context, "40-ore-highlight-64-blocks");
            check(hasMagentaOutline(Path.of("vision-world-captures", "40-ore-highlight-64-blocks.png")),
                "The far ore was indexed but its colored outline was missing from the framebuffer");

            context.runOnClient(mc -> OreSelection.setEnabled(0, false));
            context.waitFor(mc -> !OreHighlight.outlined(FAR_Z) && !OreHighlight.outlined(FAR_X), 10);
            System.out.println("[VisionMod][ORE] PASS: selected radius, 64 blocks on both axes, bounds, block changes, ore switch and distant framebuffer outline");
        } finally {
            context.runOnClient(mc -> {
                mc.options.renderDistance().set(oldDistance);
                mc.options.hideGui = oldHud;
                VisionPerformance.oreRadius = oldRadius;
                VisionModClient.oreHighlight = oldOre;
                VisionPerformance.save();
                OreColors.setColor(0, oldColor);
                OreSelection.setEnabled(0, oldSelected);
            });
        }
    }

    private static boolean hasMagentaOutline(Path path) throws Exception {
        var image = ImageIO.read(path.toFile());
        for (int y = image.getHeight() / 2 - 18; y <= image.getHeight() / 2 + 18; y++) {
            for (int x = image.getWidth() / 2 - 18; x <= image.getWidth() / 2 + 18; x++) {
                int rgb = image.getRGB(x, y);
                int red = (rgb >>> 16) & 255, green = (rgb >>> 8) & 255, blue = rgb & 255;
                if (red > 180 && blue > 180 && green < 100) return true;
            }
        }
        return false;
    }
}
