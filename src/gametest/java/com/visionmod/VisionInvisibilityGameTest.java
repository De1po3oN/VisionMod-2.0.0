package com.visionmod;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.EntityHitResult;

import static com.visionmod.GuiTestInput.check;

final class VisionInvisibilityGameTest {
    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        context.runOnClient(mc -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.setWindowActive(true);
            mc.mouseHandler.grabMouse();
        });
        world.getServer().runCommand("gamemode creative @p");
        world.getServer().runCommand("tp @p 20.5 -60 0.5 0 0");
        world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:wooden_sword");
        world.getServer().runCommand("summon minecraft:husk 20.5 -60 3.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:[\"vision_visibility_target\"]}");
        try {
            context.waitFor(mc -> VisionFeaturePack.viewedTarget() instanceof Husk, 60);
            int id = context.computeOnClient(mc -> VisionFeaturePack.viewedTarget().getId());
            int before = context.computeOnClient(mc -> VisionCombatTracker.hitSequence());
            context.getInput().pressMouse(0);
            context.waitFor(mc -> VisionCombatTracker.hitSequence() > before, 20);

            context.runOnClient(mc -> {
                var target = mc.level.getEntity(id);
                target.setInvisible(true);
                mc.hitResult = new EntityHitResult(target);
                check(VisionFeaturePack.viewedTarget() == null, "Target HUD retained an invisible cached target");
                check(VisionCombatTracker.crosshairTarget(mc) == null, "Target ESP picked an invisible entity");
                check(!VisionCombatTracker.hitVisible(), "Hit feedback retained an invisible target");
            });

            world.getServer().runCommand("effect give @e[tag=vision_visibility_target] minecraft:invisibility 20 0 true");
            context.waitTicks(24);
            int hiddenBefore = context.computeOnClient(mc -> VisionCombatTracker.hitSequence());
            context.getInput().pressMouse(0);
            context.waitTicks(6);
            context.runOnClient(mc -> {
                check(mc.level.getEntity(id).isInvisible(), "The server invisibility effect did not reach the client");
                check(VisionFeaturePack.viewedTarget() == null, "The Target HUD reacquired an invisible entity");
                check(VisionCombatTracker.hitSequence() == hiddenBefore, "An invisible entity emitted hit feedback");
            });

            world.getServer().runCommand("effect clear @e[tag=vision_visibility_target] minecraft:invisibility");
            context.waitFor(mc -> VisionFeaturePack.viewedTarget() instanceof Husk, 40);
            System.out.println("[VisionMod][INVISIBILITY] PASS: outline, cached HUD, combat feedback, server-applied invisibility and visible target recovery");
        } finally {
            world.getServer().runCommand("kill @e[tag=vision_visibility_target]");
        }
    }
}
