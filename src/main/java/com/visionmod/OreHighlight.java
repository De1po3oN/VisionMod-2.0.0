package com.visionmod;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** Cache ore positions per loaded section and draw the closest exposed blocks. */
public final class OreHighlight {
    private static final VoxelShape OUTLINE = Shapes.box(-.012, -.012, -.012, 1.012, 1.012, 1.012);
    private static final Direction[] FACES = Direction.values();
    private static final int SECTION_SIZE = 16 * 16 * 16;
    private static final int RESCAN_TICKS = 40;
    // Local section reads are cheaper than looking up a world block and its neighbors.
    private static final int SECTION_READS_PER_CHECK = 64;
    private static final Map<Long, SectionScan> SECTIONS = new HashMap<>();
    private static final List<SectionScan> PENDING = new ArrayList<>();
    private static List<BlockPos> outlines = List.of();
    private static ClientLevel level;
    private static int tick;

    private OreHighlight() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(OreHighlight::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            VisionPerformance.observeFrame();
            if (!VisionModClient.isOreEnabled() || outlines.isEmpty() ||
                context.consumers() == null || context.matrices() == null) return;
            Minecraft client = Minecraft.getInstance();
            if (client.level == null || client.level != level || client.player == null) return;

            BlockPos center = client.player.blockPosition();
            int radius = VisionPerformance.oreRadius;
            Vec3 camera = client.gameRenderer.getMainCamera().position();
            VertexConsumer lines = context.consumers().getBuffer(RenderTypes.lines());
            var neighbor = new BlockPos.MutableBlockPos();
            for (BlockPos pos : outlines) {
                if (!inRange(center, pos, radius) || !level.hasChunkAt(pos)) continue;
                Block block = level.getBlockState(pos).getBlock();
                if (!OreSelection.enabled(block) || !exposed(level, pos, neighbor)) continue;
                ShapeRenderer.renderShape(context.matrices(), lines, OUTLINE,
                    pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z,
                    OreColors.colorFor(block), 2.75F);
            }
        });
    }

    private static void tick(Minecraft client) {
        if (client.level != level || client.player == null || !VisionModClient.isOreEnabled()) reset();
        if (client.level == null || client.player == null || !VisionModClient.isOreEnabled()) return;
        level = client.level;
        tick++;

        int radius = VisionPerformance.oreRadius;
        BlockPos center = client.player.blockPosition();
        collectSections(center, radius);
        PENDING.sort(Comparator.comparingInt((SectionScan scan) -> scan.refreshAt)
            .thenComparingInt(scan -> scan.distanceTo(center)));
        int budget = VisionPerformance.effectiveChecksPerTick() * SECTION_READS_PER_CHECK;
        for (SectionScan scan : PENDING) {
            if (budget <= 0) break;
            budget -= scan.read(budget);
        }
        if (tick % 3 == 1) updateOutlines(center, radius);
    }

    private static void collectSections(BlockPos center, int radius) {
        PENDING.clear();
        int minSectionX = SectionPos.blockToSectionCoord(center.getX() - radius);
        int maxSectionX = SectionPos.blockToSectionCoord(center.getX() + radius);
        int minSectionZ = SectionPos.blockToSectionCoord(center.getZ() - radius);
        int maxSectionZ = SectionPos.blockToSectionCoord(center.getZ() + radius);
        int vertical = Math.min(radius, 6);
        int minSectionY = SectionPos.blockToSectionCoord(center.getY() - vertical);
        int maxSectionY = SectionPos.blockToSectionCoord(center.getY() + vertical);

        for (int x = minSectionX; x <= maxSectionX; x++) {
            for (int z = minSectionZ; z <= maxSectionZ; z++) {
                var chunk = level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                var chunkSections = chunk.getSections();
                for (int y = minSectionY; y <= maxSectionY; y++) {
                    int index = level.getSectionIndex(y * 16);
                    if (index < 0 || index >= chunkSections.length) continue;

                    long key = SectionPos.asLong(x, y, z);
                    LevelChunkSection section = chunkSections[index];
                    SectionScan scan = SECTIONS.get(key);
                    if (scan == null || scan.section != section) {
                        scan = new SectionScan(x * 16, y * 16, z * 16, section);
                        SECTIONS.put(key, scan);
                    }
                    scan.seenAt = tick;
                    if (scan.refreshAt <= tick) PENDING.add(scan);
                }
            }
        }
        SECTIONS.values().removeIf(scan -> scan.seenAt != tick);
    }

    private static void updateOutlines(BlockPos center, int radius) {
        int limit = VisionPerformance.effectiveOutlineLimit();
        var closest = new PriorityQueue<Outline>(limit, Comparator.comparingDouble(Outline::distance).reversed());
        var neighbor = new BlockPos.MutableBlockPos();
        for (SectionScan scan : SECTIONS.values()) {
            for (BlockPos pos : scan.ores) {
                if (!inRange(center, pos, radius)) continue;
                double distance = pos.distSqr(center);
                if (closest.size() == limit && distance >= closest.element().distance()) continue;
                if (!OreSelection.enabled(level.getBlockState(pos).getBlock()) || !exposed(level, pos, neighbor)) continue;
                if (closest.size() == limit) closest.remove();
                closest.add(new Outline(pos, distance));
            }
        }
        outlines = closest.stream().map(Outline::pos).toList();
    }

    static boolean outlined(BlockPos pos) {
        return outlines.contains(pos);
    }

    private static boolean inRange(BlockPos center, BlockPos pos, int radius) {
        return Math.abs(pos.getX() - center.getX()) <= radius &&
            Math.abs(pos.getZ() - center.getZ()) <= radius &&
            Math.abs(pos.getY() - center.getY()) <= Math.min(radius, 6);
    }

    private static boolean exposed(ClientLevel level, BlockPos pos, BlockPos.MutableBlockPos neighbor) {
        for (Direction face : FACES) {
            if (!level.getBlockState(neighbor.setWithOffset(pos, face)).isSolidRender()) return true;
        }
        return false;
    }

    private static void reset() {
        level = null;
        tick = 0;
        SECTIONS.clear();
        PENDING.clear();
        outlines = List.of();
    }

    private record Outline(BlockPos pos, double distance) {}

    private static final class SectionScan {
        private final int x, y, z;
        private final LevelChunkSection section;
        private List<BlockPos> ores = List.of();
        private final List<BlockPos> building = new ArrayList<>();
        private int cursor;
        private int refreshAt;
        private int seenAt;

        private SectionScan(int x, int y, int z, LevelChunkSection section) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.section = section;
        }

        private int distanceTo(BlockPos center) {
            int dx = center.getX() - Math.clamp(center.getX(), x, x + 15);
            int dy = center.getY() - Math.clamp(center.getY(), y, y + 15);
            int dz = center.getZ() - Math.clamp(center.getZ(), z, z + 15);
            return dx * dx + dy * dy + dz * dz;
        }

        private int read(int budget) {
            if (cursor == 0 && !section.maybeHas(state -> OreSelection.index(state.getBlock()) >= 0)) {
                ores = List.of();
                refreshAt = tick + RESCAN_TICKS;
                return 1;
            }

            int reads = Math.min(budget, SECTION_SIZE - cursor);
            int end = cursor + reads;
            for (; cursor < end; cursor++) {
                int localX = cursor & 15;
                int localZ = (cursor >> 4) & 15;
                int localY = cursor >> 8;
                if (OreSelection.index(section.getBlockState(localX, localY, localZ).getBlock()) >= 0) {
                    building.add(new BlockPos(x + localX, y + localY, z + localZ));
                }
            }

            if (cursor == SECTION_SIZE) {
                ores = List.copyOf(building);
                building.clear();
                cursor = 0;
                refreshAt = tick + RESCAN_TICKS;
            }
            return reads;
        }
    }
}
