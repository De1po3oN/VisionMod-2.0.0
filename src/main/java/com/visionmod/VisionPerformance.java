package com.visionmod;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** CPU budgets for Vision Mod effects. The separately enabled render booster changes Minecraft video options. */
public final class VisionPerformance {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("visionmod/performance.properties");
    static final int[] ORE_RADII = {4, 6, 8, 12, 16, 24, 32, 48, 64};
    public static int oreRadius = 6;
    public static int checksPerTick = 80;
    public static int outlineLimit = 48;
    public static String preset = "MAX_FPS";
    public static boolean adaptive = true;
    public static boolean limitHudOnBusySpawn = true;
    public static boolean reduceAmbientOnBusySpawn = true;
    public static int busyPlayerThreshold = 16;
    private static int nearbyPlayers;
    private static int scanTicks;
    private static long lastFrameNanos;
    private static double frameTimeMillis = 16.7;

    private VisionPerformance() {}
    public static void tick(Minecraft client) {
        if (client.level == null || client.player == null) {
            nearbyPlayers = scanTicks = 0;
            lastFrameNanos = 0;
            return;
        }
        if (++scanTicks >= 40) {
            scanTicks = 0;
            nearbyPlayers = client.level.players().size();
        }
    }
    public static void observeFrame() {
        long now = System.nanoTime();
        if (lastFrameNanos != 0) {
            double elapsed = (now - lastFrameNanos) / 1_000_000.0;
            if (elapsed > 1 && elapsed < 250) frameTimeMillis = frameTimeMillis * .92 + elapsed * .08;
        }
        lastFrameNanos = now;
    }
    public static int loadedPlayers() { return nearbyPlayers; }
    public static int estimatedFps() { return (int)Math.round(1000.0 / Math.max(1, frameTimeMillis)); }
    public static boolean busy() { return adaptive && (nearbyPlayers >= busyPlayerThreshold || frameTimeMillis >= 40.0); }
    public static int effectiveChecksPerTick() { return busy() ? Math.max(24, checksPerTick / (frameTimeMillis > 50 ? 4 : 2)) : checksPerTick; }
    public static int effectiveOutlineLimit() { return busy() ? Math.max(16, outlineLimit / 2) : outlineLimit; }
    public static boolean drawHearts() { return !busy() || !limitHudOnBusySpawn; }
    /** User-enabled local particles stay visible even at spawn; only frequency is reduced. */
    public static int ambientInterval() { return busy() && reduceAmbientOnBusySpawn ? 10 : 4; }
    public static boolean spawnAmbient() { return true; }

