package com.visionmod;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Chat-open HUD editor for every moveable Vision HUD text/panel.
 * Does not capture input outside widget bounds or interfere with chat entry.
 * Saved positions are normalized to the current GUI scale/resolution.
 *
 * World-space overlays (player hearts, world ESP, pet, chat, vanilla reticle)
 * are deliberately not movable screen HUD panels.
 */
public final class VisionHudLayout {
    public enum Widget {
        CPS("CPS"),
        FOOD("Food & Saturation"),
        ARMOR("Armor HUD"),
        SHULKER("Shulker HUD"),
        ARMOR_COLOR("Armor Colorful"),
        TARGET("Target Health / Hit Range"),
        TNT("TNT Indicator"),
        ARROW("Arrow Names"),
        LYRICS("World Lyrics"),
        KILL("Kill Effect"),
        TAB("TAB Heads"),
        SOCIAL(VisionExtendedFeatures.FRIEND_ZONE);

        public final String label;
        private boolean moved;
        private float xRatio,yRatio;
        private int x,y,w,h;
        Widget(String label) { this.label=label; }
    }
    public record Bounds(int x,int y,int width,int height) {
        public boolean contains(double px,double py) {
            return px>=x && py>=y && px<x+width && py<y+height;
        }
    }
    private static final Path CONFIG=FabricLoader.getInstance().getConfigDir()
        .resolve("visionmod/hud-layout.properties");
    private static Widget dragging;
    private static double gripX,gripY;
    private static boolean initiated;
    private static final java.util.EnumMap<Widget,HudPacking.Rect> occupied=new java.util.EnumMap<>(Widget.class);
    private VisionHudLayout(){}

