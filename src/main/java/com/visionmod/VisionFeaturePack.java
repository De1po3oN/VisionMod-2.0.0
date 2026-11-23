package com.visionmod;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/**
 * Vision 2.0's lightweight ORIGINAL HUD features.
 *
 * Not a port of the proprietary Fading Light classes or their resources.
 * All options are off by default. Nearby-entity scans happen at most every ten
 * client ticks and no persistent per-entity allocations are retained.
 */
public final class VisionFeaturePack {
    public static final String[] NAMES = {
        "Aspect Bars", "Armor Colorful", "Target Health", "Hit Range",
        "TNT Indicator", "Arrow Names", "Hit Pulse", "Blood Hit"
    };
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir()
        .resolve("visionmod/features-2.properties");
    private static final boolean[] ENABLED = new boolean[NAMES.length];
    private static final EquipmentSlot[] ARMOR = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final String[] ARMOR_NAMES = {"Helmet", "Chest", "Legs", "Boots"};
    private static int ticks;
    private static int nearestTnt = -1;
    private static int nearestArrow = -1;
    private static int hitPulse;
    private static int lastHitSequence;
    private static LivingEntity viewed;
    private static int targetGrace,viewedId=-1;
    private static float shownHealth,damageHealth;
    private static long lastFrame;

    private VisionFeaturePack() { }