    public static void load() {
        if (!Files.isRegularFile(FILE)) { maxFps(); return; }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            p.load(in);
            oreRadius = choice(number(p,"oreRadius",6),ORE_RADII,6);
            checksPerTick = choice(number(p,"checksPerTick",80),new int[]{40,80,160,320},80);
            outlineLimit = choice(number(p,"outlineLimit",48),new int[]{32,48,96,160,240},48);
            busyPlayerThreshold = choice(number(p,"busyPlayerThreshold",16),new int[]{8,12,16,24,32},16);
            adaptive = flag(p,"adaptive",true);
            limitHudOnBusySpawn = flag(p,"limitHudOnBusy",true);
            reduceAmbientOnBusySpawn = flag(p,"reduceAmbientOnBusy",true);
            String value = p.getProperty("preset","CUSTOM");
            preset = value.matches("MAX_FPS|BALANCED|VISUAL|CUSTOM") ? value : "CUSTOM";
            VisionModClient.fullBright = flag(p,"fullBright",true);
            VisionModClient.clearWater = flag(p,"clearWater",true);
            VisionModClient.clearLava = flag(p,"clearLava",true);
            VisionModClient.hideBlockParticles = flag(p,"hideBlockParticles",true);
            VisionModClient.hideSprintParticles = flag(p,"hideSprintParticles",true);
            VisionModClient.hideEnderChestParticles = flag(p,"hideEnderChestParticles",true);
            VisionModClient.oreHighlight = flag(p,"oreHighlight",false);
            VisionModClient.playerHearts = flag(p,"playerHearts",false);
            VisionModClient.particles = flag(p,"particleFilter",true);
            VisionModClient.hideAllParticles = flag(p,"hideAllParticles",true);
        } catch (Exception e) { System.err.println("[VisionMod] Invalid performance config: " + e); maxFps(); }
    }
    private static int choice(int v,int[] all,int fallback) { for(int t:all)if(v==t)return v; return fallback; }
    private static int number(Properties p,String key,int fallback) {
        try { return Integer.parseInt(p.getProperty(key,Integer.toString(fallback))); }
        catch(NumberFormatException e){return fallback;}
    }
    private static boolean flag(Properties p,String key,boolean fallback) {
        String v=p.getProperty(key);
        return v==null?fallback:Boolean.parseBoolean(v);
    }
    public static void maxFps() {
        preset = "MAX_FPS";
        oreRadius = 6; checksPerTick = 80; outlineLimit = 48;
        adaptive = true; limitHudOnBusySpawn = true; reduceAmbientOnBusySpawn = true; busyPlayerThreshold = 12;
        VisionModClient.oreHighlight = false; VisionModClient.playerHearts = false;
        VisionModClient.particles = true; VisionModClient.hideAllParticles = true;
        // Keep user-selected ambient effects: they already have a strict per-tick budget.
        VisionExtras.targetOutline = false;
        VisionRenderBoost.enabled = true;
        VisionRenderBoost.chunkCap = 4;
        VisionRenderBoost.save();
        save();
    }
    public static void balanced() {
        preset = "BALANCED";
        oreRadius = 8; checksPerTick = 160; outlineLimit = 96;
        adaptive = true; limitHudOnBusySpawn = true; reduceAmbientOnBusySpawn = true; busyPlayerThreshold = 16;
        VisionModClient.oreHighlight = true; VisionModClient.playerHearts = false;
        VisionModClient.particles = true; VisionModClient.hideAllParticles = false;
        VisionRenderBoost.enabled = false;
        VisionRenderBoost.save();
        save();
    }
    public static void visual() {
        preset = "VISUAL";
        oreRadius = 16; checksPerTick = 320; outlineLimit = 240;
        adaptive = false; limitHudOnBusySpawn = false; reduceAmbientOnBusySpawn = false; busyPlayerThreshold = 32;
        VisionModClient.oreHighlight = true; VisionModClient.playerHearts = true;
        VisionModClient.particles = false; VisionModClient.hideAllParticles = false;
        VisionRenderBoost.enabled = false;
        VisionRenderBoost.save();
        save();
    }
    public static void custom() { preset = "CUSTOM"; save(); }
    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Properties p = new Properties();
            p.setProperty("fullBright",Boolean.toString(VisionModClient.fullBright));
            p.setProperty("clearWater",Boolean.toString(VisionModClient.clearWater));
            p.setProperty("clearLava",Boolean.toString(VisionModClient.clearLava));
            p.setProperty("hideBlockParticles",Boolean.toString(VisionModClient.hideBlockParticles));
            p.setProperty("hideSprintParticles",Boolean.toString(VisionModClient.hideSprintParticles));
            p.setProperty("hideEnderChestParticles",Boolean.toString(VisionModClient.hideEnderChestParticles));
            p.setProperty("preset",preset);
            p.setProperty("oreRadius",Integer.toString(oreRadius));
            p.setProperty("checksPerTick",Integer.toString(checksPerTick));
            p.setProperty("outlineLimit",Integer.toString(outlineLimit));
            p.setProperty("adaptive",Boolean.toString(adaptive));
            p.setProperty("limitHudOnBusy",Boolean.toString(limitHudOnBusySpawn));
            p.setProperty("reduceAmbientOnBusy",Boolean.toString(reduceAmbientOnBusySpawn));
            p.setProperty("busyPlayerThreshold",Integer.toString(busyPlayerThreshold));
            p.setProperty("oreHighlight",Boolean.toString(VisionModClient.oreHighlight));
            p.setProperty("playerHearts",Boolean.toString(VisionModClient.playerHearts));
            p.setProperty("particleFilter",Boolean.toString(VisionModClient.particles));
            p.setProperty("hideAllParticles",Boolean.toString(VisionModClient.hideAllParticles));
            try(OutputStream out=Files.newOutputStream(FILE)){p.store(out,"Vision Mod 1.0.0 performance settings");}
        }catch(Exception e){System.err.println("[VisionMod] Could not save performance options: "+e);}
    }
}