    public static void init() {
        load();
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("visionmod","hud_layout_frame"),(g,t)->{
            occupied.clear();
            for(Widget id:Widget.values())if(id.moved&&id.w>0&&shown(id)){
                int px=Math.round(id.xRatio*Math.max(0,g.guiWidth()-id.w));
                int py=Math.round(id.yRatio*Math.max(0,g.guiHeight()-id.h));
                occupied.put(id,new HudPacking.Rect(px,py,id.w,id.h));
            }
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof ChatScreen)) return;
            ScreenEvents.afterRender(screen).register((scr,g,mx,my,delta) -> renderEditor(g,mx,my));
            ScreenMouseEvents.allowMouseClick(screen).register((scr,click) ->
                !begin(click));
            ScreenMouseEvents.allowMouseDrag(screen).register((scr,click,dx,dy) ->
                !drag(click));
            ScreenMouseEvents.allowMouseRelease(screen).register((scr,click) ->
                !release(click));
            ScreenEvents.remove(screen).register(scr -> {
                if (dragging!=null) save();
                dragging=null;
            });
        });
    }

    public static boolean chatOpen(Minecraft mc) {return mc.screen instanceof ChatScreen;}
    public static boolean hudVisible(Minecraft mc) {
        return !mc.options.hideGui && (mc.screen == null || chatOpen(mc));
    }
    public static boolean moved(Widget id){return id.moved;}

    public static Bounds place(Widget widget,int defaultX,int defaultY,int width,int height){
        Minecraft mc=Minecraft.getInstance();
        int screenW=mc.getWindow().getGuiScaledWidth();
        int screenH=mc.getWindow().getGuiScaledHeight();
        int ww=Math.max(16,Math.min(screenW, width)), hh=Math.max(12,Math.min(screenH,height));
        int x=widget.moved ? Math.round(widget.xRatio*Math.max(0,screenW-ww)):defaultX;
        int y=widget.moved ? Math.round(widget.yRatio*Math.max(0,screenH-hh)):defaultY;
        x=Math.max(0,Math.min(Math.max(0,screenW-ww),x));
        y=Math.max(0,Math.min(Math.max(0,screenH-hh),y));
        occupied.remove(widget);
        if(!widget.moved&&widget!=Widget.ARROW){
            var packed=HudPacking.find(x,y,ww,hh,screenW,screenH,occupied.values());
            x=packed.x();y=packed.y();
        }
        occupied.put(widget,new HudPacking.Rect(x,y,ww,hh));
        widget.x=x;widget.y=y;widget.w=ww;widget.h=hh;
        return new Bounds(x,y,ww,hh);
    }
    public static Bounds get(Widget id,int w,int h) {
        if (id.w<=0 || id.h<=0) {
            int xx=switch(id) {
                case ARMOR_COLOR, LYRICS, SOCIAL -> w-160;
                case TARGET, KILL -> w/2-75;
                case ARROW -> w/2+65;
                case TNT -> w/2-62;
                default -> 8;
            };
            int yy=switch(id) {
                case TARGET -> h/2+35;
                case KILL -> h/2-38;
                case ARMOR_COLOR -> 18;
                case LYRICS -> 22;
                case SOCIAL -> Math.max(12,h-90);
                case TNT -> 17;
                case TAB -> 26;
                case SHULKER -> 47;
                case ARMOR -> 34;
                case FOOD -> 21;
                default -> 8;
            };
            int rw=switch(id){
                case ARMOR_COLOR -> 132;
                case TARGET -> 150;
                case LYRICS -> 156;
                case SHULKER -> 220;
                case FOOD -> 190;
                case TNT -> 124;
                case KILL -> 190;
                case TAB -> 118;
                default -> 125;
            };
            int rh=switch(id){case ARMOR_COLOR -> 66;case TARGET -> 45;case LYRICS -> 29;case TAB -> 45;default -> 13;};
            return place(id,xx,yy,rw,rh);
        }
        return place(id,id.x,id.y,id.w,id.h);
    }
    private static boolean shown(Widget id) {
        if(!VisionModClient.enabled)return false;
        return switch(id){
            case CPS -> VisionExtras.cps;
            case FOOD -> VisionExtras.hungerHud;
            case ARMOR -> VisionExtras.armorHud;
            case SHULKER -> VisionExtras.shulkerHud;
            case ARMOR_COLOR -> VisionFeaturePack.enabled(1);
            case TARGET -> VisionFeaturePack.enabled(2) || VisionFeaturePack.enabled(3);
            case TNT -> VisionFeaturePack.enabled(4);
            case ARROW -> VisionFeaturePack.enabled(5);
            case LYRICS -> false; // Kept as a config key; lyrics now live in the world.
            case KILL -> VisionExtendedFeatures.enabled(1);
            case TAB -> VisionTabHeads.enabled;
            case SOCIAL -> VisionExtendedFeatures.enabled(5);
        };
    }

    private static void renderEditor(GuiGraphics g,int mouseX,int mouseY) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null)return;
        int screenW=g.guiWidth(),screenH=g.guiHeight();
        g.drawString(mc.font,"VISION HUD / LMB drag | RMB reset position | ESC closes chat",
            Math.max(7,screenW/2-140),5,0xFF9AE7EF);
        for(Widget id:Widget.values()){
            if(!shown(id))continue;
            Bounds b=get(id,screenW,screenH);
            // Do not obscure the chat input at the bottom of the screen.
            if(b.y()>=screenH-34)continue;
            boolean hover=b.contains(mouseX,mouseY);
            int color=(id==dragging)?0xFFE8B6FF:hover?0xFF81E6DD:0xFF686A8E;
            g.fill(b.x()-2,b.y()-2,b.x()+b.width()+2,b.y(),color);
            g.fill(b.x()-2,b.y()+b.height(),b.x()+b.width()+2,b.y()+b.height()+2,color);
            g.fill(b.x()-2,b.y(),b.x(),b.y()+b.height(),color);
            g.fill(b.x()+b.width(),b.y(),b.x()+b.width()+2,b.y()+b.height(),color);
            if(hover || id==dragging) {
                int tw=mc.font.width(id.label)+10;
                int ty=Math.max(12,b.y()-12);
                g.fill(b.x(),ty,b.x()+tw,ty+11,0xE51A1A30);
                g.drawString(mc.font,id.label,b.x()+4,ty+2,0xFFFFFFFF);
            }
        }
    }

    private static boolean begin(MouseButtonEvent e) {
        if (e.button()!=GLFW.GLFW_MOUSE_BUTTON_LEFT &&
            e.button()!=GLFW.GLFW_MOUSE_BUTTON_RIGHT)return false;
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null)return false;
        // Pick the topmost overlay in case two widgets overlap.
        Widget[] widgets=Widget.values();
        for(int i=widgets.length-1;i>=0;i--){
            Widget id=widgets[i];
            if(!shown(id))continue;
            Bounds b=get(id,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
            if(e.y()>=mc.getWindow().getGuiScaledHeight()-34)continue;
            if(b.contains(e.x(),e.y())){
                if(e.button()==GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    id.moved=false;
                    id.w=id.h=0;
                    save();
                    return true;
                }
                dragging=id;
                gripX=e.x()-b.x();gripY=e.y()-b.y();
                initiated=true;
                return true;
            }
        }
        return false;
    }
    private static boolean drag(MouseButtonEvent e){
        if(dragging==null || e.button()!=GLFW.GLFW_MOUSE_BUTTON_LEFT)return false;
        Minecraft mc=Minecraft.getInstance();
        int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
        int usableW=Math.max(1,w-dragging.w),usableH=Math.max(1,h-dragging.h);
        int xx=(int)Math.round(Math.max(0,Math.min(usableW,e.x()-gripX)));
        int yy=(int)Math.round(Math.max(0,Math.min(usableH,e.y()-gripY)));
        dragging.moved=true;
        dragging.xRatio=xx/(float)usableW; dragging.yRatio=yy/(float)usableH;
        dragging.x=xx;dragging.y=yy;
        return true;
    }
    private static boolean release(MouseButtonEvent e) {
        if(dragging==null || e.button()!=GLFW.GLFW_MOUSE_BUTTON_LEFT)return false;
        // Click without motion can still select an overlay; no unwanted reset.
        dragging=null;
        if(initiated){save();initiated=false;}
        return true;
    }
    private static void load(){
        Properties p=new Properties();
        if(!Files.isRegularFile(CONFIG))return;
        try(InputStream in=Files.newInputStream(CONFIG)){
            p.load(in);
            for(Widget id:Widget.values()){
                String prefix=id.name().toLowerCase(java.util.Locale.ROOT)+".";
                if(!Boolean.parseBoolean(p.getProperty(prefix+"moved","false")))continue;
                float x=Float.parseFloat(p.getProperty(prefix+"x","0"));
                float y=Float.parseFloat(p.getProperty(prefix+"y","0"));
                if(Float.isFinite(x)&&Float.isFinite(y)&&x>=0&&x<=1&&y>=0&&y<=1) {
                    id.moved=true;id.xRatio=x;id.yRatio=y;
                }
            }
        }catch(Exception ex){System.err.println("[VisionMod] HUD layout load: "+ex);}
    }
    private static void save(){
        Properties p=new Properties();
        for(Widget id:Widget.values()){
            String prefix=id.name().toLowerCase(java.util.Locale.ROOT)+".";
            p.setProperty(prefix+"moved",Boolean.toString(id.moved));
            p.setProperty(prefix+"x",Float.toString(id.xRatio));
            p.setProperty(prefix+"y",Float.toString(id.yRatio));
        }
        try{
            Files.createDirectories(CONFIG.getParent());
            try(OutputStream out=Files.newOutputStream(CONFIG)) {
                p.store(out,"VisionMod HUD positions; editable while chat is open");
            }
        }catch(Exception ex){System.err.println("[VisionMod] HUD layout save: "+ex);}
    }
    public static void reset() {
        for(Widget id:Widget.values()){id.moved=false;id.w=0;id.h=0;}
        save();
    }
}