    public static int count() { return NAMES.length; }
    public static boolean enabled(int i) { return i >= 0 && i < ENABLED.length && ENABLED[i]; }
    public static void toggle(int i) {
        if (i < 0 || i >= ENABLED.length) return;
        ENABLED[i] = !ENABLED[i];
        save();
    }
    public static void init() {
        load();
        ClientTickEvents.END_CLIENT_TICK.register(VisionFeaturePack::tick);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("visionmod", "feature_pack"),
            (g, tracker) -> render(g));
    }

    static LivingEntity viewedTarget() {
        return viewed != null && viewed.isAlive() && !viewed.isInvisible() ? viewed : null;
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            viewed=null;targetGrace=0;viewedId=-1;
            ticks = 0;
            nearestTnt = -1;
            nearestArrow = -1;
            lastHitSequence = VisionCombatTracker.hitSequence();
            hitPulse = 0;
            return;
        }
        if (!VisionModClient.enabled) {
            viewed=null;targetGrace=0;nearestTnt=nearestArrow=-1;hitPulse=0;
            lastHitSequence=VisionCombatTracker.hitSequence();return;
        }
        LivingEntity target = VisionCombatTracker.crosshairTarget(mc);
        if (target != null) {
            viewed = target;
            targetGrace = 12;
        } else if (viewedTarget() != null && targetGrace > 0) {
            targetGrace--;
        } else {
            viewed = null;
        }
        if (hitPulse > 0) hitPulse--;
        // Fire exactly once per client-observed hurt event after our own attack,
        // even when crosshair has moved away by the time the target reacts.
        int sequence = VisionCombatTracker.hitSequence();
        if (sequence != lastHitSequence) {
            lastHitSequence = sequence;
            if (ENABLED[6] || ENABLED[7]) hitPulse = 8;
        }
        if ((!ENABLED[6] && !ENABLED[7]) || !VisionCombatTracker.hitVisible()) hitPulse = 0;

        // Avoid scanning the player-dense world every rendered frame.
        if (++ticks < 10) return;
        ticks = 0;
        nearestTnt = -1;
        nearestArrow = -1;
        if (!ENABLED[4] && !ENABLED[5]) return;
        double tntDist = Double.MAX_VALUE, arrowDist = Double.MAX_VALUE;
        List<Entity> nearby = mc.level.getEntitiesOfClass(Entity.class,
            mc.player.getBoundingBox().inflate(32.0),
            e -> e.isAlive() && !e.isInvisible() && (e.getType() == EntityType.TNT ||
                e.getType() == EntityType.ARROW || e.getType() == EntityType.SPECTRAL_ARROW));
        for (Entity entity : nearby) {
            double d = entity.distanceToSqr(mc.player);
            if (ENABLED[4] && entity.getType() == EntityType.TNT && d < tntDist) {
                tntDist = d;
                nearestTnt = entity.getId();
            }
            if (ENABLED[5] && (entity.getType() == EntityType.ARROW ||
                entity.getType() == EntityType.SPECTRAL_ARROW) && d < arrowDist) {
                arrowDist = d;
                nearestArrow = entity.getId();
            }
        }
    }

    private static void render(GuiGraphics g) {
        Minecraft mc=Minecraft.getInstance();
        if(!VisionModClient.enabled||mc.level==null||mc.player==null||!VisionHudLayout.hudVisible(mc))return;
        int w=g.guiWidth(),h=g.guiHeight();
        long now=System.nanoTime();float seconds=lastFrame==0?.016f:(now-lastFrame)/1_000_000_000f;lastFrame=now;
        if(ENABLED[0]){
            int bar=Math.max(0,(int)Math.round((h-w/2.39)/2));
            if(bar>0){g.fill(0,0,w,bar,0xF0000000);g.fill(0,h-bar,w,h,0xF0000000);
                g.fill(0,bar-1,w,bar,0xFF242536);g.fill(0,h-bar,w,h-bar+1,0xFF242536);}
        }
        if(ENABLED[1]){
            var b=VisionHudLayout.place(VisionHudLayout.Widget.ARMOR_COLOR,w-156,18,144,110);
            VisionHudTheme.panel(g,b.x(),b.y(),b.width(),b.height(),VisionMenuTheme.CYAN);
            g.drawString(mc.font,"БРОНЯ",b.x()+10,b.y()+8,VisionMenuTheme.MUTED);
            for(int i=0;i<4;i++){
                ItemStack item=mc.player.getItemBySlot(ARMOR[i]);int y=b.y()+23+i*20;
                if(item.isEmpty()){g.drawString(mc.font,"—",b.x()+12,y+4,0xFF666C83);continue;}
                g.renderItem(item,b.x()+8,y);float value=item.isDamageableItem()?Math.max(0,1-item.getDamageValue()/(float)Math.max(1,item.getMaxDamage())):1;
                int color=EffectMath.blend(0xFFFF758F,0xFF8CE8C7,value);
                g.drawString(mc.font,Math.round(value*100)+"%",b.x()+29,y+1,color);
                VisionHudTheme.meter(g,b.x()+29,y+12,Math.max(10,b.width()-40),3,value,color);
            }
        }
        LivingEntity selected=viewedTarget();
        if(selected!=null&&(ENABLED[2]||ENABLED[3])){
            var b=VisionHudLayout.place(VisionHudLayout.Widget.TARGET,w/2-91,h/2+36,182,60);
            int x=b.x(),y=b.y(),ww=b.width();
            float hp=Math.max(0,selected.getHealth()),max=Math.max(1,selected.getMaxHealth());
            if(viewedId!=selected.getId()){viewedId=selected.getId();shownHealth=damageHealth=hp;}
            shownHealth=VisionAppearance.animations?EffectMath.smooth(shownHealth,hp,seconds):hp;
            damageHealth=VisionAppearance.animations?EffectMath.smooth(damageHealth,hp,seconds*.3f):hp;
            VisionHudTheme.panel(g,x,y,ww,b.height(),VisionTools2.hitColor());
            VisionHudTheme.round(g,x+9,y+11,28,29,6,VisionGeometry.withAlpha(VisionTools2.hitColor(),45));
            String name=selected.getName().getString();String initial=name.isEmpty()?"?":name.substring(0,1).toUpperCase(java.util.Locale.ROOT);
            g.drawCenteredString(mc.font,initial,x+23,y+21,VisionMenuTheme.WHITE);
            VisionHudTheme.text(g,mc.font,name,x+46,y+10,ww-55,VisionMenuTheme.WHITE);
            if(ENABLED[2]){
                int mw=Math.max(12,ww-56);
                VisionHudTheme.meter(g,x+46,y+27,mw,5,damageHealth/max,0xFF977AA2);
                VisionHudTheme.round(g,x+46,y+27,Math.round(mw*EffectMath.unit(shownHealth/max)),5,2,0xFF88E4C0);
                g.drawString(mc.font,Math.round(hp*10)/10f+" / "+Math.round(max)+" HP",x+46,y+40,0xFFB4DDCF);
            }
            if(ENABLED[3]){
                String range=Math.round(Math.sqrt(selected.distanceToSqr(mc.player))*10)/10.0+" m";
                g.drawString(mc.font,range,x+ww-mc.font.width(range)-10,y+40,0xFFCCB5FF);
            }
        }
        if(ENABLED[4]&&nearestTnt>=0){
            Entity e=mc.level.getEntity(nearestTnt);
            if(e instanceof PrimedTnt tnt&&tnt.isAlive()&&!tnt.isInvisible()){
                float fuse=Math.max(0,tnt.getFuse()-mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
                int color=fuse<20?0xFFFF788A:0xFFFFC884;
                var b=VisionHudLayout.place(VisionHudLayout.Widget.TNT,w/2-65,20,130,45);
                VisionHudTheme.panel(g,b.x(),b.y(),b.width(),b.height(),color);
                VisionHudTheme.ring(g,b.x()+21,b.y()+22,13,fuse/80,color);
                g.drawCenteredString(mc.font,String.format(java.util.Locale.ROOT,"%.1f",fuse/20),b.x()+22,b.y()+19,color);
                g.drawString(mc.font,"TNT",b.x()+44,b.y()+10,color);
                g.drawString(mc.font,Math.round(Math.sqrt(e.distanceToSqr(mc.player))*10)/10.0+" m",b.x()+44,b.y()+26,VisionMenuTheme.MUTED);
            }
        }
        if(ENABLED[5]&&nearestArrow>=0){
            Entity e=mc.level.getEntity(nearestArrow);
            if(e!=null&&e.isAlive()&&!e.isInvisible()){
                var camera=mc.gameRenderer.getMainCamera();Vec3 forward=Vec3.directionFromRotation(camera.xRot(),camera.yRot());
                Vec3 right=forward.cross(new Vec3(0,1,0));
                if(camera.isInitialized()&&right.lengthSqr()>1e-5){
                    right=right.normalize();Vec3 up=right.cross(forward).normalize();
                    Vec3 toward=e.getPosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)).add(0,.45,0).subtract(camera.position());
                    double depth=toward.dot(forward);
                    if(depth>.3&&depth<32){
                        double factor=h/(2*Math.tan(Math.toRadians(mc.options.fov().get()*.5)));
                        int sx=(int)(w*.5+toward.dot(right)*factor/depth),sy=(int)(h*.5-toward.dot(up)*factor/depth);
                        String label="➜ "+e.getName().getString();int bw=mc.font.width(label)+18;
                        if(sx>bw/2&&sx<w-bw/2&&sy>15&&sy<h-25){
                            var b=VisionHudLayout.place(VisionHudLayout.Widget.ARROW,sx-bw/2,sy-20,bw,20);
                            VisionHudTheme.panel(g,b.x(),b.y(),b.width(),b.height(),VisionMenuTheme.CYAN);
                            VisionHudTheme.text(g,mc.font,label,b.x()+9,b.y()+6,b.width()-18,0xFFD4CEFF);
                        }
                    }
                }
            }
        }
        if(hitPulse>0&&VisionCombatTracker.hitVisible()&&(ENABLED[6]||ENABLED[7])){
            float progress=EffectMath.unit((8-hitPulse+mc.getDeltaTracker().getGameTimeDeltaPartialTick(false))/8f);
            int alpha=(int)(220*EffectMath.fade(progress));
            if(ENABLED[6]){
                int radius=6+(int)(EffectMath.ease(progress)*10),color=VisionGeometry.withAlpha(VisionTools2.hitColor(),alpha);
                for(int sx=-1;sx<=1;sx+=2)for(int sy=-1;sy<=1;sy+=2)for(int k=0;k<5;k++){
                    int xx=w/2+sx*(radius+k),yy=h/2+sy*(radius+k);
                    g.fill(xx,yy,xx+2,yy+2,color);
                }
            }
            if(ENABLED[7])for(int layer=0;layer<10;layer++){
                int edge=layer*4,opacity=(int)((10-layer)*2.1*EffectMath.fade(progress));
                int c=VisionGeometry.withAlpha(0xFFD83870,opacity);
                g.fill(edge,edge,w-edge,edge+4,c);g.fill(edge,h-edge-4,w-edge,h-edge,c);
                g.fill(edge,edge+4,edge+4,h-edge-4,c);g.fill(w-edge-4,edge+4,w-edge,h-edge-4,c);
            }
        }
    }

    private static void load() {
        if (!Files.isRegularFile(CONFIG)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(CONFIG)) {
            p.load(in);
            for (int i = 0; i < ENABLED.length; i++)
                ENABLED[i] = Boolean.parseBoolean(p.getProperty("feature." + i, "false"));
        } catch (Exception ex) {
            System.err.println("[VisionMod] FeaturePack settings read failed: " + ex);
        }
    }
    private static void save() {
        Properties p = new Properties();
        for (int i = 0; i < ENABLED.length; i++)
            p.setProperty("feature." + i, Boolean.toString(ENABLED[i]));
        try {
            Files.createDirectories(CONFIG.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG)) {
                p.store(out, "Vision Mod 2.0: independent HUD features (all optional)");
            }
        } catch (Exception ex) {
            System.err.println("[VisionMod] FeaturePack settings save failed: " + ex);
        }
    }
}
