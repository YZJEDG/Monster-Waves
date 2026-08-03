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
 * - 每 SPAWN_INTERVAL tick 为每个在线玩家生成一波怪物
 * - 怪物生成在玩家 MIN_DISTANCE ~ MAX_DISTANCE 之间的合法位置
 * - 按权重从怪物池选择生物，并按当前难度系数调整生命/攻击
 * - 所有本mod生成的怪物打上 NBT 标记，用于数量统计与属性球掉落判定
 */
public final class MobSpawnManager {
    public static final String MARKER = "monsterwaves_spawned";

    private static final List<EntityType<?>> MOB_TYPES = new ArrayList<>();
    private static final List<Integer> MOB_WEIGHTS = new ArrayList<>();

    static {
        MWConfig.MOB_POOL.forEach((id, weight) -> {
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(id));
            if (type != null) {
                MOB_TYPES.add(type);
                MOB_WEIGHTS.add(weight);
            }
        });
    }

    private MobSpawnManager() {
    }

    public static void serverTick(ServerLevel level) {
        if (!MWConfig.ENABLED) {
            return;
        }
        if (level.getServer().getTickCount() % MWConfig.SPAWN_INTERVAL != 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            spawnForPlayer(level, player);
        }
    }

    private static void spawnForPlayer(ServerLevel level, ServerPlayer player) {
        if (countSpawnedNear(level, player) >= MWConfig.MAX_MOBS_PER_PLAYER) {
            return;
        }
        int toSpawn = MWConfig.MOB_COUNT_MIN
                + level.getRandom().nextInt(MWConfig.MOB_COUNT_MAX - MWConfig.MOB_COUNT_MIN + 1);
        double difficulty = StageManager.getDifficulty(level.getServer());
        for (int i = 0; i < toSpawn; i++) {
            BlockPos pos = findSpawnPos(level, player);
            if (pos == null) {
                continue;
            }
            spawnMob(level, pos, difficulty);
        }
    }

    private static int countSpawnedNear(ServerLevel level, Player player) {
        AABB box = player.getBoundingBox().inflate(MWConfig.MOB_STAT_RADIUS);
        return level.getEntitiesOfClass(Mob.class, box,
                m -> m.getPersistentData().getBoolean(MARKER)).size();
    }

    /** 在玩家周围寻找合法生成位置（最高可达地面），找不到返回 null */
    private static BlockPos findSpawnPos(ServerLevel level, Player player) {
        RandomSource random = level.getRandom();
        double angle = random.nextDouble() * Math.PI * 2;
        double dist = MWConfig.MIN_DISTANCE
                + random.nextDouble() * (MWConfig.MAX_DISTANCE - MWConfig.MIN_DISTANCE);
        int tx = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
        int tz = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int startY = (int) Math.ceil(player.getY()) + 5;
        for (int y = startY; y > level.getMinBuildHeight(); y--) {
            pos.set(tx, y, tz);
            if (!level.isLoaded(pos)) {
                break;
            }
            BlockState here = level.getBlockState(pos);
            if (here.isAir() && level.getBlockState(pos.below()).isSolid()) {
                return pos.immutable();
            }
        }
        return null;
    }

    private static EntityType<?> pickEntityType(RandomSource random) {
        int total = 0;
        for (int w : MOB_WEIGHTS) {
            total += w;
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < MOB_TYPES.size(); i++) {
            roll -= MOB_WEIGHTS.get(i);
            if (roll < 0) {
                return MOB_TYPES.get(i);
            }
        }
        return MOB_TYPES.get(0);
    }

    private static void spawnMob(ServerLevel level, BlockPos pos, double difficulty) {
        EntityType<?> type = pickEntityType(level.getRandom());
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
        double mult = 1 + (difficulty - 1) * MWConfig.HEALTH_BONUS_PER_LEVEL;
        var hpAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.setBaseValue(hpAttr.getBaseValue() * mult);
            mob.setHealth(mob.getMaxHealth());
        }
        var atkAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atkAttr != null) {
            atkAttr.setBaseValue(atkAttr.getBaseValue()
                    + (difficulty - 1) * MWConfig.ATTACK_BONUS_PER_LEVEL);
        }
    }
}
