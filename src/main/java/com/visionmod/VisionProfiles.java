package com.visionmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.awt.Desktop;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** Local, shareable JSON profiles. Never executes imported data. */
public final class VisionProfiles {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern VALID_NAME = Pattern.compile("[a-zA-Z0-9_-]{1,40}");
    private static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("visionmod/profiles");

    private VisionProfiles() {}

    public static Path directory() { return DIRECTORY; }

    private static Path path(String name) {
        if (name == null || !VALID_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Use 1-40 English letters, digits, _ or -");
        }
        return DIRECTORY.resolve(name + ".json");
    }

    public static List<String> list() {
        List<String> names = new ArrayList<>();
        try {
            Files.createDirectories(DIRECTORY);
            try (DirectoryStream<Path> files = Files.newDirectoryStream(DIRECTORY, "*.json")) {
                for (Path file : files) {
                    if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) continue;
                    String name = file.getFileName().toString();
                    name = name.substring(0, name.length() - 5);
                    if (VALID_NAME.matcher(name).matches()) names.add(name);
                }
            }
        } catch (Exception ignored) { }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public static void save(String name) throws Exception {
        Files.createDirectories(DIRECTORY);
        Path target = path(name);
        if (Files.isSymbolicLink(target)) throw new IllegalArgumentException("Symlinks are not supported");
        JsonObject data = new JsonObject();
        data.addProperty("format", 1);
        saveFlags(data,"hud2",VisionFeaturePack.count(),VisionFeaturePack::enabled);
        saveFlags(data,"world2",VisionWorldEffects.count(),VisionWorldEffects::enabled);
        saveFlags(data,"extended2",VisionExtendedFeatures.count(),VisionExtendedFeatures::enabled);
        saveFlags(data,"motion2",VisionMotionEffects.count(),VisionMotionEffects::selected);
        saveFlags(data,"tools2",VisionTools2.count(),VisionTools2::selected);
        data.addProperty("tabHeads",VisionTabHeads.enabled);
        data.addProperty("freeLookSmooth",FreeLook.smooth);
        data.addProperty("ownLyric",VisionExtendedFeatures.customText());
        data.addProperty("friends",VisionExtendedFeatures.friendNames());
        data.addProperty("trailStart",VisionWorldEffects.trailColorHex(VisionWorldEffects.trailStartColor()));
        data.addProperty("trailEnd",VisionWorldEffects.trailColorHex(VisionWorldEffects.trailEndColor()));
        data.addProperty("trailMode",VisionWorldEffects.trailMode());
        data.addProperty("trailWidth",VisionWorldEffects.trailWidth());
        data.addProperty("trailOpacity",VisionWorldEffects.trailOpacity());
        data.addProperty("spearDuration",VisionMotionEffects.spearDuration());
        data.addProperty("spearSize",Math.round(VisionMotionEffects.spearSize()*100));
        data.addProperty("spearColor",VisionWorldEffects.trailColorHex(VisionMotionEffects.spearColor()));
        data.addProperty("ambientStyle",VisionExtras.ambientStyle);
        data.addProperty("ambientDensity",VisionExtras.ambientDensity);
        data.addProperty("petColor",String.format("#%06X",VisionExtendedFeatures.petColor()&0xFFFFFF));
        data.addProperty("worldColor",VisionExtendedFeatures.worldColor());
        data.addProperty("fullBrightLevel",VisionAppearance.brightness);
        data.addProperty("handScale",VisionAppearance.handScale);
        data.addProperty("appearanceDetail",VisionAppearance.detail);
        data.addProperty("appearanceOpacity",VisionAppearance.opacity);
        data.addProperty("appearanceAnimations",VisionAppearance.animations);
        data.addProperty("smoothHotbar",VisionAppearance.smoothHotbar);
        data.addProperty("playerBadge",VisionAppearance.playerBadge);
        data.addProperty("hideEntityNames",VisionAppearance.hideEntityNames);
        data.addProperty("soundPreset",VisionExtendedFeatures.soundPreset());
        data.addProperty("fullBright", VisionModClient.fullBright);
        data.addProperty("oreHighlight", VisionModClient.oreHighlight);
        data.addProperty("clearWater", VisionModClient.clearWater);
        data.addProperty("clearLava", VisionModClient.clearLava);
        data.addProperty("playerHearts", VisionModClient.playerHearts);
        data.addProperty("particles", VisionModClient.particles);
        data.addProperty("hideBlockParticles", VisionModClient.hideBlockParticles);
        data.addProperty("hideSprintParticles", VisionModClient.hideSprintParticles);
        data.addProperty("hideEnderChestParticles", VisionModClient.hideEnderChestParticles);
        data.addProperty("hideAllParticles", VisionModClient.hideAllParticles);
        data.addProperty("freeLook", FreeLook.enabled);
        data.addProperty("cps", VisionExtras.cps);
        data.addProperty("armorHud", VisionExtras.armorHud);
        data.addProperty("hungerHud", VisionExtras.hungerHud);
        data.addProperty("customCrosshair", VisionExtras.crosshair);
        data.addProperty("zoom", VisionExtras.zoom);
        data.addProperty("fastScroll", VisionExtras.fastScroll);
        data.addProperty("targetOutline", VisionExtras.targetOutline);
        data.addProperty("ambientSnow", VisionExtras.snow);
        data.addProperty("ambientHearts", VisionExtras.hearts);
        data.addProperty("shulkerHud", VisionExtras.shulkerHud);
        data.addProperty("shulkerTooltip", VisionExtras.shulkerTooltip);
        data.addProperty("performanceAdaptive", VisionPerformance.adaptive);
        data.addProperty("performanceBusyThreshold", VisionPerformance.busyPlayerThreshold);
        data.addProperty("performanceLimitAmbientBusy", VisionPerformance.reduceAmbientOnBusySpawn);
        com.google.gson.JsonArray hotkeys = new com.google.gson.JsonArray();
        for (int key : VisionHotkeys.snapshot()) hotkeys.add(key);
        data.add("hotkeys", hotkeys);
        data.addProperty("arrowHitSound", VisionExtras.arrowHitSound);
        data.addProperty("smallSword", VisionExtras.smallSword);
        data.addProperty("smallShield", VisionExtras.smallShield);
        data.addProperty("smallTotem", VisionExtras.smallTotem);
        data.addProperty("lowFire", VisionExtras.lowFire);
        data.addProperty("crosshairPixels", VisionExtras.pixels());
        data.addProperty("crosshairSize", VisionExtras.crosshairSize);
        data.addProperty("crosshairGrid", VisionExtras.crosshairGrid);
        data.addProperty("themePalette", VisionMenuTheme.palette());
        data.addProperty("renderBoost", VisionRenderBoost.enabled);
        data.addProperty("renderBoostCap", VisionRenderBoost.chunkCap);
        data.addProperty("renderBoostTargetFps", VisionRenderBoost.targetFps);
        data.addProperty("renderBoostAdaptive", VisionRenderBoost.adaptiveChunks);
        data.addProperty("performanceLimitHudBusy", VisionPerformance.limitHudOnBusySpawn);
        data.addProperty("totemAnimation", VisionAdvanced.smallTotemAnimation);
        data.addProperty("totemAnimationPercent", VisionAdvanced.totemAnimationPercent);
        data.addProperty("shulkerGrid", VisionAdvanced.shulkerGrid);
        data.addProperty("crosshairColor", String.format("#%06X", VisionExtras.crosshairColor & 0xFFFFFF));
        data.addProperty("outlineColor", String.format("#%06X", VisionExtras.outlineColor & 0xFFFFFF));
        data.addProperty("zoomFactor", VisionExtras.zoomFactor);
        // Optional v0.7.0 settings; legacy v0.6.0 profile files stay loadable.
        data.addProperty("performanceRadius", VisionPerformance.oreRadius);
        data.addProperty("performanceBudget", VisionPerformance.checksPerTick);
        data.addProperty("performanceOutlines", VisionPerformance.outlineLimit);
        com.google.gson.JsonArray colors = new com.google.gson.JsonArray();
        com.google.gson.JsonArray ores = new com.google.gson.JsonArray();
        for (int i = 0; i < OreColors.NAMES.length; i++) {
            colors.add(String.format("#%06X", OreColors.getColor(i) & 0xFFFFFF));
            ores.add(OreSelection.enabled(i));
        }
        data.add("oreColors", colors);
        data.add("oreEnabled", ores);
        String output = GSON.toJson(data);
        Path temporary = Files.createTempFile(DIRECTORY, "profile-", ".tmp");
        try {
            Files.writeString(temporary, output, StandardCharsets.UTF_8);
            try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    public static void load(String name) throws Exception {
        Path file = path(name);
        if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) throw new IllegalArgumentException("Profile not found");
        if (Files.size(file) > 16384L) throw new IllegalArgumentException("Profile is too large");
        JsonObject data = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        if (!data.has("format") || data.get("format").getAsInt() != 1) throw new IllegalArgumentException("Unsupported profile format");
        boolean[] hud2=readFlags(data,"hud2",VisionFeaturePack.count());
        boolean[] world2=readFlags(data,"world2",VisionWorldEffects.count());
        boolean[] extended2=readFlags(data,"extended2",VisionExtendedFeatures.count());
        boolean[] motion2=readFlags(data,"motion2",VisionMotionEffects.count());
        boolean[] tools2=readFlags(data,"tools2",VisionTools2.count());
        boolean tabHeads=optionalFlag(data,"tabHeads",VisionTabHeads.enabled);
        boolean smooth=optionalFlag(data,"freeLookSmooth",FreeLook.smooth);
        boolean smoothHotbar=optionalFlag(data,"smoothHotbar",VisionAppearance.smoothHotbar);
        boolean playerBadge=optionalFlag(data,"playerBadge",VisionAppearance.playerBadge);
        boolean hideEntityNames=optionalFlag(data,"hideEntityNames",VisionAppearance.hideEntityNames);
        String ownLyric=optionalText(data,"ownLyric",VisionExtendedFeatures.customText());
        String friends=optionalText(data,"friends",VisionExtendedFeatures.friendNames(),VisionExtendedFeatures.FRIENDS_TEXT_LIMIT);
        int trailStart=optionalHex(data,"trailStart",VisionWorldEffects.trailStartColor());
        int trailEnd=optionalHex(data,"trailEnd",VisionWorldEffects.trailEndColor());
        int trailMode=optionalChoice(data,"trailMode",VisionWorldEffects.trailMode(),0,1,2);
        int appearanceDetail=optionalChoice(data,"appearanceDetail",VisionAppearance.detail,0,1,2);
        int appearanceOpacity=optionalRange(data,"appearanceOpacity",VisionAppearance.opacity,100,240);
        boolean appearanceAnimations=optionalFlag(data,"appearanceAnimations",VisionAppearance.animations);
        int soundPreset=optionalChoice(data,"soundPreset",VisionExtendedFeatures.soundPreset(),0,1,2);
        float trailWidth=optionalFloat(data,"trailWidth",VisionWorldEffects.trailWidth(),.04f,.30f);
        int trailOpacity=optionalRange(data,"trailOpacity",VisionWorldEffects.trailOpacity(),30,250);
        int spearDuration=optionalRange(data,"spearDuration",VisionMotionEffects.spearDuration(),300,1500);
        int spearSize=optionalRange(data,"spearSize",Math.round(VisionMotionEffects.spearSize()*100),50,175);
        int spearColor=optionalHex(data,"spearColor",VisionMotionEffects.spearColor());
        int ambientStyle=optionalChoice(data,"ambientStyle",VisionExtras.ambientStyle,0,1);
        int ambientDensity=optionalChoice(data,"ambientDensity",VisionExtras.ambientDensity,1,2,3);
        int petColor=optionalHex(data,"petColor",VisionExtendedFeatures.petColor());
        int worldColor=optionalChoice(data,"worldColor",VisionExtendedFeatures.worldColor(),0,1,2);
        int brightness=optionalRange(data,"fullBrightLevel",VisionAppearance.brightness,3,16);
        float handScale=optionalFloat(data,"handScale",VisionAppearance.handScale,.5f,.9f);
        // Validate every field before changing any mod settings.
        boolean[] switches = new boolean[] {
            flag(data, "fullBright"), flag(data, "oreHighlight"), flag(data, "clearWater"),
            flag(data, "clearLava"), flag(data, "playerHearts"), flag(data, "particles"),
            flag(data, "hideBlockParticles"), flag(data, "hideSprintParticles"),
            flag(data, "hideEnderChestParticles"), flag(data, "hideAllParticles"), flag(data, "freeLook")
        };
        var colors = data.getAsJsonArray("oreColors");
        var ores = data.getAsJsonArray("oreEnabled");
        if (colors == null || ores == null || colors.size() != OreColors.NAMES.length || ores.size() != OreColors.NAMES.length)
            throw new IllegalArgumentException("Ore settings are missing");
        int[] validatedColors = new int[colors.size()];
        boolean[] validatedOres = new boolean[ores.size()];
        for (int i = 0; i < colors.size(); i++) {
            String hex = colors.get(i).getAsString();
            if (!hex.matches("#[0-9A-Fa-f]{6}")) throw new IllegalArgumentException("Invalid ore color");
            validatedColors[i] = 0xFF000000 | Integer.parseInt(hex.substring(1), 16);
            if (!ores.get(i).isJsonPrimitive() || !ores.get(i).getAsJsonPrimitive().isBoolean())
                throw new IllegalArgumentException("Invalid ore toggle");
            validatedOres[i] = ores.get(i).getAsBoolean();
        }
        // Accept optional 0.7.0 numeric fields only if they match UI-supported values.
        int radius = optionalChoice(data, "performanceRadius", VisionPerformance.oreRadius, VisionPerformance.ORE_RADII);
        int budget = optionalChoice(data, "performanceBudget", VisionPerformance.checksPerTick, 40, 80, 160, 320);
        int outlines = optionalChoice(data, "performanceOutlines", VisionPerformance.outlineLimit, 32, 48, 96, 160, 240);
        // Profiles made by older releases omit extras and remain supported.
        boolean exCps = optionalFlag(data, "cps", VisionExtras.cps);
        boolean exArmor = optionalFlag(data, "armorHud", VisionExtras.armorHud);
        boolean exFood = optionalFlag(data, "hungerHud", VisionExtras.hungerHud);
        boolean exReticle = optionalFlag(data, "customCrosshair", VisionExtras.crosshair);
        boolean exZoom = optionalFlag(data, "zoom", VisionExtras.zoom);
        boolean exFast = optionalFlag(data, "fastScroll", VisionExtras.fastScroll);
        boolean exOutline = optionalFlag(data, "targetOutline", VisionExtras.targetOutline);
        boolean exSnow = optionalFlag(data, "ambientSnow", VisionExtras.snow);
        boolean exHearts = optionalFlag(data, "ambientHearts", VisionExtras.hearts);
        boolean exShulker = optionalFlag(data, "shulkerHud", VisionExtras.shulkerHud);
        boolean exShulkerTooltip = optionalFlag(data, "shulkerTooltip", VisionExtras.shulkerTooltip);
        boolean perfAdaptive = optionalFlag(data, "performanceAdaptive", VisionPerformance.adaptive);
        int busyThreshold = optionalChoice(data, "performanceBusyThreshold", VisionPerformance.busyPlayerThreshold, 8,12,16,24,32);
        boolean perfAmbient = optionalFlag(data, "performanceLimitAmbientBusy", VisionPerformance.reduceAmbientOnBusySpawn);
        boolean limitHud = optionalFlag(data, "performanceLimitHudBusy", VisionPerformance.limitHudOnBusySpawn);
        int[] profileHotkeys = null;
        if (data.has("hotkeys")) {
            com.google.gson.JsonArray keys = data.getAsJsonArray("hotkeys");
            if (keys == null || keys.size() != VisionHotkeys.count() &&
                keys.size() != VisionHotkeys.PREVIOUS_COUNT && keys.size() != VisionHotkeys.LEGACY_COUNT && keys.size() != VisionHotkeys.PREVIOUS_53_COUNT)
                throw new IllegalArgumentException("Invalid hotkeys list");
            profileHotkeys = new int[VisionHotkeys.count()];
            java.util.HashSet<Integer> seenKeys = new java.util.HashSet<>();
            for (int i = 0; i < keys.size(); i++) {
                if (!keys.get(i).isJsonPrimitive() || !keys.get(i).getAsJsonPrimitive().isNumber())
                    throw new IllegalArgumentException("Invalid hotkey");
                int key = keys.get(i).getAsInt();
                if (!VisionHotkeys.valid(key) || key != 0 && !seenKeys.add(key))
                    throw new IllegalArgumentException("Invalid or duplicate hotkey");
                profileHotkeys[i] = key;
            }
        }
        boolean exArrowHit = optionalFlag(data, "arrowHitSound", VisionExtras.arrowHitSound);
        boolean exSword = optionalFlag(data, "smallSword", VisionExtras.smallSword);
        boolean exShield = optionalFlag(data, "smallShield", VisionExtras.smallShield);
        boolean exTotem = optionalFlag(data, "smallTotem", VisionExtras.smallTotem);
        boolean exFire = optionalFlag(data, "lowFire", VisionExtras.lowFire);
        String crosshairPixels = data.has("crosshairPixels") ? data.get("crosshairPixels").getAsString() : VisionExtras.pixels();
        if (!VisionExtras.validPixelString(crosshairPixels))
            throw new IllegalArgumentException("Invalid crosshair drawing");
        int crosshairSize = optionalChoice(data,"crosshairSize",VisionExtras.crosshairSize,1,2,3,4);
        int themePalette = optionalChoice(data,"themePalette",VisionMenuTheme.palette(),0,1,2,3,4,5);
        boolean renderBoost = optionalFlag(data,"renderBoost",VisionRenderBoost.enabled);
        int renderBoostCap = optionalChoice(data,"renderBoostCap",VisionRenderBoost.chunkCap,4,6,8);
        int boostTarget = optionalChoice(data,"renderBoostTargetFps",VisionRenderBoost.targetFps,40,50,60,75);
        boolean adaptiveChunks = optionalFlag(data,"renderBoostAdaptive",VisionRenderBoost.adaptiveChunks);
        boolean totemAnimation = optionalFlag(data,"totemAnimation",VisionAdvanced.smallTotemAnimation);
        int totemAnimationPercent = optionalChoice(data,"totemAnimationPercent",VisionAdvanced.totemAnimationPercent,50,75,100);
        boolean shulkerGrid = optionalFlag(data,"shulkerGrid",VisionAdvanced.shulkerGrid);
        int exCrossColor = optionalHex(data, "crosshairColor", VisionExtras.crosshairColor);
        int exOutlineColor = optionalHex(data, "outlineColor", VisionExtras.outlineColor);
        float exZoomFactor = VisionExtras.zoomFactor;
        if (data.has("zoomFactor")) {
            exZoomFactor = data.get("zoomFactor").getAsFloat();
            if (exZoomFactor != .2f && exZoomFactor != .35f && exZoomFactor != .5f)
                throw new IllegalArgumentException("Unsupported zoom factor");
        }
        VisionWorldEffects.setTrailWidth(trailWidth);VisionWorldEffects.setTrailOpacity(trailOpacity);
        VisionMotionEffects.setSpearSettings(spearDuration,spearSize,spearColor);
        VisionExtras.ambientStyle=ambientStyle;VisionExtras.ambientDensity=ambientDensity;
        VisionExtendedFeatures.setPetColor(petColor);VisionExtendedFeatures.setWorldColor(worldColor);
        VisionAppearance.brightness=brightness;VisionAppearance.handScale=handScale;
        VisionAppearance.detail=appearanceDetail;VisionAppearance.opacity=appearanceOpacity;VisionAppearance.animations=appearanceAnimations;VisionAppearance.smoothHotbar=smoothHotbar;VisionAppearance.playerBadge=playerBadge;
        VisionAppearance.hideEntityNames=hideEntityNames;
        VisionAppearance.save();
        for(int i=0;i<3&&VisionExtendedFeatures.soundPreset()!=soundPreset;i++)VisionExtendedFeatures.cycleSound();
        applyFlags(hud2,VisionFeaturePack::enabled,VisionFeaturePack::toggle);
        applyFlags(world2,VisionWorldEffects::enabled,VisionWorldEffects::toggle);
        applyFlags(extended2,VisionExtendedFeatures::enabled,VisionExtendedFeatures::toggle);
        applyFlags(motion2,VisionMotionEffects::selected,VisionMotionEffects::toggle);
        applyFlags(tools2,VisionTools2::selected,VisionTools2::toggle);
        if(VisionTabHeads.enabled!=tabHeads)VisionTabHeads.toggle();
        FreeLook.smooth=smooth;
        VisionExtendedFeatures.setCustomText(ownLyric);
        VisionExtendedFeatures.setFriendNames(friends);
        VisionWorldEffects.setTrailColor(0,trailStart);
        VisionWorldEffects.setTrailColor(1,trailEnd);
        VisionWorldEffects.setTrailMode(trailMode);
        VisionModClient.fullBright = switches[0];
        VisionModClient.oreHighlight = switches[1];
        VisionModClient.clearWater = switches[2];
        VisionModClient.clearLava = switches[3];
        VisionModClient.playerHearts = switches[4];
        VisionModClient.particles = switches[5];
        VisionModClient.hideBlockParticles = switches[6];
        VisionModClient.hideSprintParticles = switches[7];
        VisionModClient.hideEnderChestParticles = switches[8];
        VisionModClient.hideAllParticles = switches[9];
        FreeLook.enabled = switches[10];
        VisionPerformance.oreRadius = radius;
        VisionPerformance.checksPerTick = budget;
        VisionPerformance.outlineLimit = outlines;
        VisionExtras.cps = exCps;
        VisionExtras.armorHud = exArmor;
        VisionExtras.hungerHud = exFood;
        VisionExtras.crosshair = exReticle;
        VisionExtras.zoom = exZoom;
        VisionExtras.fastScroll = exFast;
        VisionExtras.targetOutline = exOutline;
        VisionExtras.snow = exSnow;
        VisionExtras.hearts = exHearts;
        VisionExtras.shulkerHud = exShulker;
        VisionExtras.shulkerTooltip = exShulkerTooltip;
        VisionPerformance.adaptive = perfAdaptive;
        VisionPerformance.busyPlayerThreshold = busyThreshold;
        VisionPerformance.reduceAmbientOnBusySpawn = perfAmbient;
        VisionPerformance.limitHudOnBusySpawn = limitHud;
        VisionExtras.arrowHitSound = exArrowHit;
        VisionExtras.smallSword = exSword;
        VisionExtras.smallShield = exShield;
        VisionExtras.smallTotem = exTotem;
        VisionExtras.lowFire = exFire;
        VisionExtras.setPixels(crosshairPixels);
        VisionExtras.setCrosshairSize(crosshairSize);
        VisionMenuTheme.setPalette(themePalette);
        VisionRenderBoost.enabled = renderBoost;
        VisionRenderBoost.chunkCap = renderBoostCap;
        VisionRenderBoost.targetFps = boostTarget;
        VisionRenderBoost.adaptiveChunks = adaptiveChunks;
        VisionRenderBoost.save();
        VisionAdvanced.smallTotemAnimation = totemAnimation;
        VisionAdvanced.totemAnimationPercent = totemAnimationPercent;
        VisionAdvanced.shulkerGrid = shulkerGrid;
        VisionAdvanced.save();
        VisionExtras.crosshairColor = exCrossColor;
        VisionExtras.outlineColor = exOutlineColor;
        VisionExtras.zoomFactor = exZoomFactor;
        VisionExtras.save();
        VisionPerformance.custom();
        if (profileHotkeys != null) VisionHotkeys.replaceAll(profileHotkeys);
        for (int i = 0; i < validatedColors.length; i++) {
            OreColors.setColor(i, validatedColors[i]);
            OreSelection.setEnabled(i, validatedOres[i]);
        }
    }

    private static void saveFlags(JsonObject data,String key,int count,java.util.function.IntPredicate selected) {
        var values=new com.google.gson.JsonArray();
        for(int i=0;i<count;i++)values.add(selected.test(i));
        data.add(key,values);
    }
    private static boolean[] readFlags(JsonObject data,String key,int count) {
        if(!data.has(key))return null;
        var values=data.getAsJsonArray(key);
        if(values==null || values.size()!=count)throw new IllegalArgumentException("Invalid module list: "+key);
        boolean[] flags=new boolean[count];
        for(int i=0;i<count;i++){
            var v=values.get(i);
            if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Invalid toggle: "+key);
            flags[i]=v.getAsBoolean();
        }
        return flags;
    }
    private static void applyFlags(boolean[] values,java.util.function.IntPredicate selected,java.util.function.IntConsumer toggle) {
        if(values!=null)for(int i=0;i<values.length;i++)if(values[i]!=selected.test(i))toggle.accept(i);
    }
    private static String optionalText(JsonObject data,String key,String fallback) {
        return optionalText(data,key,fallback,120);
    }
    private static String optionalText(JsonObject data,String key,String fallback,int maxLength) {
        if(!data.has(key))return fallback;
        var v=data.get(key);
        if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString()||v.getAsString().length()>maxLength)
            throw new IllegalArgumentException("Invalid text: "+key);
        return v.getAsString();
    }

