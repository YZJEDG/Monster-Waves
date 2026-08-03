package com.mcmod.monsterwaves.spawn;

import com.mcmod.monsterwaves.config.MWConfig;
import com.mcmod.monsterwaves.stage.StageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * 生成引擎（MVP）：
 * - 每 spawnInterval tick 为每个在线玩家生成一波怪物（配置可调）
 * - 怪物生成在玩家 minDistance ~ maxDistance 之间的合法位置
 * - 按权重从怪物池选择生物（配置 mobPool，格式 "注册名:权重"），并按当前难度系数调整生命/攻击
 * - 所有本mod生成的怪物打上 NBT 标记，用于数量统计与属性球掉落判定
 */
public final class MobSpawnManager {
    public static final String MARKER = "monsterwaves_spawned";

    private MobSpawnManager() {
    }

    public static void serverTick(ServerLevel level) {
        MWConfig cfg = MWConfig.get();
        if (!cfg.enabled) {
            return;
        }
        if (level.getServer().getTickCount() % Math.max(1, cfg.spawnInterval) != 0) {
            return;
        }
        List<EntityType<?>> types = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        loadMobPool(cfg, types, weights);
        if (types.isEmpty()) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            spawnForPlayer(level, player, cfg, types, weights);
        }
    }

    /** 从配置解析怪物池（"minecraft:zombie:5" 格式） */
    private static void loadMobPool(MWConfig cfg, List<EntityType<?>> types, List<Integer> weights) {
        for (String entry : cfg.mobPool) {
            String[] parts = entry.split(":");
            if (parts.length != 3) {
                continue;
            }
            ResourceLocation rl = ResourceLocation.tryParse(parts[0] + ":" + parts[1]);
            EntityType<?> type = rl == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(rl);
            if (type == null) {
                continue;
            }
            try {
                int weight = Integer.parseInt(parts[2]);
                if (weight > 0) {
                    types.add(type);
                    weights.add(weight);
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private static void spawnForPlayer(ServerLevel level, ServerPlayer player, MWConfig cfg,
                                       List<EntityType<?>> types, List<Integer> weights) {
        if (countSpawnedNear(level, player, cfg) >= cfg.maxMobsPerPlayer) {
            return;
        }
        int min = Math.max(1, cfg.mobCountMin);
        int max = Math.max(min, cfg.mobCountMax);
        int toSpawn = min + level.getRandom().nextInt(max - min + 1);
        double difficulty = StageManager.getDifficulty(level.getServer());
        for (int i = 0; i < toSpawn; i++) {
            BlockPos pos = findSpawnPos(level, player, cfg);
            if (pos == null) {
                continue;
            }
            spawnMob(level, pos, difficulty, types, weights);
        }
    }

    private static int countSpawnedNear(ServerLevel level, Player player, MWConfig cfg) {
        AABB box = player.getBoundingBox().inflate(cfg.mobStatRadius);
        return level.getEntitiesOfClass(Mob.class, box,
                m -> m.getPersistentData().getBoolean(MARKER)).size();
    }

    /** 在玩家周围寻找合法生成位置（最高可达地面），找不到返回 null */
    private static BlockPos findSpawnPos(ServerLevel level, Player player, MWConfig cfg) {
        RandomSource random = level.getRandom();
        double angle = random.nextDouble() * Math.PI * 2;
        double dist = cfg.minDistance
                + random.nextDouble() * (cfg.maxDistance - cfg.minDistance);
        int tx = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
        int tz = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int startY = (int) Math.ceil(player.getY()) + 5;
        for (int y = startY; y > level.getMinBuildHeight(); y--) {
            pos.set(tx, y, tz);
            if (!level.isLoaded(pos)) {
                break;
            }
            // 注意：在不可变副本上调用 below()，避免 MutableBlockPos 原地修改
            BlockPos candidate = pos.immutable();
            if (level.getBlockState(candidate).isAir()
                    && level.getBlockState(candidate.below()).isSolid()) {
                return candidate;
            }
        }
        return null;
    }

    private static EntityType<?> pickEntityType(RandomSource random,
                                                List<EntityType<?>> types, List<Integer> weights) {
        if (types.isEmpty()) {
            return null;
        }
        int total = 0;
        for (int w : weights) {
            total += w;
        }
        if (total <= 0) {
            return types.get(0);
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < types.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0) {
                return types.get(i);
            }
        }
        return types.get(0);
    }

    private static void spawnMob(ServerLevel level, BlockPos pos, double difficulty,
                                 List<EntityType<?>> types, List<Integer> weights) {
        EntityType<?> type = pickEntityType(level.getRandom(), types, weights);
        if (type == null) {
            return;
        }
        Mob mob = (Mob) type.spawn(level, pos, MobSpawnType.MOB_SUMMONED);
        if (mob == null) {
            return;
        }
        mob.getPersistentData().putBoolean(MARKER, true);
        applyDifficultyTo(mob, difficulty);
    }

    /** 按难度系数调整生物生命/攻击（供生成引擎与指令共用） */
    public static void applyDifficultyTo(Mob mob, double difficulty) {
        if (difficulty <= 1.0) {
            return;
        }
        MWConfig cfg = MWConfig.get();
        double mult = 1 + (difficulty - 1) * cfg.healthBonusPerLevel;
        var hpAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.setBaseValue(hpAttr.getBaseValue() * mult);
            mob.setHealth(mob.getMaxHealth());
        }
        var atkAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atkAttr != null) {
            atkAttr.setBaseValue(atkAttr.getBaseValue()
                    + (difficulty - 1) * cfg.attackBonusPerLevel);
        }
    }
}
