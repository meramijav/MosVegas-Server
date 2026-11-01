package studio.merami.sliminterfacefabric.client.light;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class LightVolume {
    static final int SIZE_X = 96;
    static final int SIZE_Y = 32;
    static final int SIZE_Z = 96;
    static final int TILES = 8;
    static final int BELOW_CAMERA = 12;
    static final int WIDTH = SIZE_X * TILES;
    static final int HEIGHT = SIZE_Z * (SIZE_Y / TILES);
    private static final int LAYERS_PER_TICK = 4;
    private static final int COLUMNS = 8;
    private static final double LEAD = 12;
    private static final double GAP = 3.5 / 16;
    private static final double COVER = .7;
    private static final Map<BlockState, Byte> KINDS = new IdentityHashMap<>();
    private static final Map<Long, byte[]> SKY_COLUMNS = new HashMap<>();
    private static final ByteBuffer PIXELS = ByteBuffer.allocateDirect(WIDTH * HEIGHT * 4);
    private static int texture = -1;
    private static int layer = -1;
    private static BlockPos origin;
    private static BlockPos building;
    private static Object world;

    private LightVolume() {}

    static boolean ready() {
        return origin != null && texture >= 0;
    }

    static int texture() {
        return texture;
    }

    static BlockPos origin() {
        return origin;
    }

    static void tick(Minecraft mc, boolean needed) {
        if (world != mc.level) {
            world = mc.level;
            KINDS.clear();
            origin = null;
            layer = -1;
            if (mc.level == null) release();
        }
        if (mc.level == null || mc.player == null) return;
        if (!needed) {
            origin = null;
            layer = -1;
            return;
        }
        if (layer < 0) {
            Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
            Entity moving = mc.player.getRootVehicle();
            double x = camera.x + (moving.getX() - moving.xo) * LEAD;
            double z = camera.z + (moving.getZ() - moving.zo) * LEAD;
            building = new BlockPos(Mth.floor(x) - SIZE_X / 2, Mth.floor(camera.y) - BELOW_CAMERA, Mth.floor(z) - SIZE_Z / 2);
            layer = 0;
            SKY_COLUMNS.clear();
        }
        int layers = origin == null ? SIZE_Y : LAYERS_PER_TICK;
        for (int i = 0; i < layers && layer < SIZE_Y; i++) fill(mc.level, layer++);
        if (layer >= SIZE_Y) {
            upload();
            origin = building;
            layer = -1;
        }
    }

    private static void fill(ClientLevel level, int y) {
        int worldY = building.getY() + y;
        int tileX = (y % TILES) * SIZE_X;
        int tileZ = (y / TILES) * SIZE_Z;
        int sectionY = SectionPos.blockToSectionCoord(worldY);
        boolean outside = level.isOutsideBuildHeight(worldY);
        LayerLightEventListener blockLight = level.getLightEngine().getLayerListener(LightLayer.BLOCK);
        LayerLightEventListener skyLight = level.getLightEngine().getLayerListener(LightLayer.SKY);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minX = building.getX();
        int minZ = building.getZ();
        int maxX = minX + SIZE_X - 1;
        int maxZ = minZ + SIZE_Z - 1;
        for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
            for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
                LevelChunk chunk = outside ? null : level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                LevelChunkSection section = chunk == null ? null : chunk.getSection(chunk.getSectionIndex(worldY));
                boolean empty = section == null || section.hasOnlyAir();
                SectionPos at = SectionPos.of(cx, sectionY, cz);
                DataLayer blocks = chunk == null ? null : blockLight.getDataLayerData(at);
                DataLayer sky = chunk == null ? null : skyLight.getDataLayerData(at);
                byte[] columns = sky == null ? skyColumns(skyLight, at, pos) : null;
                for (int z = Math.max(minZ, cz << 4); z <= Math.min(maxZ, (cz << 4) + 15); z++) {
                    for (int x = Math.max(minX, cx << 4); x <= Math.min(maxX, (cx << 4) + 15); x++) {
                        byte kind = empty ? 0 : kind(level, section.getBlockState(x & 15, worldY & 15, z & 15), pos.set(x, worldY, z));
                        int i = ((tileZ + z - minZ) * WIDTH + tileX + x - minX) * 4;
                        PIXELS.put(i, kind).put(i + 1, (byte) (blocks == null ? 0 : blocks.get(x & 15, worldY & 15, z & 15)))
                                .put(i + 2, sky != null ? (byte) sky.get(x & 15, worldY & 15, z & 15) : columns[(z & 15) << 4 | x & 15]).put(i + 3, (byte) 0);
                    }
                }
            }
        }
    }

    private static byte[] skyColumns(LayerLightEventListener sky, SectionPos at, BlockPos.MutableBlockPos pos) {
        return SKY_COLUMNS.computeIfAbsent(at.asLong(), key -> {
            byte[] columns = new byte[256];
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) columns[z << 4 | x] = (byte) sky.getLightValue(pos.set(at.minBlockX() + x, at.minBlockY(), at.minBlockZ() + z));
            }
            return columns;
        });
    }

    private static byte kind(ClientLevel level, BlockState state, BlockPos pos) {
        Byte known = KINDS.get(state);
        if (known != null) return known;
        byte kind = 0;
        if (state.is(BlockTags.LEAVES) || state.is(BlockTags.WALLS)) kind = 16;
        else if (state.canOcclude() && !(state.getBlock() instanceof LiquidBlock)) kind = classify(state.getShape(level, pos).toAabbs());
        KINDS.put(state, kind);
        return kind;
    }

    static byte classify(List<AABB> boxes) {
        if (boxes.isEmpty()) return 0;
        float bottom = anchored(boxes, true);
        if (bottom >= .5f / 16) return (byte) Mth.clamp(Math.round(bottom * 16), 1, 16);
        float top = anchored(boxes, false);
        if (top >= .5f / 16) return (byte) (16 + Mth.clamp(Math.round((1 - top) * 16), 1, 15));
        return 0;
    }

    private static float anchored(List<AABB> boxes, boolean bottom) {
        float[] heights = new float[COLUMNS * COLUMNS];
        for (int cz = 0; cz < COLUMNS; cz++) {
            for (int cx = 0; cx < COLUMNS; cx++) {
                double x = (cx + .5) / COLUMNS;
                double z = (cz + .5) / COLUMNS;
                double h = 0;
                for (boolean grown = true; grown; ) {
                    grown = false;
                    for (AABB b : boxes) {
                        if (x < b.minX || x > b.maxX || z < b.minZ || z > b.maxZ) continue;
                        double from = bottom ? b.minY : 1 - b.maxY;
                        double to = bottom ? b.maxY : 1 - b.minY;
                        if (from <= h + GAP && to > h + 1e-6) {
                            h = to;
                            grown = true;
                        }
                    }
                }
                heights[cz * COLUMNS + cx] = (float) Math.min(h, 1);
            }
        }
        Arrays.sort(heights);
        return heights[(int) (heights.length * (1 - COVER))];
    }

    private static void upload() {
        if (texture < 0) texture = LightGL.byteTexture(WIDTH, HEIGHT);
        LightGL.upload(texture, WIDTH, HEIGHT, PIXELS);
    }

    private static void release() {
        LightGL.releaseTexture(texture);
        texture = -1;
    }
}
