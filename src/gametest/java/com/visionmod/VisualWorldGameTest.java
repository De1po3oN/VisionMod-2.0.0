package com.visionmod;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.item.PrimedTnt;
import java.nio.file.*;

/** Real integrated-world render smoke; does not replace shader/visual acceptance or FPS measurement. */
public final class VisualWorldGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context){
        context.runOnClient(mc->{
            mc.options.renderDistance().set(3);
            mc.options.simulationDistance().set(5);
            mc.options.pauseOnLostFocus=false;
        });
        context.waitTicks(5);
        try(var world=context.worldBuilder().create()){
            world.getServer().runCommand("gamemode creative @p");
            world.getServer().runCommand("time set day");
            world.getServer().runCommand("weather clear");
            world.getServer().runCommand("tp @p 0 -60 0 0 0");
            world.getServer().runCommand("item replace entity @p armor.head with minecraft:diamond_helmet");
            world.getServer().runCommand("item replace entity @p armor.chest with minecraft:diamond_chestplate");
            world.getServer().runCommand("item replace entity @p armor.legs with minecraft:diamond_leggings");
            world.getServer().runCommand("item replace entity @p armor.feet with minecraft:diamond_boots");
            world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:shulker_box");
            world.getServer().runCommand("setblock 2 -60 0 minecraft:diamond_ore");
            world.getServer().runCommand("summon minecraft:armor_stand 0 -60 2.5");
            world.getServer().runOnServer(server->{
                var level=server.overworld();
                var tnt=new PrimedTnt(level,1,-60,2,null);tnt.setFuse(1200);level.addFreshEntity(tnt);
            });
            context.waitTicks(4);
            context.runOnClient(mc->{
                VisionModClient.enabled=true;VisionRenderBoost.enabled=false;
                VisionModClient.oreHighlight=true;VisionPerformance.oreRadius=4;VisionPerformance.checksPerTick=80;
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                for(int i:new int[]{0,1,2,3,7,8,9})if(!VisionExtras.enabled(i))VisionExtras.toggle(i);
                for(int i=0;i<VisionWorldEffects.count();i++)if(!VisionWorldEffects.enabled(i))VisionWorldEffects.toggle(i);
                for(int i:new int[]{1,2,3,4,5,6,7})if(!VisionFeaturePack.enabled(i))VisionFeaturePack.toggle(i);
                for(int i:new int[]{0,1,2,3,4,5})if(!VisionExtendedFeatures.enabled(i))VisionExtendedFeatures.toggle(i);
                VisionExtendedFeatures.setCustomText("VISION · WORLD TEST");
                if(!VisionTools2.selected(1))VisionTools2.toggle(1);
                VisionTabHeads.enabled=true;
            });
            world.getClientWorld().waitForChunksRender();
            context.waitTicks(35);
            assertRenderers(context);
            capture(context,"01-first-person");
            context.runOnClient(mc->{mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);mc.options.hideGui=true;});
            context.waitTicks(10);assertRenderers(context);capture(context,"02-pet-hat-target");
            world.getServer().runCommand("kill @e[type=minecraft:armor_stand]");
            world.getServer().runCommand("tp @p 4 -60 0 0 12");
            context.waitTicks(3);
            context.getInput().holdKey(options->options.keyUp);
            context.getInput().holdKey(options->options.keyJump);
            context.waitTicks(2);
            context.getInput().releaseKey(options->options.keyJump);
            context.waitTicks(6);assertRenderers(context);capture(context,"03-trail-and-particles");
            context.getInput().releaseKey(options->options.keyUp);
            context.waitFor(mc->mc.player.onGround(),100);
            context.waitTicks(2);assertRenderers(context);
            context.runOnClient(mc->{if(!VisionWorldEffects.landingActive())throw new AssertionError("Landing did not trigger jump rings");});
            capture(context,"04-landing-and-pet");
            VisionPetMotionGameTest.run(context,world);
            VisionLyricsPresenceGameTest.run(context,world);
            context.runOnClient(mc->mc.options.hideGui=false);
            VisionControlsGameTest.run(context);
            VisionInventoryGameTest.run(context,world);
            VisionMenuMemoryGameTest.run(context);
            VisionGuiDesignGameTest.run(context);
            VisionSpearGameTest.run(context,world);
            VisionNameTagGameTest.run(context);
            VisionItemPhysicsGameTest.run(context,world);
            VisionFriendZoneGameTest.run(context);
            VisionInvisibilityGameTest.run(context,world);
            VisionOreHighlightGameTest.run(context,world);
            context.setScreen(VisionMenuSession::dashboard);
            context.waitTicks(3);capture(context,"05-dashboard-in-world");
            context.setScreen(()->null);
            long[] frames=context.computeOnClient(mc->new long[]{VisionPetRenderer.renderedFrames(),VisionWorldEffects.renderedFrames(),VisionCustomModels.renderedFrames()});
            context.runOnClient(mc->VisionModClient.enabled=false);
            context.waitTicks(3);capture(context,"06-global-off");
            context.runOnClient(mc->{
                if(frames[0]!=VisionPetRenderer.renderedFrames()||frames[1]!=VisionWorldEffects.renderedFrames()||frames[2]!=VisionCustomModels.renderedFrames())
                    throw new AssertionError("Global OFF left a renderer running");
                System.out.println("[VisionMod][WORLDTEST] Submitted frames: pet="+frames[0]+", world="+frames[1]+", custom="+frames[2]);
            });
            System.out.println("[VisionMod][WORLDTEST] PASS: loaded world, active renderers, movement/jump, input/camera/hotbar, framebuffer captures and global OFF");
        }catch(Throwable e){
            Thread.getAllStackTraces().forEach((thread,stack)->{
                if(thread.getName().equals("Server thread")||thread.getName().equals("Render thread")||thread.getName().startsWith("Worker-")){
                    System.err.println("[VisionMod][WORLDTEST] Thread: "+thread.getName()+" "+thread.getState());
                    for(int i=0;i<Math.min(24,stack.length);i++)System.err.println("  at "+stack[i]);
                }
            });
            throw new AssertionError("Vision world render test failed",e);
        }
    }
    private static void assertRenderers(ClientGameTestContext context){
        context.runOnClient(mc->{
            if(mc.level==null||mc.player==null)throw new AssertionError("World not loaded");
            if(VisionPetRenderer.suspended())throw new AssertionError("Pet renderer silently suspended");
            if(VisionWorldEffects.suspended())throw new AssertionError("World FX silently suspended");
            if(VisionCustomModels.suspended())throw new AssertionError("Custom model renderer silently suspended");
            if(mc.options.getCameraType()!=CameraType.FIRST_PERSON&&
                (VisionPetRenderer.renderedFrames()==0||VisionWorldEffects.renderedFrames()==0||VisionCustomModels.renderedFrames()==0))
                throw new AssertionError("An enabled renderer never submitted a frame");
        });
    }
    static void capture(ClientGameTestContext context,String name)throws java.io.IOException{
        Path image=context.takeScreenshot("vision-world-"+name);
        Path directory=Path.of("vision-world-captures");Files.createDirectories(directory);
        Files.copy(image,directory.resolve(name+".png"),StandardCopyOption.REPLACE_EXISTING);
    }
}
