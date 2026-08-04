package com.mcmod.monsterwaves.safe;

import com.mcmod.monsterwaves.MonsterWavesMod;
import com.mcmod.monsterwaves.config.MWConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 休息维度系统：
 * - 维度 monsterwaves:safe（数据包注册，flat 全空 + 代码生成空岛）
 * - 休息符咒传送（消耗制，无冷却）
 * - 玩家规则：不饥饿、免疫伤害、低于 fallTeleportY 传送至目标维度
 */
public final class SafeDimensionManager {
    public static final ResourceLocation SAFE_ID = new ResourceLocation(MonsterWavesMod.MOD_ID, "safe");
    public static final ResourceKey<Level> SAFE_DIMENSION = ResourceKey.create(Registries.DIMENSION, SAFE_ID);

    private SafeDimensionManager() {
    }

    public static boolean isSafe(LevelAccessor level) {
        return level instanceof Level l && l.dimension().equals(SAFE_DIMENSION);
    }

    public static ServerLevel getSafeLevel(MinecraftServer server) {
        return server == null ? null : server.getLevel(SAFE_DIMENSION);
    }

    /** 传送玩家至休息维度（带冷却，返回是否成功） */
    public static boolean teleportToSafe(ServerPlayer player) {
        MWConfig cfg = MWConfig.get();
        if (!cfg.safeEnabled) {
            player.displayClientMessage(Component.literal("休息维度未启用"), true);
            return false;
        }
        ServerLevel safeLevel = getSafeLevel(player.getServer());
        if (safeLevel == null) {
            player.displayClientMessage(Component.literal("休息维度不可用"), true);
            return false;
        }
        ensureIsland(safeLevel);
        int y = Math.max(cfg.safeSpawnY, 1);
        player.teleportTo(safeLevel, 0.5, y, 0.5, player.getYRot(), player.getXRot());
        return true;
    }

    /** 返回主世界（任意维度可用，`/monsterwaves leave`）：优先玩家重生点（床），否则主世界出生点 */
    public static boolean teleportToSpawn(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        ServerLevel overworld = server.overworld();
        BlockPos respawn = player.getRespawnPosition();
        if (respawn != null && Level.OVERWORLD.equals(player.getRespawnDimension())) {
            player.teleportTo(overworld, respawn.getX() + 0.5, respawn.getY(), respawn.getZ() + 0.5,
                    player.getRespawnAngle(), 0.0F);
        } else {
            BlockPos spawn = overworld.getSharedSpawnPos();
            player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        }
        player.displayClientMessage(Component.literal("已返回主世界"), true);
        return true;
    }

