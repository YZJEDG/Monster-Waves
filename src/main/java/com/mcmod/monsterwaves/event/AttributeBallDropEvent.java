package com.mcmod.monsterwaves.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * 属性球掉落事件：本mod生成的怪物死亡时触发（于 LivingDeathEvent 中发布到 Forge 总线）。
 *
 * <p>可修改参数（setter）：
 * <ul>
 *   <li>{@code chance}：掉落判定概率（默认 = min(1, baseChance × 难度)），设 0 禁掉、设 1 必掉</li>
 *   <li>{@code attributeType}：指定属性类型（null = 随机），须为配置 ballTypes 之一</li>
 *   <li>{@code ballCount}：掉落数量（至少 1）</li>
 * </ul>
 *
 * <p>可取消：取消后本次不掉落任何属性球。
 */
@Cancelable
public class AttributeBallDropEvent extends Event {
    private final LivingEntity entity;
    private final ServerLevel level;
    private final Vec3 dropPos;
    private double chance;
    private String attributeType;
    private int ballCount;

    public AttributeBallDropEvent(LivingEntity entity, ServerLevel level, Vec3 dropPos,
                                  double chance, String attributeType, int ballCount) {
        this.entity = entity;
        this.level = level;
        this.dropPos = dropPos;
        this.chance = chance;
        this.attributeType = attributeType;
        this.ballCount = ballCount;
    }

    /** 被击杀的怪物 */
    public LivingEntity getEntity() {
        return entity;
    }

    public ServerLevel getLevel() {
        return level;
    }

    /** 掉落位置（怪物死亡位置上方 0.5 格） */
    public Vec3 getDropPos() {
        return dropPos;
    }

    /** 当前掉落判定概率（0~1） */
    public double getChance() {
        return chance;
    }

    public void setChance(double chance) {
        this.chance = chance;
    }

    /** 指定属性类型（null = 随机）；须为配置 ballTypes 之一 */
    public String getAttributeType() {
        return attributeType;
    }

    public void setAttributeType(String attributeType) {
        this.attributeType = attributeType;
    }

    /** 掉落数量（至少 1） */
    public int getBallCount() {
        return ballCount;
    }

    public void setBallCount(int ballCount) {
        this.ballCount = ballCount;
    }
}
