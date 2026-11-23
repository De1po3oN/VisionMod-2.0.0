package com.visionmod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Properties;

/**
 * Bounded client-only world visuals. No copied proprietary assets or shaders.
 *
 * Continuous gradient ribbons, expanding radial ground circles, a translucent
 * cone hat, and pulsing target outline replace the former sparse 3D boxes.
 * All effects can be disabled without retaining GPU resources or network data.
 */
public final class VisionWorldEffects {
    public static final String[] NAMES = {
        "Target ESP", "Trails", "Jump Circles", "China Hat"
    };
    private static final boolean[] ENABLED = new boolean[NAMES.length];
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir()
        .resolve("visionmod/world-effects-2.properties");
    private static final int SEGMENTS=36;
    private static final int MAX_TRAIL=25;
    private static final long TRAIL_LIFETIME=28;
    // Preserved in world-effects-2.properties, independent of menu/GUI state.
    private static int trailStartColor = 0xFF67E8F9;
    private static int trailEndColor = 0xFFB66DFF;
    /** 0 solid, 1 two-color gradient, 2 smooth cycling rainbow. */
    private static int trailMode = 1;
    private static float trailWidth = 0.115f;
    private static int trailOpacity = 160;

    public static int trailStartColor() { return trailStartColor; }
    public static int trailEndColor() { return trailEndColor; }
    public static int trailMode() { return trailMode; }
    public static String trailModeLabel() {
        return switch(trailMode) { case 0 -> "SOLID"; case 2 -> "RAINBOW"; default -> "GRADIENT"; };
    }
    public static float trailWidth() { return trailWidth; }
    public static int trailOpacity() { return trailOpacity; }
    public static String trailColorHex(int value) {
        return String.format(java.util.Locale.ROOT, "#%06X", value & 0xFFFFFF);
    }
    /** Strictly accept #RRGGBB, RRGGBB, or decimal R,G,B. */
    public static int parseTrailColor(String value) {
        if (value == null) throw new IllegalArgumentException("No color");
        String input = value.trim();
        if (input.matches("(?i)#?[0-9a-f]{6}"))
            return 0xFF000000 | Integer.parseInt(input.replace("#", ""), 16);
        String[] channels = input.split(",",-1);
        if (channels.length != 3) throw new IllegalArgumentException("Use #RRGGBB or R,G,B");
        int color = 0xFF000000;
        for (int i=0;i<3;i++) {
            int v;
            try { v = Integer.parseInt(channels[i].trim()); }
            catch(NumberFormatException e) { throw new IllegalArgumentException("RGB channels must be numbers"); }
            if (v < 0 || v > 255) throw new IllegalArgumentException("RGB channels must be 0-255");
            color |= v << (16 - 8*i);
        }
        return color;
    }
    public static void setTrailColor(int index,int color) {
        if (index==0) trailStartColor = 0xFF000000 | (color & 0xFFFFFF);
        else if (index==1) trailEndColor = 0xFF000000 | (color & 0xFFFFFF);
        else return;
        save();
    }
    public static void setTrailMode(int mode) {
        trailMode = Math.max(0,Math.min(2,mode)); save();
    }
    public static void cycleTrailMode() { setTrailMode((trailMode+1)%3); }
    public static void setTrailWidth(float value){if(Float.isFinite(value)){trailWidth=Math.clamp(value,.04f,.3f);save();}}
    public static void setTrailOpacity(int value){trailOpacity=Math.clamp(value,30,250);save();}
    public static void cycleTrailWidth() {
        trailWidth = trailWidth < .09f ? .115f : trailWidth < .15f ? .22f : .065f;
        save();
    }
    public static void cycleTrailOpacity() {
        trailOpacity = trailOpacity <= 110 ? 160 : trailOpacity <= 160 ? 220 : 90;
        save();
    }
    public static int trailSampleColor(float along,long ticks) {
        float t=Math.max(0f,Math.min(1f,along));
        return switch(trailMode) {
            case 0 -> trailStartColor;
            case 2 -> 0xFF000000 | (java.awt.Color.HSBtoRGB(
                (float)((t*.85 + ticks*.009) % 1.0),.83f,1f) & 0xFFFFFF);
            default -> VisionGeometry.blendRgb(trailStartColor,trailEndColor,t);
        };
    }
    private record TrailPoint(Vec3 position,long time) {}
    private static final Deque<TrailPoint> TRAIL = new ArrayDeque<>(MAX_TRAIL);
    private static ClientLevel previousWorld;
    private static Vec3 landingPos;
    private static int landingAge;
    private static boolean grounded;
    private static int airborneTicks;
    private static int tick;
    private static boolean renderFaulted;
    private static long renderedFrames;
    static long renderedFrames(){return renderedFrames;}
    static boolean landingActive(){return landingAge>0;}
    public static boolean suspended(){return renderFaulted;}

