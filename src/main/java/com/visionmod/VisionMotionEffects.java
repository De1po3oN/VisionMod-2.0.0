package com.visionmod;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.enchantment.Enchantments;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Original, opt-in animations and visual effects. Never changes server-side
 * combat/reach/cooldown or attempts to bypass server-authoritative spear rules.
 */
public final class VisionMotionEffects {
    public static final String[] NAMES = {
        "Swing Animation", "Item Physics", "Spear Lunge FX",
        "Spear Pierce FX", "Camera Streaks"
    };
    private static final boolean[] ENABLED = new boolean[NAMES.length];
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
        .resolve("visionmod/motion-2.properties");
    private static int streakTicks;
    private static float previousYaw;
    private static final SpearLungeTrigger LUNGE = new SpearLungeTrigger();
    private static ClientLevel previousWorld;
    private static int spearDuration=700, spearSize=100, spearColor=0xFFA89CFF;
    public static int spearDuration(){return spearDuration;}
    public static float spearSize(){return spearSize/100f;}
    public static int spearColor(){return spearColor;}
    public static void cycleSpearDuration(){spearDuration=spearDuration>=1100?400:spearDuration+350;save();}
    public static void cycleSpearSize(){spearSize=spearSize>=150?75:spearSize+25;save();}
    public static void setSpearColor(int color){spearColor=0xFF000000|(color&0xFFFFFF);save();}
    public static void setSpearSettings(int duration,int percent,int color){
        spearDuration=Math.clamp(duration,300,1500);spearSize=Math.clamp(percent,50,175);
        spearColor=0xFF000000|(color&0xFFFFFF);save();
    }

    private VisionMotionEffects() {}
    public static int count() { return NAMES.length; }
    public static boolean enabled(int id) {
        return id >= 0 && id < ENABLED.length && ENABLED[id] && VisionModClient.enabled;
    }
    public static boolean selected(int id) {
        return id >= 0 && id < ENABLED.length && ENABLED[id];
    }
    public static void toggle(int id) {
        if (id < 0 || id >= ENABLED.length) return;
        ENABLED[id] = !ENABLED[id];
        save();
    }

    public static void init() {
        load();
        ClientTickEvents.END_CLIENT_TICK.register(VisionMotionEffects::tick);
        VisionSpearEffects.init();
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("visionmod", "camera_streaks"),
            (g, tracker) -> render(g));
    }
    public static void beforeLunge() {
        Minecraft mc=Minecraft.getInstance();
        LUNGE.reset();
        if(enabled(2)&&mc.player!=null&&mc.player.getMainHandItem().has(DataComponents.PIERCING_WEAPON)){
            boolean enchanted=mc.player.getMainHandItem().getEnchantments().entrySet().stream()
                .anyMatch(entry->entry.getKey().is(Enchantments.LUNGE)&&entry.getIntValue()>0);
            if(enchanted){
                Vec3 view=mc.player.getLookAngle();
                LUNGE.arm(mc.player.getX(),mc.player.getZ(),view.x,view.z,
                    Math.hypot(mc.player.getX()-mc.player.xo,mc.player.getZ()-mc.player.zo));
            }
        }
    }
    public static void afterLunge() { observeLunge(Minecraft.getInstance()); }
    private static void observeLunge(Minecraft mc) {
        boolean eligible=enabled(2)&&mc.player!=null&&mc.level!=null&&
            mc.player.getMainHandItem().has(DataComponents.PIERCING_WEAPON);
        var impulse=LUNGE.sample(eligible?mc.player.getX():0,eligible?mc.player.getZ():0,eligible);
        if(impulse!=null) {
            VisionSpearEffects.emit(mc.player.position().add(0,.9,0),new Vec3(impulse.x(),0,impulse.z()),null);
        }
    }
    /** Server damage attribution also covers the spear's held-use charge attack. */
    public static void spearDamage(ClientboundDamageEventPacket packet) {
        Minecraft mc=Minecraft.getInstance();
        if(!enabled(3)||mc.player==null||mc.level==null||packet.sourceCauseId()!=mc.player.getId()||
            !packet.sourceType().is(DamageTypes.SPEAR))return;
        var stack=mc.player.isUsingItem()&&mc.player.getUseItem().has(DataComponents.KINETIC_WEAPON)?
            mc.player.getUseItem():mc.player.getMainHandItem();
        if(!stack.has(DataComponents.PIERCING_WEAPON))return;
        var target=mc.level.getEntity(packet.entityId());
        if(target==null||target==mc.player||target.isInvisible()||target.distanceToSqr(mc.player)>100)return;
        Vec3 contact=target.getBoundingBox().getCenter();
        VisionSpearEffects.emit(contact,contact.subtract(mc.player.getEyePosition()),target);
    }
    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null || !VisionModClient.enabled) {
            streakTicks=0;LUNGE.reset();VisionSpearEffects.reset();previousWorld=null;
            if(mc.player!=null)previousYaw=mc.player.getYRot();
            return;
        }
        if(previousWorld!=mc.level){LUNGE.reset();VisionSpearEffects.reset();previousWorld=mc.level;previousYaw=mc.player.getYRot();}
        // Vanilla can defer the lunge's movement until the following client tick.
        observeLunge(mc);
        if (streakTicks > 0) streakTicks--;
        if (enabled(4)) {
            float difference = Math.abs(Mth.wrapDegrees(mc.player.getYRot() - previousYaw));
            if (difference > 9) streakTicks = Math.min(7, (int)(difference / 5) + 1);
        }
        previousYaw = mc.player.getYRot();

    }
    private static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled(4) || mc.screen != null || mc.player == null || streakTicks <= 0) return;
        int w=g.guiWidth(),h=g.guiHeight();
        float strength=streakTicks/7f;
        for(int i=0;i<9;i++){
            int length=8+(int)(i*2.5),yy=h/2-62+i*15;
            int color=VisionGeometry.withAlpha(VisionMenuTheme.PURPLE,(int)(45*strength*(1-Math.abs(i-4)/6f)));
            for(int n=0;n<4;n++){
                int xx=3+n*5;int fade=VisionGeometry.withAlpha(color,(int)(((color>>>24)&255)*(1-n*.22)));
                g.fill(xx,yy+n*2,xx+length,yy+n*2+1,fade);
                g.fill(w-xx-length,yy+n*2,w-xx,yy+n*2+1,fade);
            }
        }
    }
    private static void load() {
        if (!Files.isRegularFile(FILE)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            p.load(in);
            for (int i = 0; i < ENABLED.length; i++)
                ENABLED[i] = Boolean.parseBoolean(p.getProperty("motion." + i,"false"));
            spearDuration=Math.clamp(Integer.parseInt(p.getProperty("spearDuration","700")),300,1500);
            spearSize=Math.clamp(Integer.parseInt(p.getProperty("spearSize","100")),50,175);
            spearColor=VisionWorldEffects.parseTrailColor(p.getProperty("spearColor","#A89CFF"));
        } catch (Exception e) {
            System.err.println("[VisionMod] Motion options load failed: " + e);
        }
    }
    private static void save() {
        Properties p = new Properties();
        for (int i = 0; i < ENABLED.length; i++)
            p.setProperty("motion." + i, Boolean.toString(ENABLED[i]));
        p.setProperty("spearDuration",Integer.toString(spearDuration));
        p.setProperty("spearSize",Integer.toString(spearSize));
        p.setProperty("spearColor",VisionWorldEffects.trailColorHex(spearColor));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                p.store(out, "Vision Mod original client motion FX");
            }
        } catch (Exception e) {
            System.err.println("[VisionMod] Motion options save failed: " + e);
        }
    }
}
