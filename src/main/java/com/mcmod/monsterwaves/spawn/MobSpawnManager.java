package com.mcmod.monsterwaves.spawn;

import com.mcmod.monsterwaves.MonsterWavesMod;
import com.mcmod.monsterwaves.config.MWConfig;
import com.mcmod.monsterwaves.safe.SafeDimensionManager;
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

    /** 维度开关：空列表 = 全部启用；非空则仅列出的维度刷怪 */
    private static boolean isDimensionEnabled(ServerLevel level) {
        java.util.List<String> dims = MWConfig.get().enabledDimensions;
        return dims.isEmpty() || dims.contains(level.dimension().location().toString());
    }

    public static void serverTick(ServerLevel level) {
        if (SafeDimensionManager.isSafe(level)) {
            return; // 休息维度不生成怪物
        }
        if (!isDimensionEnabled(level)) {
            return; // 维度开关：未启用
        }
        MWConfig cfg = MWConfig.get();
        if (!cfg.enabled) {
            return;
        }
        if (level.getServer().getTickCount() % Math.max(1, cfg.spawnInterval) != 0) {
            return;
        }
        List<EntityType<?>> types = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        StageManager.Stage stage = StageManager.getData(level.getServer()).currentStage();
        if (stage.hasMobListOverride()) {
            loadMobPool(stage.mobListOverride(), types, weights); // 阶段专属怪物池
        } else {
            loadMobPool(cfg.mobPool, types, weights); // 全局怪物池
        }
        if (types.isEmpty()) {
            MonsterWavesMod.LOGGER.warn("MW 怪物池为空，无法生成（检查配置 mobPool/阶段 mobListOverride）");
            return;
        }
        for (ServerPlayer player : level.players()) {
            spawnForPlayer(level, player, cfg, types, weights, stage);
        }
    }

    /** 从怪物池配置解析（"minecraft:zombie:5" 格式；pool 可为全局 mobPool 或阶段 mobListOverride） */
    private static void loadMobPool(List<String> pool, List<EntityType<?>> types, List<Integer> weights) {
        for (String entry : pool) {
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
                                       List<EntityType<?>> types, List<Integer> weights,
                                       StageManager.Stage stage) {
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
                MonsterWavesMod.LOGGER.warn("MW 未找到合法生成位置（维度 {}，玩家 {}）",
                        level.dimension().location(), player.getName().getString());
                continue;
            }
            spawnMob(level, pos, difficulty, types, weights, stage);
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
                                 List<EntityType<?>> types, List<Integer> weights,
                                 StageManager.Stage stage) {
        EntityType<?> type = pickEntityType(level.getRandom(), types, weights);
        if (type == null) {
            return;
        }
        Mob mob = (Mob) type.spawn(level, pos, MobSpawnType.MOB_SUMMONED);
        if (mob == null) {
            MonsterWavesMod.LOGGER.warn("MW 实体生成返回 null（{} 在 {}，维度 {}）",
                    type, pos, level.dimension().location());
            return;
        }
        mob.getPersistentData().putBoolean(MARKER, true);
        applyDifficultyTo(mob, difficulty, stage);
        // 精英/Boss 升级（v9.2+ 第三阶段）
        com.mcmod.monsterwaves.mob.EliteBossHandler.tryUpgrade(mob, difficulty);
        MonsterWavesMod.LOGGER.debug("MW 已生成 {} 于 {}", type, pos);
    }

    /** 属性难度算法：multiply=乘算 / add=加算（未知值回退加算） */
    private static double applyAlgorithm(double base, double difficulty, double perLevel, double stageMult, String algo) {
        if ("multiply".equalsIgnoreCase(algo)) {
            return base * (1 + (difficulty - 1) * perLevel) * stageMult;
        }
        return base + (difficulty - 1) * perLevel * stageMult;
    }

    /**
     * 按难度系数与阶段配置调整生物属性并应用阶段 BUFF（供生成引擎与指令共用）。
     * 算法（v10.1 可配置，每属性独立）：
     * - multiply：基础 × (1 + (难度-1)×系数) × 阶段倍率
     * - add：基础 + (难度-1)×系数 × 阶段倍率
     */
    public static void applyDifficultyTo(Mob mob, double difficulty, StageManager.Stage stage) {
        MWConfig cfg = MWConfig.get();
        var hpAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.setBaseValue(applyAlgorithm(hpAttr.getBaseValue(), difficulty,
                    cfg.healthBonusPerLevel, stage.healthMultiplier(), cfg.healthAlgorithm));
            mob.setHealth(mob.getMaxHealth());
        }
        var atkAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atkAttr != null) {
            atkAttr.setBaseValue(applyAlgorithm(atkAttr.getBaseValue(), difficulty,
                    cfg.attackBonusPerLevel, stage.attackMultiplier(), cfg.attackAlgorithm));
        }
        var armorAttr = mob.getAttribute(Attributes.ARMOR);
        if (armorAttr != null) {
            armorAttr.setBaseValue(applyAlgorithm(armorAttr.getBaseValue(), difficulty,
                    cfg.armorBonusPerLevel, stage.armorMultiplier(), cfg.armorAlgorithm));
        }
        // 阶段 BUFF
        for (var e : stage.effects()) {
            if (mob.getRandom().nextDouble() >= e.chance) {
                continue;
            }
            var effect = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS
                    .getValue(net.minecraft.resources.ResourceLocation.tryParse(e.effect));
            if (effect == null) {
                MonsterWavesMod.LOGGER.warn("MW 未知药水效果：{}", e.effect);
                continue;
            }
            mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    effect, e.duration, e.amplifier, false, e.showParticles, e.showIcon));
        }
    }
}