    private VisionWorldEffects() {}
    public static int count() { return NAMES.length; }
    public static boolean enabled(int id) {
        return id>=0 && id<ENABLED.length && ENABLED[id];
    }
    public static void toggle(int id) {
        if (id<0 || id>=ENABLED.length) return;
        ENABLED[id]=!ENABLED[id];
        renderFaulted=false;
        if (id==1 && !ENABLED[id]) TRAIL.clear();
        if (id==2 && !ENABLED[id]) landingAge=0;
        save();
    }

    public static void init() {
        load();
        ClientTickEvents.END_CLIENT_TICK.register(VisionWorldEffects::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(ctx->{
            Minecraft mc=Minecraft.getInstance();
            if(renderFaulted||!VisionModClient.enabled||mc.player==null||mc.level==null||ctx.consumers()==null||ctx.matrices()==null)return;
            if(!ENABLED[0]&&!ENABLED[1]&&!ENABLED[2]&&!ENABLED[3])return;
            Vec3 cam=mc.gameRenderer.getMainCamera().position();PoseStack pose=ctx.matrices();
            float partial=mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            double time=mc.level.getGameTime()+partial;
            int steps=VisionAppearance.segments();
            LivingEntity target=ENABLED[0]?VisionCombatTracker.crosshairTarget(mc):null;
            try{
                // Finish every translucent vertex before acquiring the line consumer.
                VertexConsumer fill=ctx.consumers().getBuffer(RenderTypes.entityTranslucent(VisionGeometry.WHITE_TEXTURE));
                if(ENABLED[1]&&TRAIL.size()>=2){
                    TrailPoint last=null;int i=0,size=TRAIL.size();
                    for(TrailPoint point:TRAIL){
                        if(last!=null){
                            Vec3 aa=last.position(),bb=point.position();
                            double dx=bb.x-aa.x,dz=bb.z-aa.z,length=Math.sqrt(dx*dx+dz*dz);
                            if(length>.025&&length<2.5&&Math.abs(bb.y-aa.y)<1.7){
                                float fade=EffectMath.fade((float)((time-point.time())/TRAIL_LIFETIME));
                                double width=trailWidth*(.3+.7*fade),px=-dz/length*width,pz=dx/length*width;
                                int colorA=trailSampleColor((i-1f)/(size-1),(long)time),colorB=trailSampleColor(i/(float)(size-1),(long)time);
                                for(int strip=0;strip<3;strip++){
                                    double left=strip==0?-1:strip==1?-.26:.26,right=strip==0?-.26:strip==1?.26:1;
                                    int alphaL=strip==0?0:(int)(trailOpacity*fade),alphaR=strip==2?0:(int)(trailOpacity*fade);
                                    ribbon(fill,pose,aa.subtract(cam),bb.subtract(cam),px,pz,left,right,colorA,colorB,alphaL,alphaR);
                                }
                            }
                        }
                        last=point;i++;
                    }
                }
                if(ENABLED[2]&&landingAge>0&&landingPos!=null){
                    float progress=EffectMath.unit((20-landingAge+partial)/20f);
                    for(int layer=0;layer<(VisionAppearance.detail==0?2:3);layer++){
                        float local=EffectMath.unit(progress-layer*.10f);
                        if(local<=0)continue;
                        double radius=EffectMath.ringRadius(local,1.05f-layer*.13f);
                        int color=EffectMath.blend(VisionMenuTheme.CYAN,VisionMenuTheme.PURPLE,layer*.45f);
                        int alpha=(int)(165*EffectMath.fade(local));
                        VisionGeometry.band(fill,pose,landingPos.x-cam.x,landingPos.y-cam.y+.045+layer*.006,
                            landingPos.z-cam.z,radius,.055+local*.03,VisionGeometry.withAlpha(color,alpha),VisionGeometry.withAlpha(color,0),steps);
                    }
                }
                if(ENABLED[3]&&mc.options.getCameraType()!=CameraType.FIRST_PERSON&&cam.distanceToSqr(mc.player.getEyePosition(partial))>=.85*.85){
                    Vec3 pos=mc.player.getPosition(partial).subtract(cam);
                    double yy=pos.y+mc.player.getBbHeight()+.08;
                    for(int i=0;i<steps;i++){
                        double aa=i*Math.PI*2/steps,bb=(i+1)*Math.PI*2/steps;
                        int color=VisionGeometry.withAlpha(EffectMath.blend(VisionMenuTheme.CYAN,VisionMenuTheme.PURPLE,(float)(.5+.5*Math.sin(aa))),160);
                        float ix=(float)(pos.x+Math.cos(aa)*.012),iz=(float)(pos.z+Math.sin(aa)*.012);
                        float jx=(float)(pos.x+Math.cos(bb)*.012),jz=(float)(pos.z+Math.sin(bb)*.012);
                        float ax=(float)(pos.x+Math.cos(aa)*.56),az=(float)(pos.z+Math.sin(aa)*.56);
                        float bx=(float)(pos.x+Math.cos(bb)*.56),bz=(float)(pos.z+Math.sin(bb)*.56);
                        VisionGeometry.quad(fill,pose,ix,(float)yy+.34f,iz,ax,(float)yy,az,bx,(float)yy,bz,jx,(float)yy+.34f,jz,color,0,1,0);
                        VisionGeometry.quad(fill,pose,jx,(float)yy+.34f,jz,bx,(float)yy,bz,ax,(float)yy,az,ix,(float)yy+.34f,iz,color,0,-1,0);
                    }
                    VisionGeometry.band(fill,pose,pos.x,yy-.006,pos.z,.585,.045,VisionGeometry.withAlpha(VisionMenuTheme.CYAN,170),VisionGeometry.withAlpha(VisionMenuTheme.PURPLE,0),steps);
                }
                if(target!=null&&target.distanceToSqr(mc.player)<24*24){
                    Vec3 tp=target.getPosition(partial).subtract(cam);
                    float pulse=VisionAppearance.animations?(float)Math.sin(time*.12)*.025f:0;
                    int c=VisionTools2.hitColor();
                    VisionGeometry.band(fill,pose,tp.x,tp.y+.025,tp.z,target.getBbWidth()*.65+.18+pulse,.085,
                        VisionGeometry.withAlpha(c,110),VisionGeometry.withAlpha(c,0),steps);
                    // No translucent writes after this point.
                    VertexConsumer lines=ctx.consumers().getBuffer(RenderTypes.lines());
                    AABB box=target.getBoundingBox().move(target.getPosition(partial).subtract(target.position())).inflate(.045);
                    corners(lines,pose,box.move(-cam.x,-cam.y,-cam.z),VisionGeometry.withAlpha(c,210));
                }
                renderedFrames++;
            }catch(RuntimeException ex){renderFaulted=true;System.err.println("[VisionMod][WORLD FX] Renderer suspended: "+ex);}
        });
    }
    private static void ribbon(VertexConsumer out,PoseStack pose,Vec3 a,Vec3 b,double px,double pz,double left,double right,int ca,int cb,int al,int ar){
        for(int face=0;face<2;face++){
            double l=face==0?left:right,r=face==0?right:left;
            int alphaL=face==0?al:ar,alphaR=face==0?ar:al;
            VisionGeometry.vertex(out,pose,(float)(a.x+px*l),(float)a.y+.09f,(float)(a.z+pz*l),0,0,VisionGeometry.withAlpha(ca,alphaL),0,1,0);
            VisionGeometry.vertex(out,pose,(float)(b.x+px*l),(float)b.y+.09f,(float)(b.z+pz*l),1,0,VisionGeometry.withAlpha(cb,alphaL),0,1,0);
            VisionGeometry.vertex(out,pose,(float)(b.x+px*r),(float)b.y+.09f,(float)(b.z+pz*r),1,1,VisionGeometry.withAlpha(cb,alphaR),0,1,0);
            VisionGeometry.vertex(out,pose,(float)(a.x+px*r),(float)a.y+.09f,(float)(a.z+pz*r),0,1,VisionGeometry.withAlpha(ca,alphaR),0,1,0);
        }
    }
    private static void corners(VertexConsumer line,PoseStack pose,AABB b,int color){
        double len=Math.min(.22,Math.min(b.getXsize(),b.getZsize())*.35);
        for(int sx=0;sx<2;sx++)for(int sy=0;sy<2;sy++)for(int sz=0;sz<2;sz++){
            double x=sx==0?b.minX:b.maxX,y=sy==0?b.minY:b.maxY,z=sz==0?b.minZ:b.maxZ;
            VisionGeometry.line(line,pose,x,y,z,x+(sx==0?len:-len),y,z,color,1.6f);
            VisionGeometry.line(line,pose,x,y,z,x,y+(sy==0?len:-len),z,color,1.6f);
            VisionGeometry.line(line,pose,x,y,z,x,y,z+(sz==0?len:-len),color,1.6f);
        }
    }

    private static void tick(Minecraft mc) {
        if(mc.level==null || mc.player==null) {
            reset(null);return;
        }
        if(previousWorld!=mc.level) reset(mc.level);
        if(!VisionModClient.enabled) {
            TRAIL.clear();landingAge=airborneTicks=0;grounded=mc.player.onGround();return;
        }
        long now=mc.level.getGameTime();
        if(ENABLED[1]) {
            while(!TRAIL.isEmpty() && (now-TRAIL.peekFirst().time())>TRAIL_LIFETIME)
                TRAIL.removeFirst();
            if(++tick>=2) {
                tick=0;
                Vec3 p=mc.player.position();
                if (!TRAIL.isEmpty() && TRAIL.peekLast().position().distanceToSqr(p)>7.0)
                    TRAIL.clear();
                if(TRAIL.isEmpty() || TRAIL.peekLast().position().distanceToSqr(p)>.012) {
                    TRAIL.addLast(new TrailPoint(p,now));
                    while(TRAIL.size()>MAX_TRAIL) TRAIL.removeFirst();
                }
            }
        } else {
            tick=0;
            TRAIL.clear();
        }
        boolean onGround=mc.player.onGround();
        if(!onGround)airborneTicks=Math.min(100,airborneTicks+1);
        if(ENABLED[2]&&onGround&&!grounded&&airborneTicks>=2&&!mc.player.isPassenger()){
            landingPos=mc.player.position();landingAge=20;
        }
        if(onGround)airborneTicks=0;
        grounded=onGround;
        if(landingAge>0) landingAge--;
    }

    private static void reset(ClientLevel level) {
        previousWorld=level;
        renderFaulted=false;
        TRAIL.clear();landingPos=null;
        landingAge=tick=airborneTicks=0;grounded=false;
    }

    private static void load() {
        if (!Files.isRegularFile(CONFIG)) return;
        Properties p=new Properties();
        try(InputStream in=Files.newInputStream(CONFIG)) {
            p.load(in);
            for(int i=0;i<ENABLED.length;i++)
                ENABLED[i]=Boolean.parseBoolean(p.getProperty("effect."+i,"false"));
            try { trailStartColor=parseTrailColor(p.getProperty("trail.start", "#67E8F9")); }
            catch(IllegalArgumentException e){trailStartColor=0xFF67E8F9;}
            try { trailEndColor=parseTrailColor(p.getProperty("trail.end", "#B66DFF")); }
            catch(IllegalArgumentException e){trailEndColor=0xFFB66DFF;}
            try { trailMode=Math.max(0,Math.min(2,Integer.parseInt(p.getProperty("trail.mode","1")))); }
            catch(NumberFormatException e){trailMode=1;}
            try { trailWidth=Math.max(.04f,Math.min(.30f,Float.parseFloat(p.getProperty("trail.width",".115")))); }
            catch(NumberFormatException e){trailWidth=.115f;}
            if(!Float.isFinite(trailWidth))trailWidth=.115f;
            try { trailOpacity=Math.max(30,Math.min(250,Integer.parseInt(p.getProperty("trail.opacity","160")))); }
            catch(NumberFormatException e){trailOpacity=160;}
        }catch(Exception e){System.err.println("[VisionMod] World FX load: "+e);}
    }
    private static void save() {
        Properties p=new Properties();
        for(int i=0;i<ENABLED.length;i++)
            p.setProperty("effect."+i,Boolean.toString(ENABLED[i]));
        p.setProperty("trail.start",trailColorHex(trailStartColor));
        p.setProperty("trail.end",trailColorHex(trailEndColor));
        p.setProperty("trail.mode",Integer.toString(trailMode));
        p.setProperty("trail.width",Float.toString(trailWidth));
        p.setProperty("trail.opacity",Integer.toString(trailOpacity));
        try {
            Files.createDirectories(CONFIG.getParent());
            try(OutputStream out=Files.newOutputStream(CONFIG)) {
                p.store(out,"VisionMod original low-cost smooth world FX");
            }
        }catch(Exception e){System.err.println("[VisionMod] World FX save: "+e);}
    }
}
