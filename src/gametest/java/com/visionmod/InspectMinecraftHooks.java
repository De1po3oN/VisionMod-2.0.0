package com.visionmod;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

public final class InspectMinecraftHooks {
    public static void main(String[] args) throws Exception {
        inspect("net/minecraft/client/renderer/entity/ItemEntityRenderer", true);
        inspect("net/minecraft/client/renderer/entity/state/ItemEntityRenderState", false);
        inspect("net/minecraft/client/renderer/entity/state/ItemClusterRenderState", false);
        inspect("net/minecraft/client/renderer/item/ItemStackRenderState", false);
        inspect("net/minecraft/client/renderer/OrderedSubmitNodeCollector", false);
    }

    private static void inspect(String name, boolean calls) throws Exception {
        try (var stream=InspectMinecraftHooks.class.getClassLoader().getResourceAsStream(name+".class")) {
            if (stream==null) { System.out.println("missing "+name); return; }
            var node=new ClassNode();new ClassReader(stream).accept(node,0);
            System.out.println(name+" extends "+node.superName);
            for (var field:node.fields) System.out.println("field "+field.name+field.desc);
            for (var method:node.methods) {
                System.out.println(method.name+method.desc);
                if (!calls) continue;
                for (var instruction:method.instructions) {
                    if (instruction instanceof MethodInsnNode call)
                        System.out.println(" call "+call.owner+"."+call.name+call.desc);
                    else if (instruction instanceof FieldInsnNode field)
                        System.out.println(" field "+field.owner+"."+field.name+field.desc);
                    else if (instruction instanceof LdcInsnNode literal)
                        System.out.println(" constant "+literal.cst);
                }
            }
        }
    }
}