    /** 跳下空岛传送：safe 维度玩家 Y 低于阈值时传送至目标维度（主世界则传玩家重生点） */    public static void handleFall(ServerPlayer player) {
        MWConfig cfg = MWConfig.get();
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ResourceLocation fallLoc = ResourceLocation.tryParse(cfg.fallDestinationDimension);
        if (fallLoc == null) {
            // v1.0.2 配置非法防护：避免 ResourceKey 含 null location 导致每 tick NPE 崩溃
            fallLoc = new ResourceLocation(com.mcmod.monsterwaves.MonsterWavesMod.MOD_ID, "arena");
            com.mcmod.monsterwaves.MonsterWavesMod.LOGGER.warn(
                    "MW fallDestinationDimension 配置非法（{}），回退 monsterwaves:arena",
                    cfg.fallDestinationDimension);
        }
        ResourceKey<Level> targetKey = ResourceKey.create(Registries.DIMENSION, fallLoc);
        ServerLevel target = server.getLevel(targetKey);
        if (target == null) {
            player.displayClientMessage(Component.literal("坠落目标维度不可用"), true);
            return;
        }

        // 传送到重生点开关：开启=主世界用玩家重生点/其他维度用目标出生点；关闭=自定义坐标
        if (cfg.fallToRespawnPoint) {
            if (targetKey.equals(Level.OVERWORLD)) {
                // 主世界：传送到玩家重生点（床/出生点），无重生点则世界出生点
                BlockPos respawn = player.getRespawnPosition();
                if (respawn != null && player.getRespawnDimension().equals(Level.OVERWORLD)) {
                    player.teleportTo(target, respawn.getX() + 0.5, respawn.getY(), respawn.getZ() + 0.5,
                            player.getRespawnAngle(), 0.0F);
                } else {
                    BlockPos spawn = target.getSharedSpawnPos();
                    player.teleportTo(target, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
                }
            } else {
                // 其他维度：战斗维度用竞技场出生点（对齐 teleportToArena），其余用目标维度出生点
                if (targetKey.equals(com.mcmod.monsterwaves.arena.ArenaDimensionManager.ARENA_DIMENSION)) {
                    int y = Math.max(1, cfg.arenaSpawnY);
                    player.teleportTo(target, 0.5, y, 0.5, player.getYRot(), player.getXRot());
                } else {
                    BlockPos spawn = target.getSharedSpawnPos();
                    player.teleportTo(target, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
                }
            }
            return;
        }

        // 自定义坐标
        Vec3 dest = new Vec3(cfg.fallDestinationX + 0.5, cfg.fallDestinationY, cfg.fallDestinationZ + 0.5);
        player.teleportTo(target, dest.x, dest.y, dest.z, player.getYRot(), player.getXRot());
    }

    /** 空岛生成（幂等）：中心 (0, spawnY-1)，顶部方块 + 3 层泥土 + 4 层石头，圆形半径配置 */    public static void ensureIsland(ServerLevel safeLevel) {
        if (!isSafe(safeLevel)) {
            return;
        }
        MWConfig cfg = MWConfig.get();
        IslandData data = IslandData.get(safeLevel);
        if (data.generated) {
            return;
        }
        int topY = cfg.safeSpawnY - 1;
        int r = Math.max(1, cfg.islandRadius);
        BlockState top = parseBlock(cfg.islandBlock, Blocks.GRASS_BLOCK.defaultBlockState());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z > r * r) {
                    continue; // 圆形
                }
                safeLevel.setBlock(pos.set(x, topY, z), top, 2);
                safeLevel.setBlock(pos.set(x, topY - 1, z), Blocks.DIRT.defaultBlockState(), 2);
                safeLevel.setBlock(pos.set(x, topY - 2, z), Blocks.DIRT.defaultBlockState(), 2);
                safeLevel.setBlock(pos.set(x, topY - 3, z), Blocks.DIRT.defaultBlockState(), 2);
                for (int y = topY - 4; y >= topY - 7; y--) {
                    safeLevel.setBlock(pos.set(x, y, z), Blocks.STONE.defaultBlockState(), 2);
                }
            }
        }
        // 功能性方块（中心十字布局，玩家出生点 (0, surfaceY, 0) 保持空出）
        int surfaceY = topY + 1;
        placeFuncBlock(safeLevel, 1, surfaceY, 0, Blocks.CRAFTING_TABLE);
        placeFuncBlock(safeLevel, -1, surfaceY, 0, Blocks.FURNACE);
        placeFuncBlock(safeLevel, 0, surfaceY, 1, Blocks.CHEST);
        placeFuncBlock(safeLevel, 0, surfaceY, -1, Blocks.BLAST_FURNACE);
        placeFuncBlock(safeLevel, 1, surfaceY, 1, Blocks.SMOKER);
        placeFuncBlock(safeLevel, -1, surfaceY, 1, Blocks.STONECUTTER);
        // 附魔台区域：5x5 外环 16 个书架（满级附魔）
        int ex = 4, ez = 0;
        placeFuncBlock(safeLevel, ex, surfaceY, ez, Blocks.ENCHANTING_TABLE);
        for (int bx = ex - 2; bx <= ex + 2; bx++) {
            for (int bz = ez - 2; bz <= ez + 2; bz++) {
                if (Math.abs(bx - ex) <= 1 && Math.abs(bz - ez) <= 1) {
                    continue; // 附魔台周围 1 格留空
                }
                placeFuncBlock(safeLevel, bx, surfaceY, bz, Blocks.BOOKSHELF);
            }
        }
        // 景观装饰：树 / 花与草 / 周围漂浮小岛
        placeTree(safeLevel, -5, topY + 1, -4);
        placeTree(safeLevel, 7, topY + 1, -3);
        placeTree(safeLevel, -8, topY + 1, 6);
        placeFlowers(safeLevel, topY, r);
        placeFloatIsland(safeLevel, 12, topY - 3, 8, 3);
        placeFloatIsland(safeLevel, -13, topY - 5, -9, 4);
        placeFloatIsland(safeLevel, 14, topY - 2, -12, 2);
        data.generated = true;
    }

    private static void placeFuncBlock(ServerLevel level, int x, int y, int z, net.minecraft.world.level.block.Block block) {
        level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), 2);
    }

    /** 手工放置一棵小橡树（主干 + 三层树叶冠） */
    private static void placeTree(ServerLevel level, int x, int baseY, int z) {
        for (int i = 0; i < 4; i++) {
            level.setBlock(new BlockPos(x, baseY + i, z), Blocks.OAK_LOG.defaultBlockState(), 2);
        }
        setLeaves(level, x, baseY + 3, z, 1);
        setLeaves(level, x, baseY + 4, z, 2);
        setLeaves(level, x, baseY + 5, z, 1);
        level.setBlock(new BlockPos(x, baseY + 5, z), Blocks.OAK_LEAVES.defaultBlockState(), 2);
    }

    private static void setLeaves(ServerLevel level, int cx, int y, int cz, int r) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (Math.abs(dx) == r && Math.abs(dz) == r) {
                    continue;
                }
                BlockPos p = new BlockPos(cx + dx, y, cz + dz);
                if (level.getBlockState(p).isAir()) {
                    level.setBlock(p, Blocks.OAK_LEAVES.defaultBlockState(), 2);
                }
            }
        }
    }

    /** 空岛地面撒花草（避开中心设施区与附魔台区） */
    private static void placeFlowers(ServerLevel level, int topY, int r) {
        net.minecraft.util.RandomSource random = level.getRandom();
        net.minecraft.world.level.block.Block[] flowers = {
                Blocks.GRASS, Blocks.POPPY, Blocks.DANDELION, Blocks.OXEYE_DAISY,
                Blocks.CORNFLOWER, Blocks.TALL_GRASS, Blocks.AZURE_BLUET
        };
        for (int i = 0; i < 40; i++) {
            int x = random.nextInt(r * 2) - r;
            int z = random.nextInt(r * 2) - r;
            if (x * x + z * z > r * r) {
                continue;
            }
            if (Math.abs(x) <= 5 && Math.abs(z) <= 5) {
                continue; // 中心设施区
            }
            if (x >= 2 && x <= 6 && Math.abs(z) <= 2) {
                continue; // 附魔台区
            }
            BlockPos p = new BlockPos(x, topY + 1, z);
            if (level.getBlockState(p).isAir() && level.getBlockState(p.below()).isSolid()) {
                level.setBlock(p, flowers[random.nextInt(flowers.length)].defaultBlockState(), 2);
            }
        }
    }

    /** 周围漂浮小岛（草顶 + 泥土），带小树苗装饰 */
    private static void placeFloatIsland(ServerLevel level, int cx, int cy, int cz, int r) {
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z > r * r) {
                    continue;
                }
                level.setBlock(new BlockPos(cx + x, cy, cz + z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                level.setBlock(new BlockPos(cx + x, cy - 1, cz + z), Blocks.DIRT.defaultBlockState(), 2);
            }
        }
        if (level.getRandom().nextBoolean()) {
            level.setBlock(new BlockPos(cx, cy + 1, cz), Blocks.OAK_SAPLING.defaultBlockState(), 2);
        }
    }

    /** 重置空岛标记并立即重建（覆盖旧方块与装饰，调试/刷新景观用） */
    public static void resetIsland(ServerLevel safeLevel) {
        if (!isSafe(safeLevel)) {
            return;
        }
        IslandData data = IslandData.get(safeLevel);
        data.generated = false;
        ensureIsland(safeLevel);
    }

    private static BlockState parseBlock(String id, BlockState fallback) {
        if (id == null) {
            return fallback;
        }
        var block = ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryParse(id));
        return block == null ? fallback : block.defaultBlockState();
    }

    /** 休息维度玩家规则：锁定饥饿与饱和度 */
    public static void applySafeRules(ServerPlayer player) {
        if (isSafe(player.level())) {
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(10.0f);
        }
    }

    /** 空岛生成标记（维度 saved data，跨重启保留） */
    public static class IslandData extends SavedData {
        public static final String DATA_NAME = "monsterwaves_safe_island";
        public boolean generated = false;

        public static IslandData get(ServerLevel level) {
            DimensionDataStorage storage = level.getDataStorage();
            return storage.computeIfAbsent(IslandData::load, IslandData::new, DATA_NAME);
        }

        public static IslandData load(CompoundTag tag) {
            IslandData data = new IslandData();
            data.generated = tag.getBoolean("generated");
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putBoolean("generated", generated);
            return tag;
        }
    }
}
