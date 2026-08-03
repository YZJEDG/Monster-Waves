package com.mcmod.monsterwaves.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MVP 阶段的硬编码配置中心。
 * 字段路径与 v8.3 规格中的 Cloth Config 路径一一对应，后续阶段可直接迁移。
 */
public final class MWConfig {
    private MWConfig() {
    }

    // ===== 全局 =====
    public static final boolean ENABLED = true;

    // ===== 生成设置（spawn.*）=====
    public static final int SPAWN_INTERVAL = 40;          // 生成检测间隔（tick）
    public static final int MIN_DISTANCE = 20;            // 距玩家最小距离
    public static final int MAX_DISTANCE = 32;            // 距玩家最大距离
    public static final int MAX_MOBS_PER_PLAYER = 30;     // 玩家周围本mod怪物数量上限
    public static final int MOB_COUNT_MIN = 1;            // 每次波次最少生成数
    public static final int MOB_COUNT_MAX = 2;            // 每次波次最多生成数
    public static final double MOB_STAT_RADIUS = 48.0;    // 统计本mod怪物的半径

    /** 怪物池：生物注册名 -> 权重（MVP 硬编码，支持任意已注册生物） */
    public static final Map<String, Integer> MOB_POOL = new LinkedHashMap<>();

    static {
        MOB_POOL.put("minecraft:zombie", 5);
        MOB_POOL.put("minecraft:skeleton", 3);
        MOB_POOL.put("minecraft:creeper", 2);
    }

    // ===== 难度参数（spawn.difficulty.*）=====
    public static final double HEALTH_BONUS_PER_LEVEL = 0.2; // 每点难度增加的生命百分比
    public static final double ATTACK_BONUS_PER_LEVEL = 0.5; // 每点难度增加的攻击力（固定值）

    // ===== 属性球（drop.ball.*）=====
    public static final double BALL_BASE_CHANCE = 0.2;    // 普通怪基础掉落率
    public static final int BALL_VALUE = 1;               // 每个属性球的加成点数

    /** 属性球类型（MVP 精简为攻击/生命/护甲三种） */
    public static final String[] BALL_TYPES = {"ATTACK", "HEALTH", "ARMOR"};
}
