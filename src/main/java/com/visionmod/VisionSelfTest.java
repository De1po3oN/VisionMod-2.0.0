package com.visionmod;

import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Properties;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/**
 * CI-only live-client test of feature-toggle wiring and persisted settings.
 * This catches an entire class of "button exists but changes nothing" bugs.
 * It DOES NOT verify rendered visuals, damage mechanics or FPS.
 */
public final class VisionSelfTest {
    private VisionSelfTest() {}

    public static void run() {
        int passed = 0;
        passed += verify("HUD/COMBAT", VisionFeaturePack.NAMES,
            VisionFeaturePack::enabled, VisionFeaturePack::toggle,
            "features-2.properties", "feature.");
        passed += verify("WORLD FX", VisionWorldEffects.NAMES,
            VisionWorldEffects::enabled, VisionWorldEffects::toggle,
            "world-effects-2.properties", "effect.");
        passed += verify("EXTRA FX", VisionExtendedFeatures.NAMES,
            VisionExtendedFeatures::enabled, VisionExtendedFeatures::toggle,
            "extended-2.properties", "feature.");
        passed += verify("MOTION FX", VisionMotionEffects.NAMES,
            VisionMotionEffects::selected, VisionMotionEffects::toggle,
            "motion-2.properties", "motion.");
        passed += verify("TOOLS", VisionTools2.NAMES,
            VisionTools2::selected, VisionTools2::toggle,
            "tools-2.properties", "tool.");
        String[] extras = {
            "CPS", "Armor HUD", "Food HUD", "Crosshair", "Zoom", "Fast Scroll",
            "Outline", "Snowflakes", "Air Hearts", "Shulker HUD", "Arrow Hit",
            "Small Sword", "Small Shield", "Small Totem", "Low Fire", "Shulker Tooltip"
        };
        String[] extrasKeys = {
            "cps", "armorHud", "hungerHud", "crosshair", "zoom", "fastScroll",
            "targetOutline", "snow", "hearts", "shulkerHud", "arrowHitSound",
            "smallSword", "smallShield", "smallTotem", "lowFire", "shulkerTooltip"
        };
        passed += verifyKeys("LEGACY EXTRAS", extras, extrasKeys,
            VisionExtras::enabled, VisionExtras::toggle,
            FabricLoader.getInstance().getConfigDir().resolve("visionmod/extras.properties"));

        String[] oreNames = {
            "Diamond", "Debris", "Iron", "Gold", "Redstone", "Emerald",
            "Lapis", "Copper", "Coal", "Quartz"
        };
        String[] oreKeys = new String[oreNames.length];
        for (int i=0;i<oreKeys.length;i++) oreKeys[i]="enabled."+i;
        passed += verifyKeys("ORES", oreNames, oreKeys,
            OreSelection::enabled, OreSelection::toggle,
            FabricLoader.getInstance().getConfigDir().resolve("visionmod-ores.properties"));
        passed += verifyKeys("APPEARANCE", new String[]{"Hide entity names"}, new String[]{"hideEntityNames"},
            ignored -> VisionAppearance.hideEntityNames, ignored -> VisionAppearance.toggleEntityNames(),
            FabricLoader.getInstance().getConfigDir().resolve("visionmod/appearance.properties"));

        if (passed != 55) throw new IllegalStateException(
            "[VisionMod][SELFTEST] Expected 55 toggle+save checks, got "+passed);
        if (!"FIRST".equals(VisionWorldLyrics.previewLine("FIRST | SECOND",0)) ||
            !"SECOND".equals(VisionWorldLyrics.previewLine("FIRST | SECOND",70)) ||
            !VisionWorldLyrics.previewLine("",0).isBlank())
            throw new IllegalStateException("[VisionMod][SELFTEST] World Lyrics line playback failed");
        try (InputStream texture=VisionSelfTest.class.getResourceAsStream(
            "/assets/visionmod/textures/entity/pet_surface.png")) {
            if(texture==null)throw new IllegalStateException("Pet texture is not packaged");
            java.awt.image.BufferedImage image=javax.imageio.ImageIO.read(texture);
            if(image==null || image.getWidth()!=32 || image.getHeight()!=32)
                throw new IllegalStateException("Pet texture invalid 32x32 image");
        } catch(Exception e) {
            throw new IllegalStateException("[VisionMod][SELFTEST] Pet texture QA failed",e);
        }
        if (!"Second".equals(VisionLyricsSource.testLine(
                "[00:00.00]First","[00:01.50]Second")) ||
            !VisionHudLayout.Widget.CPS.label.equals("CPS") ||
            VisionHudLayout.Widget.values().length!=12 ||
            !new VisionHudLayout.Bounds(10,20,50,13).contains(12,24) ||
            new VisionHudLayout.Bounds(10,20,50,13).contains(61,24))
            throw new IllegalStateException("[VisionMod][SELFTEST] LRC / HUD widget regression");
        VisionLyricsSource.receiveMediaTrack("Spotify","Test Artist","Test Song",1600,true);
        VisionLyricsSource.setMediaLyrics("Test Artist","Test Song",
            "[00:00.00]TEST FIRST"+System.lineSeparator()+"[00:01.50]TEST SECOND");
        if(!"TEST SECOND".equals(VisionLyricsSource.current()))
            throw new IllegalStateException("System media timestamp sync failed");
        VisionLyricsSource.clearMediaTrack();
        System.out.println("[VisionMod][SELFTEST] PASS: System media metadata and timed lyric sync.");
        System.out.println("[VisionMod][SELFTEST] PASS: Timed LRC parsing and 12 HUD layout widget IDs.");
        System.out.println("[VisionMod][SELFTEST] PASS: World Lyrics text rotation and packaged Pet texture.");
        VisionAmbientParticles.selfTestPool();
        verifyAmbientSprite(VisionAmbientParticles.heartResource());
        verifyAmbientSprite(VisionAmbientParticles.snowResource());
        System.out.println("[VisionMod][SELFTEST] PASS: Custom Heart/Snow textures, 96-sprite cap, cleanup.");
        testTrailsAndTab();
        try (InputStream fur=VisionSelfTest.class.getResourceAsStream(
            "/assets/visionmod/textures/entity/pet_fur.png")) {
            if(fur==null)throw new IllegalStateException("New pet fur texture missing");
            var image=javax.imageio.ImageIO.read(fur);
            if(image==null || image.getWidth()!=64 || image.getHeight()!=64)
                throw new IllegalStateException("Pet fur must be readable 64x64 PNG");
        }catch(Exception e){throw new IllegalStateException("Pet texture regression",e);}
        var itemState=(VisionItemPhysicsState)(Object)
            new net.minecraft.client.renderer.entity.state.ItemEntityRenderState();
        itemState.visionmod$setMotion(true,0.0);
        if(!itemState.visionmod$isGrounded() || itemState.visionmod$speed()!=0.0)
            throw new IllegalStateException("Item render ground state broken");
        itemState.visionmod$setMotion(false,0.24);
        if(itemState.visionmod$isGrounded() || itemState.visionmod$speed()!=0.24)
            throw new IllegalStateException("Item render air state broken");
        System.out.println("[VisionMod][SELFTEST] PASS: Per-item physics Mixin state.");
        System.out.println("[VisionMod][SELFTEST] PASS: Trail gradient/HEX/RGB, TAB toggle and Pet texture.");
        System.out.println("[VisionMod][SELFTEST] PASS: "+passed+" module toggles and their settings persistence.");
    }

