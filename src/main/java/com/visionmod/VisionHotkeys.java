package com.visionmod;

import net.fabricmc.loader.api.FabricLoader;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;

/**
 * Single assignment table for nearly every gameplay-facing Vision Mod control.
 * Existing 0.8.1 slots (0..32) have not been renumbered: old configs keep
 * exactly the same bindings. Mod menus use MMB to start capture.
 *
 * IDs: visuals 7, extras 16, ores 10, particle filters 4, FPS presets 3,
 * performance values 5, performance switches 3, extra adjustments 3, booster 2, advanced 3 = 56.
 */
public final class VisionHotkeys {
    public static final int VISUAL_COUNT = 7;
    public static final int EXTRAS_COUNT = 16;
    public static final int ORE_COUNT = 10;
    public static final int PARTICLE_COUNT = 4;
    public static final int PRESET_COUNT = 3;
    public static final int PERFORMANCE_VALUE_COUNT = 5;
    public static final int PERFORMANCE_TOGGLE_COUNT = 3;
    public static final int EXTRA_SETTING_COUNT = 3;
    public static final int LEGACY_COUNT = VISUAL_COUNT + EXTRAS_COUNT + ORE_COUNT;
    public static final int PREVIOUS_COUNT = LEGACY_COUNT + PARTICLE_COUNT + PRESET_COUNT
        + PERFORMANCE_VALUE_COUNT + PERFORMANCE_TOGGLE_COUNT + EXTRA_SETTING_COUNT;
    public static final int PREVIOUS_53_COUNT = PREVIOUS_COUNT + 2;
    private static final int COUNT = PREVIOUS_53_COUNT + 3; // Three new advanced controls.

    private static final int[] bindings = new int[COUNT];
    private static final boolean[] lastDown = new boolean[COUNT];
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
        .resolve("visionmod/hotkeys.properties");

    static {
        bindings[visual(6)] = GLFW.GLFW_KEY_LEFT_ALT;
        bindings[extra(4)] = GLFW.GLFW_KEY_C;
    }

    private VisionHotkeys() { }

    public static int visual(int index) { return index; }
    public static int extra(int index) { return VISUAL_COUNT + index; }
    public static int ore(int index) { return VISUAL_COUNT + EXTRAS_COUNT + index; }
    public static int particle(int index) { return LEGACY_COUNT + index; }
    public static int preset(int index) { return particle(PARTICLE_COUNT) + index; }
    public static int performanceValue(int index) { return preset(PRESET_COUNT) + index; }
    public static int performanceToggle(int index) { return performanceValue(PERFORMANCE_VALUE_COUNT) + index; }
    public static int extraSetting(int index) { return performanceToggle(PERFORMANCE_TOGGLE_COUNT) + index; }

    public static int boostToggle() { return PREVIOUS_COUNT; }
    public static int boostCap() { return PREVIOUS_COUNT+1; }
    public static int totemAnimationToggle() { return PREVIOUS_53_COUNT; }
    public static int totemAnimationSize() { return PREVIOUS_53_COUNT + 1; }
    public static int shulkerGridToggle() { return PREVIOUS_53_COUNT + 2; }
    public static int count() { return COUNT; }
    public static int[] snapshot() { return bindings.clone(); }

    public static boolean valid(int binding) {
        return binding == 0
            || binding >= GLFW.GLFW_KEY_SPACE && binding <= GLFW.GLFW_KEY_LAST
            || binding <= -1 && binding >= -8;
    }

    /** Import older 33/51-slot arrays and current 53-slot arrays. */
    public static void replaceAll(int[] preset) {
        if (preset == null || preset.length != LEGACY_COUNT && preset.length != PREVIOUS_COUNT && preset.length != PREVIOUS_53_COUNT && preset.length != COUNT)
            throw new IllegalArgumentException("Invalid hotkey count");
        int[] complete = Arrays.copyOf(preset, COUNT);
        HashSet<Integer> used = new HashSet<>();
        for (int value : complete) {
            if (!valid(value) || value != 0 && !used.add(value))
                throw new IllegalArgumentException("Invalid or repeated hotkey");
        }
        System.arraycopy(complete, 0, bindings, 0, COUNT);
        Arrays.fill(lastDown, false);
        save();
    }

