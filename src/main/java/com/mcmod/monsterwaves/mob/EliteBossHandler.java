package com.mcmod.monsterwaves.mob;

import com.mcmod.monsterwaves.config.MWConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 精英怪 / Boss 系统（第三阶段）：
 * - 生成时按概率升级（随难度加成）：精英（名字红/发光/属性×倍率）、Boss（名字金/发光/属性大幅×倍率 + Boss 血条）
 * - 标记存实体 NBT（monsterwaves_elite / monsterwaves_boss），死亡/经验加成读取
 * - 与生成引擎（MobSpawnManager）与统一掉落/经验加成集成
 */
public final class EliteBossHandler {
    public static final String ELITE_KEY = "monsterwaves_elite";
    public static final String BOSS_KEY = "monsterwaves_boss";
    public static final String PREFIX_ELITE = "§c【精英】";
    public static final String PREFIX_BOSS = "§6【Boss】";

    private EliteBossHandler() {
    }

    /** 生成后调用：**先判定精英**（eliteChance），若判定为精英，**再在精英基础上判定 Boss**（bossChance）——Boss 是精英的升级（属性叠加、名字/血条用 Boss 样式） */
    public static void tryUpgrade(Mob mob, double difficulty) {
        if (mob == null || mob.level().isClientSide) {
            return;
        }
        MWConfig cfg = MWConfig.get();
        double mult = cfg.difficultyAffectsChance ? Math.max(1.0, difficulty) : 1.0;
        // 1) 先判定是否精英
        if (!cfg.eliteEnabled || mob.getRandom().nextDouble() >= cfg.eliteChance * mult) {
            return;
        }
        makeElite(mob);
        // 2) 已判定精英后，再判定是否进一步升级为 Boss
        if (cfg.bossEnabled && mob.getRandom().nextDouble() < cfg.bossChance * mult) {
            makeBoss(mob);
        }
    }

    /** 升级为精英 */
    public static void makeElite(Mob mob) {
        MWConfig cfg = MWConfig.get();
        upgrade(mob, ELITE_KEY, PREFIX_ELITE,
                cfg.eliteHealthMultiplier, cfg.eliteAttackMultiplier, cfg.eliteArmorMultiplier);
    }

    /** 升级为 Boss（含 Boss 血条） */
    public static void makeBoss(Mob mob) {
        MWConfig cfg = MWConfig.get();
        upgrade(mob, BOSS_KEY, PREFIX_BOSS,
                cfg.bossHealthMultiplier, cfg.bossAttackMultiplier, cfg.bossArmorMultiplier);
        BossManager.show(mob);
    }

    /** 属性乘算 + 名字变色 + 发光（幂等：已有标记则跳过） */
    private static void upgrade(Mob mob, String key, String prefix,
                                double hpMult, double atkMult, double armMult) {
        var data = mob.getPersistentData();
        if (data.getBoolean(key)) {
            return;
        }
        data.putBoolean(key, true);
        // 生命（改 base 后回满）
        var hp = mob.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(hp.getBaseValue() * hpMult);
            mob.setHealth(mob.getMaxHealth());
        }
        var atk = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) {
            atk.setBaseValue(atk.getBaseValue() * atkMult);
        }
        var arm = mob.getAttribute(Attributes.ARMOR);
        if (arm != null) {
            arm.setBaseValue(arm.getBaseValue() * armMult);
        }
        // 名字变色（Boss 升级时去掉精英前缀，避免 "§6【Boss】§c【精英】" 嵌套）
        String base = mob.hasCustomName() ? mob.getCustomName().getString()
                : mob.getType().getDescription().getString();
        if (base.startsWith(PREFIX_ELITE)) {
            base = base.substring(PREFIX_ELITE.length());
        }
        mob.setCustomName(Component.literal(prefix + base));
        mob.setCustomNameVisible(true);
        // 发光（长期，无粒子）
        mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 999999, 0, false, false, false));
    }

    public static boolean isElite(LivingEntity e) {
        return e != null && e.getPersistentData().getBoolean(ELITE_KEY);
    }

    public static boolean isBoss(LivingEntity e) {
        return e != null && e.getPersistentData().getBoolean(BOSS_KEY);
    }

    /** 精英/Boss 的经验倍率（供经验加成与统一掉落使用） */
    public static double xpMultiplier(LivingEntity e) {
        MWConfig cfg = MWConfig.get();
        if (isBoss(e)) {
            return cfg.bossXpMultiplier;
        }
        if (isElite(e)) {
            return cfg.eliteXpMultiplier;
        }
        return 1.0;
    }
}