    private static void testTrailsAndTab() {
        if (VisionWorldEffects.parseTrailColor(" #FF80B0 ")!=0xFFFF80B0 ||
            VisionWorldEffects.parseTrailColor("255,128,176")!=0xFFFF80B0)
            throw new IllegalStateException("Trail RGB/HEX parser is broken");
        for(String invalid:new String[]{"256,0,2","#12G456","1,2","#FFFFFFEE"}) {
            try{
                VisionWorldEffects.parseTrailColor(invalid);
                throw new IllegalStateException("Accepted invalid trail color: "+invalid);
            }catch(IllegalArgumentException expected){}
        }
        int oldA=VisionWorldEffects.trailStartColor();
        int oldB=VisionWorldEffects.trailEndColor();
        int oldMode=VisionWorldEffects.trailMode();
        try {
            VisionWorldEffects.setTrailColor(0,0xFF1357A9);
            VisionWorldEffects.setTrailColor(1,0xFFB96421);
            VisionWorldEffects.setTrailMode(1);
            if(VisionWorldEffects.trailSampleColor(0f,0)!=0xFF1357A9 ||
                VisionWorldEffects.trailSampleColor(1f,0)!=0xFFB96421)
                throw new IllegalStateException("Gradient endpoints mismatch");
            Path config=FabricLoader.getInstance().getConfigDir()
                .resolve("visionmod/world-effects-2.properties");
            Properties persisted=new Properties();
            try(InputStream in=Files.newInputStream(config)){persisted.load(in);}
            if(!"#1357A9".equals(persisted.getProperty("trail.start")) ||
                !"#B96421".equals(persisted.getProperty("trail.end")))
                throw new IllegalStateException("Trail colors were not saved");
        }catch(Exception e){throw new IllegalStateException("Trail self test failed",e);}
        finally {
            VisionWorldEffects.setTrailColor(0,oldA);
            VisionWorldEffects.setTrailColor(1,oldB);
            VisionWorldEffects.setTrailMode(oldMode);
        }
        boolean initial=VisionTabHeads.enabled;
        VisionTabHeads.toggle();
        if(VisionTabHeads.enabled==initial)
            throw new IllegalStateException("Tab Heads did not toggle");
        VisionTabHeads.toggle();
        if(VisionTabHeads.enabled!=initial)
            throw new IllegalStateException("Tab Heads did not restore");
    }

