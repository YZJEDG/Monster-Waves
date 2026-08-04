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
import net.minecraft.world.level.levelgen.Heightmap;
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

    // v1.0.9 本 mod 生成生物的 UUID 追踪集合（会话内存）：掉落拦截/传送优先用集合判断（精确且不依赖 NBT，
    // 兼容任意 mod 生物——其他 mod 生物不在集合也不带 MARKER，原版掉落不受影响）；重启后集合清空，由 NBT MARKER 兜底
    private static final java.util.Set<java.util.UUID> TRACKED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 标记为本 mod 生成（打 NBT 标记 + 加入 UUID 追踪集合） */
    public static void track(Mob mob) {
        if (mob == null) {
            return;
        }
        mob.getPersistentData().putBoolean(MARKER, true);
        TRACKED.add(mob.getUUID());
    }

    /** 本 mod 生成的生物（UUID 集合或 NBT 标记任一命中） */
    public static boolean isTracked(net.minecraft.world.entity.Entity e) {
        return e != null && (TRACKED.contains(e.getUUID())
                || e.getPersistentData().getBoolean(MARKER));
    }

    /** 移除追踪（死亡延迟调用，避免在掉落事件前移除导致拦截失效） */
    public static void untrack(java.util.UUID id) {
        if (id != null) {
            TRACKED.remove(id);
        }
    }

    private MobSpawnManager() {
    }

    /** 维度开关：空列表回退默认仅刷怪维度（v1.3 修正：原空=全部启用导致主世界/下界/末地也刷怪）；非空则仅列出的维度刷怪（与传送/阶段计时共用） */
    public static boolean isDimensionEnabled(ServerLevel level) {
        java.util.List<String> dims = MWConfig.get().enabledDimensions;
        if (dims.isEmpty()) {
            dims = MWConfig.DEFAULT_ENABLED_DIMENSIONS;
        }
        return dims.contains(level.dimension().location().toString());
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
            } catch (NumberFormatException e) {
                // v1.0.3 不再静默丢弃：非法权重条目记 debug 日志，便于定位配置错误
                MonsterWavesMod.LOGGER.debug("MW 怪物池权重非法，已跳过：{}", entry);
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
        // v1.0.3 从地表最高实心块往下找，避免从天空逐格扫到世界底部（getHeight 一次定位）
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, tx, tz) + 1;
        for (; y > level.getMinBuildHeight(); y--) {
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
        TRACKED.add(mob.getUUID());
        applyDifficultyTo(mob, difficulty, stage);
        // 精英/Boss 升级（v9.2+ 第三阶段）
        com.mcmod.monsterwaves.mob.EliteBossHandler.tryUpgrade(mob, difficulty);
        MonsterWavesMod.LOGGER.debug("MW 已生成 {} 于 {}", type, pos);
    }

    /** 属性难度算法：multiply=乘算 / add=加算（未知值回退加算）；触发 MobAttributeCalculateEvent 供开发者完全自定义（取消并用 setResult 覆盖） */
    private static double applyAlgorithm(Mob mob, String attribute, double base, double difficulty,
                                         double perLevel, double stageMult, String algo) {
        double v = "multiply".equalsIgnoreCase(algo)
                ? base * (1 + (difficulty - 1) * perLevel) * stageMult
                : base + (difficulty - 1) * perLevel * stageMult;
        var event = new com.mcmod.monsterwaves.api.MobAttributeCalculateEvent(
                mob, attribute, base, difficulty, perLevel, stageMult, v);
        if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event)) {
            return event.getCustomValue();
        }
        return v;
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
            hpAttr.setBaseValue(applyAlgorithm(mob, com.mcmod.monsterwaves.api.MobAttributeCalculateEvent.HEALTH,
                    hpAttr.getBaseValue(), difficulty,
                    cfg.healthBonusPerLevel, stage.healthMultiplier(), cfg.healthAlgorithm));
            mob.setHealth(mob.getMaxHealth());
        }
        var atkAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atkAttr != null) {
            atkAttr.setBaseValue(applyAlgorithm(mob, com.mcmod.monsterwaves.api.MobAttributeCalculateEvent.ATTACK,
                    atkAttr.getBaseValue(), difficulty,
                    cfg.attackBonusPerLevel, stage.attackMultiplier(), cfg.attackAlgorithm));
        }
        var armorAttr = mob.getAttribute(Attributes.ARMOR);
        if (armorAttr != null) {
            armorAttr.setBaseValue(applyAlgorithm(mob, com.mcmod.monsterwaves.api.MobAttributeCalculateEvent.ARMOR,
                    armorAttr.getBaseValue(), difficulty,
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
