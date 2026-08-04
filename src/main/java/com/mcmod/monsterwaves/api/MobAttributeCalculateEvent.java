package com.mcmod.monsterwaves.api;

import net.minecraft.world.entity.Mob;
import net.minecraftforge.eventbus.api.Event;

/**
 * 怪物属性难度计算事件（v10.1）：
 * 在生成引擎计算怪物生命/攻击/护甲时触发，**开发者可完全自定义算法**。
 * <p>用法（监听本事件）：调用 {@link #setCustomValue(double)} 设置自定义结果，然后 {@link #setCanceled(true)} 取消默认计算。
 * 不取消则使用默认算法（multiply=乘算 / add=加算，由 difficulty 分类配置决定）。
 * <pre>
 * &#64;SubscribeEvent
 * public static void onCalc(MobAttributeCalculateEvent e) {
 *     if (e.getAttribute().equals(MobAttributeCalculateEvent.HEALTH)) {
 *         e.setCustomValue(e.getBaseValue() * (1 + e.getDifficulty() * 0.5)); // 自定义公式
 *         e.setCanceled(true);
 *     }
 * }
 * </pre>
 */
public class MobAttributeCalculateEvent extends Event {
    public static final String HEALTH = "health";
    public static final String ATTACK = "attack";
    public static final String ARMOR = "armor";

    private final Mob mob;
    private final String attribute;
    private final double baseValue;
    private final double difficulty;
    private final double perLevel;
    private final double stageMultiplier;
    private double result;
    private boolean resultSet = false;

    public MobAttributeCalculateEvent(Mob mob, String attribute, double baseValue,
                                      double difficulty, double perLevel, double stageMultiplier,
                                      double defaultValue) {
        this.mob = mob;
        this.attribute = attribute;
        this.baseValue = baseValue;
        this.difficulty = difficulty;
        this.perLevel = perLevel;
        this.stageMultiplier = stageMultiplier;
        this.result = defaultValue;
    }

    public Mob getMob() {
        return mob;
    }

    /** "health" / "attack" / "armor" */
    public String getAttribute() {
        return attribute;
    }

    /** 属性原始基础值（未乘算/加算前） */
    public double getBaseValue() {
        return baseValue;
    }

    /** 当前难度系数 */
    public double getDifficulty() {
        return difficulty;
    }

    /** 每级加成系数（如 healthBonusPerLevel） */
    public double getPerLevel() {
        return perLevel;
    }

    /** 阶段倍率 */
    public double getStageMultiplier() {
        return stageMultiplier;
    }

    /** 开发者设置的自定义最终值（配合 setCanceled(true) 使用） */
    public void setCustomValue(double value) {
        this.result = value;
        this.resultSet = true;
    }

    public double getCustomValue() {
        return result;
    }

    public boolean isResultSet() {
        return resultSet;
    }

    @Override
    public boolean isCancelable() {
        return true;
    }
}
