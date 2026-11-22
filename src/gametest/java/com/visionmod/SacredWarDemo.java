package com.visionmod;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;

/** A live player and real provider lookup. No fixtures, lyric files or cache injection. */
final class SacredWarDemo {
    static void run(ClientGameTestContext context) throws Exception {
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("time set day");
            world.getServer().runCommand("weather clear");
            world.getServer().runCommand("tp @p 0 -60 0 0 0");
            context.runOnClient(mc -> {
                VisionModClient.enabled = true;
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.hideGui = true;
                if (!VisionExtendedFeatures.enabled(3)) VisionExtendedFeatures.toggle(3);
                if (VisionLyricsSource.hasImport() || !VisionExtendedFeatures.customText().isBlank()) {
                    throw new AssertionError("Demo requires a clean config with no provided text");
                }
            });
            world.getClientWorld().waitForChunksRender();
            playerCommand("Play");
            context.waitFor(mc -> VisionLyricsSource.mediaConnected() && VisionLyricsSource.positionKnown(), 240);
            context.runOnClient(mc -> System.out.println("[SACRED_WAR] Native track: " + VisionLyricsSource.trackLabel()
                + " (" + VisionLyricsSource.durationMs() + " ms)"));
            context.waitFor(mc -> VisionLyricsSource.hasTimedLyrics() && !VisionLyricsSource.current().isBlank(), 850);
            context.waitTicks(5);
            String first = context.computeOnClient(mc -> VisionWorldLyrics.lastRenderedText());
            if (first.isBlank()) throw new AssertionError("Timings loaded but first world line did not render");
            VisualWorldGameTest.capture(context, "sacred-war-live-01");
            context.waitFor(mc -> {
                String text = VisionLyricsSource.current();
                return text != null && !text.isBlank() && !text.equals(first) && text.equals(VisionWorldLyrics.lastRenderedText());
            }, 360);
            context.waitTicks(5);
            VisualWorldGameTest.capture(context, "sacred-war-live-02");
            context.setScreen(VisionLyricsScreen::new);
            context.waitTicks(3);
            VisualWorldGameTest.capture(context, "sacred-war-source");
            context.runOnClient(mc -> System.out.println("[SACRED_WAR] PASS: real VLC playback, changing world lyrics, " + VisionLyricsSource.status()));
        }
    }

    private static void playerCommand(String method) throws IOException, InterruptedException {
        Process call = new ProcessBuilder("gdbus", "call", "--session", "--dest", "org.mpris.MediaPlayer2.vlc",
            "--object-path", "/org/mpris/MediaPlayer2", "--method", "org.mpris.MediaPlayer2.Player." + method)
            .inheritIO().start();
        if (!call.waitFor(8, TimeUnit.SECONDS)) {
            call.destroyForcibly();
            throw new IOException("VLC command timed out");
        }
        if (call.exitValue() != 0) throw new IOException("VLC did not accept " + method);
    }
}