    /** -1 = left mouse button; -2 = right; -3 = middle. Zero = unbound. */
    public static void assign(int id, int binding) {
        if (id < 0 || id >= COUNT || !valid(binding)) return;
        if (binding != 0) {
            // One key should never silently toggle multiple Vision Mod actions.
            for (int i = 0; i < COUNT; i++) {
                if (i != id && bindings[i] == binding) bindings[i] = 0;
            }
        }
        bindings[id] = binding;
        Arrays.fill(lastDown, false);
        save();
    }

    public static void init() {
        if (!Files.isRegularFile(FILE)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(FILE)) {
            properties.load(input);
            for (int i = 0; i < COUNT; i++) {
                String raw = properties.getProperty("binding." + i);
                if (raw == null) continue;
                try {
                    int value = Integer.parseInt(raw);
                    if (valid(value)) bindings[i] = value;
                } catch (NumberFormatException ignored) { }
            }
            // Older hand-edited files might have duplicate keys. De-duplicate.
            HashSet<Integer> seen = new HashSet<>();
            for (int i = 0; i < COUNT; i++) {
                if (bindings[i] != 0 && !seen.add(bindings[i])) bindings[i] = 0;
            }
        } catch (Exception ex) {
            System.err.println("[VisionMod] Failed to read hotkeys: " + ex);
        }
    }

    public static String label(int id) {
        boolean held = isHold(id);
        if (id < 0 || id >= COUNT || bindings[id] == 0)
            return held ? "Hold: unbound" : "MMB: bind";
        int code = bindings[id];
        if (code < 0) return (held ? "Hold: " : "") + "Mouse " + (-code);
        String readable = switch (code) {
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "L Shift";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "R Shift";
            case GLFW.GLFW_KEY_LEFT_ALT -> "L Alt";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "R Alt";
            case GLFW.GLFW_KEY_SPACE -> "Space";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_ENTER -> "Enter";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "L Ctrl";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "R Ctrl";
            default -> {
                String name = GLFW.glfwGetKeyName(code, 0);
                yield name != null ? name.toUpperCase(java.util.Locale.ROOT) : "Key " + code;
            }
        };
        return held ? "Hold: " + readable : readable;
    }

    /** Only Free Look and Zoom are held; everything else fires once per key press. */
    private static boolean isHold(int id) {
        return id == visual(6) || id == extra(4);
    }

    public static boolean holding(int id, Minecraft client) {
        if (id < 0 || id >= COUNT || client.screen != null) return false;
        return pressed(bindings[id], client);
    }

    private static boolean pressed(int binding, Minecraft client) {
        if (binding > 0) return InputConstants.isKeyDown(client.getWindow(),binding);
        if (binding < 0) return GLFW.glfwGetMouseButton(client.getWindow().handle(), -binding - 1) == GLFW.GLFW_PRESS;
        return false;
    }

    public static void tick(Minecraft client) {
        if (client.player == null || client.level == null) {
            Arrays.fill(lastDown, false);
            return;
        }
        boolean gameplay = client.screen == null && VisionModClient.enabled;
        for (int i = 0; i < COUNT; i++) {
            boolean down = pressed(bindings[i], client);
            if (gameplay && down && !lastDown[i] && !isHold(i)) trigger(i);
            lastDown[i] = down;
        }
    }

    private static void trigger(int id) {
        if (id == totemAnimationToggle()) { VisionAdvanced.smallTotemAnimation = !VisionAdvanced.smallTotemAnimation; VisionAdvanced.save(); return; }
        if (id == totemAnimationSize()) { VisionAdvanced.cycleTotemSize(); return; }
        if (id == shulkerGridToggle()) { VisionAdvanced.shulkerGrid = !VisionAdvanced.shulkerGrid; VisionAdvanced.save(); return; }
        if (id == boostToggle()) { VisionRenderBoost.toggle(); return; }
        if (id == boostCap()) { VisionRenderBoost.cycleCap(); return; }
        if (id < extra(0)) {
            switch (id) {
                case 0 -> VisionModClient.fullBright = !VisionModClient.fullBright;
                case 1 -> VisionModClient.oreHighlight = !VisionModClient.oreHighlight;
                case 2 -> VisionModClient.clearWater = !VisionModClient.clearWater;
                case 3 -> VisionModClient.clearLava = !VisionModClient.clearLava;
                case 4 -> VisionModClient.playerHearts = !VisionModClient.playerHearts;
                case 5 -> VisionModClient.particles = !VisionModClient.particles;
                default -> { return; }
            }
            VisionPerformance.custom();
        } else if (id < ore(0)) {
            VisionExtras.toggle(id - extra(0));
        } else if (id < particle(0)) {
            OreSelection.toggle(id - ore(0));
        } else if (id < preset(0)) {
            toggleParticle(id - particle(0));
        } else if (id < performanceValue(0)) {
            applyPreset(id - preset(0));
        } else if (id < performanceToggle(0)) {
            cyclePerformanceValue(id - performanceValue(0));
        } else if (id < extraSetting(0)) {
            togglePerformance(id - performanceToggle(0));
        } else {
            cycleExtraSetting(id - extraSetting(0));
        }
    }