    private static void verifyAmbientSprite(String resource) {
        try (InputStream input = VisionSelfTest.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing ambient texture: " + resource);
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(input);
            if (image == null || image.getWidth() != 64 || image.getHeight() != 64)
                throw new IllegalStateException("Expected 64x64 PNG: " + resource);
            if ((image.getRGB(0, 0) >>> 24) > 5 ||
                (image.getRGB(32, 32) >>> 24) < 60)
                throw new IllegalStateException("Broken sprite transparency: " + resource);
        } catch (Exception e) {
            throw new IllegalStateException("[VisionMod][SELFTEST] Ambient sprite QA failed", e);
        }
    }

    private static int verify(String group, String[] names, IntPredicate selected,
        IntConsumer toggle, String file, String keyPrefix) {
        String[] keys = new String[names.length];
        for (int i=0;i<names.length;i++) keys[i]=keyPrefix+i;
        Path p = FabricLoader.getInstance().getConfigDir().resolve("visionmod").resolve(file);
        return verifyKeys(group,names,keys,selected,toggle,p);
    }

    private static int verifyKeys(String group, String[] names, String[] keys,
        IntPredicate selected, IntConsumer toggle, Path p) {
        HashSet<String> seen = new HashSet<>();
        for (String name : names) {
            if (name == null || name.isBlank() || !seen.add(name))
                throw new IllegalStateException(group+": duplicate/empty module title");
        }
        if(keys.length!=names.length)throw new IllegalStateException(group+": mismatched property keys");
        for (int i = 0; i < names.length; i++) {
            boolean oldValue = selected.test(i);
            try {
                toggle.accept(i);
                if (selected.test(i) == oldValue) {
                    throw new IllegalStateException(group+"/"+names[i]+": toggle did not change state");
                }
                assertSaved(p, keys[i], !oldValue, group+"/"+names[i]);
            } finally {
                if (selected.test(i) != oldValue) toggle.accept(i);
            }
            if (selected.test(i) != oldValue)
                throw new IllegalStateException(group+"/"+names[i]+": restoration failed");
            assertSaved(p,keys[i],oldValue,group+"/"+names[i]);
        }
        return names.length;
    }

    private static void assertSaved(Path file,String key,boolean expected,String label) {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
        } catch (Exception e) {
            throw new IllegalStateException(label+": could not read saved config", e);
        }
        String actual = properties.getProperty(key);
        if (!Boolean.toString(expected).equals(actual))
            throw new IllegalStateException(label+": saved "+key+"="+actual+" expected="+expected);
    }
}
