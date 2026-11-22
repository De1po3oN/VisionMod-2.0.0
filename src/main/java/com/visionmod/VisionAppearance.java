package com.visionmod;

import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.util.Properties;

/** Shared visual density. Does not change feature toggles or vanilla mechanics. */
public final class VisionAppearance {
    private static final Path FILE=FabricLoader.getInstance().getConfigDir().resolve("visionmod/appearance.properties");
    public static int detail=1,opacity=190,brightness=6;
    public static float handScale=.72f;
    public static boolean animations=true;
    public static boolean smoothHotbar=true;
    public static boolean playerBadge=true;
    public static boolean hideEntityNames;
    private VisionAppearance(){}
    public static int segments(){return switch(detail){case 0->20;case 2->40;default->28;};}
    public static String quality(){return switch(detail){case 0->"ЛАКОНИЧНО";case 2->"ДЕТАЛЬНО";default->"МЯГКО";};}
    public static void cycleDetail(){detail=(detail+1)%3;save();}
    public static void cycleOpacity(){opacity=opacity==150?190:opacity==190?225:150;save();}
    public static void cycleBrightness(){brightness=brightness==3?6:brightness==6?12:3;save();}
    public static void cycleHandScale(){handScale=handScale<.7f?.72f:handScale<.8f?.84f:.60f;save();}
    public static void toggleAnimations(){animations=!animations;save();}
    public static void toggleEntityNames(){
        hideEntityNames = !hideEntityNames;
        save();
    }
    public static boolean entityNamesHidden(){
        return VisionModClient.enabled && hideEntityNames;
    }
    public static void load(){
        if(!Files.isRegularFile(FILE))return;
        try(var in=Files.newInputStream(FILE)){
            Properties p=new Properties();p.load(in);
            detail=Math.clamp(Integer.parseInt(p.getProperty("detail","1")),0,2);
            opacity=Math.clamp(Integer.parseInt(p.getProperty("opacity","190")),100,240);
            brightness=Math.clamp(Integer.parseInt(p.getProperty("brightness","6")),3,16);
            float scale=Float.parseFloat(p.getProperty("handScale",".72"));handScale=Float.isFinite(scale)?Math.clamp(scale,.5f,.9f):.72f;
            animations=Boolean.parseBoolean(p.getProperty("animations","true"));
            smoothHotbar=Boolean.parseBoolean(p.getProperty("smoothHotbar","true"));
            playerBadge=Boolean.parseBoolean(p.getProperty("playerBadge","true"));
            hideEntityNames=Boolean.parseBoolean(p.getProperty("hideEntityNames","false"));
        }catch(Exception e){System.err.println("[VisionMod] Appearance config: "+e.getMessage());}
    }
    public static void save(){
        try{
            Files.createDirectories(FILE.getParent());Properties p=new Properties();
            p.setProperty("detail",Integer.toString(detail));p.setProperty("opacity",Integer.toString(opacity));
            p.setProperty("brightness",Integer.toString(brightness));p.setProperty("handScale",Float.toString(handScale));
            p.setProperty("animations",Boolean.toString(animations));
            p.setProperty("smoothHotbar",Boolean.toString(smoothHotbar));
            p.setProperty("playerBadge",Boolean.toString(playerBadge));
            p.setProperty("hideEntityNames",Boolean.toString(hideEntityNames));
            try(var out=Files.newOutputStream(FILE)){p.store(out,"Vision visual appearance");}
        }catch(Exception e){System.err.println("[VisionMod] Appearance save: "+e.getMessage());}
    }
}