    /** Shared between the hotkeys and clickable settings pages. */
    public static void toggleParticle(int index) {
        switch (index) {
            case 0 -> VisionModClient.hideBlockParticles = !VisionModClient.hideBlockParticles;
            case 1 -> VisionModClient.hideSprintParticles = !VisionModClient.hideSprintParticles;
            case 2 -> VisionModClient.hideEnderChestParticles = !VisionModClient.hideEnderChestParticles;
            case 3 -> VisionModClient.hideAllParticles = !VisionModClient.hideAllParticles;
            default -> { return; }
        }
        VisionPerformance.custom();
    }

    public static boolean particleEnabled(int index) {
        return switch (index) {
            case 0 -> VisionModClient.hideBlockParticles;
            case 1 -> VisionModClient.hideSprintParticles;
            case 2 -> VisionModClient.hideEnderChestParticles;
            case 3 -> VisionModClient.hideAllParticles;
            default -> false;
        };
    }

    public static void applyPreset(int index) {
        switch (index) {
            case 0 -> { VisionPerformance.maxFps(); VisionExtras.save(); }
            case 1 -> VisionPerformance.balanced();
            case 2 -> VisionPerformance.visual();
            default -> { return; }
        }
    }

    private static int next(int actual, int... choices) {
        for (int i = 0; i < choices.length; i++) {
            if (choices[i] == actual) return choices[(i + 1) % choices.length];
        }
        return choices[0];
    }

    public static void cyclePerformanceValue(int index) {
        switch (index) {
            case 0 -> VisionPerformance.oreRadius = next(VisionPerformance.oreRadius, VisionPerformance.ORE_RADII);
            case 1 -> VisionPerformance.checksPerTick = next(VisionPerformance.checksPerTick, 40,80,160,320);
            case 2 -> VisionPerformance.outlineLimit = next(VisionPerformance.outlineLimit, 32,48,96,160,240);
            case 3 -> VisionRenderBoost.cycleTargetFps();
            case 4 -> VisionPerformance.busyPlayerThreshold = next(VisionPerformance.busyPlayerThreshold, 8,12,16,24,32);
            default -> { return; }
        }
        VisionPerformance.custom();
    }

    public static void togglePerformance(int index) {
        switch (index) {
            case 0 -> VisionPerformance.adaptive = !VisionPerformance.adaptive;
            case 1 -> VisionPerformance.limitHudOnBusySpawn = !VisionPerformance.limitHudOnBusySpawn;
            case 2 -> VisionPerformance.reduceAmbientOnBusySpawn = !VisionPerformance.reduceAmbientOnBusySpawn;
            default -> { return; }
        }
        VisionPerformance.custom();
    }

    public static boolean performanceEnabled(int index) {
        return switch (index) {
            case 0 -> VisionPerformance.adaptive;
            case 1 -> VisionPerformance.limitHudOnBusySpawn;
            case 2 -> VisionPerformance.reduceAmbientOnBusySpawn;
            default -> false;
        };
    }

    public static void cycleExtraSetting(int index) {
        switch (index) {
            case 0 -> VisionExtras.cycleZoom();
            case 1 -> VisionExtras.cycleOutlineColor();
            case 2 -> VisionExtras.cycleCrosshairColor();
            default -> { return; }
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Properties properties = new Properties();
            for (int i = 0; i < COUNT; i++) {
                properties.setProperty("binding." + i, Integer.toString(bindings[i]));
            }
            try (OutputStream output = Files.newOutputStream(FILE)) {
                properties.store(output, "Vision Mod 1.0.0 - MMB to bind a module");
            }
        } catch (Exception ex) {
            System.err.println("[VisionMod] Could not save hotkeys: " + ex);
        }
    }
}
