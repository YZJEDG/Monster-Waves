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
 * - 返回符咒传送（带冷却，冷却存玩家 NBT）
 * - 玩家规则：不饥饿、免疫伤害、低于 fallTeleportY 传送至目标维度
 */
public final class SafeDimensionManager {
    public static final ResourceLocation SAFE_ID = new ResourceLocation(MonsterWavesMod.MOD_ID, "safe");
    public static final ResourceKey<Level> SAFE_DIMENSION = ResourceKey.create(Registries.DIMENSION, SAFE_ID);
    public static final String COOLDOWN_KEY = "monsterwaves_safe_cooldown";

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
        long now = player.level().getGameTime();
        long cooldownUntil = player.getPersistentData().getLong(COOLDOWN_KEY);
        if (now < cooldownUntil) {
            int remain = (int) Math.ceil((cooldownUntil - now) / 20.0);
            player.displayClientMessage(Component.literal("返回符咒冷却中，剩余 " + remain + " 秒"), true);
            return false;
        }
        player.getPersistentData().putLong(COOLDOWN_KEY, now + cfg.safeCooldown);
        ensureIsland(safeLevel);
        int y = Math.max(cfg.safeSpawnY, 1);
        player.teleportTo(safeLevel, 0.5, y, 0.5, player.getYRot(), player.getXRot());
        return true;
    }

    /** 跳下空岛传送：safe 维度玩家 Y 低于阈值时传送至目标维度 */
    public static void handleFall(ServerPlayer player) {
        MWConfig cfg = MWConfig.get();
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ResourceKey<Level> targetKey = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.tryParse(cfg.fallDestinationDimension));
        ServerLevel target = server.getLevel(targetKey);
        if (target == null) {
            player.displayClientMessage(Component.literal("坠落目标维度不可用"), true);
            return;
        }
        Vec3 dest;
        if (cfg.useCustomFallDestination) {
            dest = new Vec3(cfg.fallDestinationX + 0.5, cfg.fallDestinationY, cfg.fallDestinationZ + 0.5);
        } else {
            BlockPos spawn = target.getSharedSpawnPos();
            dest = new Vec3(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        }
        player.teleportTo(target, dest.x, dest.y, dest.z, player.getYRot(), player.getXRot());
    }

    /** 空岛生成（幂等）：中心 (0, spawnY-1)，顶部方块 + 3 层泥土 + 4 层石头，圆形半径配置 */
    public static void ensureIsland(ServerLevel safeLevel) {
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
        data.generated = true;
    }

    private static void placeFuncBlock(ServerLevel level, int x, int y, int z, net.minecraft.world.level.block.Block block) {
        level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), 2);
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