    private static float optionalFloat(JsonObject data,String key,float fallback,float min,float max){
        if(!data.has(key))return fallback;
        var v=data.get(key);
        if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Invalid number: "+key);
        float value=v.getAsFloat();
        if(!Float.isFinite(value)||value<min||value>max)throw new IllegalArgumentException("Out of range: "+key);
        return value;
    }
    private static int optionalRange(JsonObject data,String key,int fallback,int min,int max){
        if(!data.has(key))return fallback;
        var primitive=data.get(key);
        if(!primitive.isJsonPrimitive()||!primitive.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Invalid number: "+key);
        double d=primitive.getAsDouble();
        if(!Double.isFinite(d)||d!=Math.rint(d)||d<min||d>max)throw new IllegalArgumentException("Out of range: "+key);
        return (int)d;
    }

    private static boolean flag(JsonObject data, String key) {
        if (!data.has(key) || !data.get(key).isJsonPrimitive() || !data.getAsJsonPrimitive(key).isBoolean())
            throw new IllegalArgumentException("Invalid setting: " + key);
        return data.get(key).getAsBoolean();
    }

    private static int optionalChoice(JsonObject data, String key, int defaultValue, int... allowed) {
        if (!data.has(key)) return defaultValue;
        if (!data.get(key).isJsonPrimitive() || !data.get(key).getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Invalid performance setting: " + key);
        int actual = data.get(key).getAsInt();
        for (int value : allowed) if (actual == value) return actual;
        throw new IllegalArgumentException("Unsupported performance setting: " + key);
    }

    private static int optionalHex(JsonObject data, String key, int fallback) {
        if (!data.has(key)) return fallback;
        String color = data.get(key).getAsString();
        if (!color.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Invalid color: " + key);
        return 0xFF000000 | Integer.parseInt(color.substring(1), 16);
    }

    private static boolean optionalFlag(JsonObject data, String key, boolean defaultValue) {
        return data.has(key) ? flag(data, key) : defaultValue;
    }

    public static void delete(String name) throws Exception {
        Path target = path(name);
        if (Files.isSymbolicLink(target)) throw new IllegalArgumentException("Symlinks are not supported");
        Files.deleteIfExists(target);
    }

    public static boolean openFolder() {
        try {
            Files.createDirectories(DIRECTORY);
            if (!Desktop.isDesktopSupported()) return false;
            Desktop.getDesktop().open(DIRECTORY.toFile());
            return true;
        } catch (Exception ignored) { return false; }
    }
}
