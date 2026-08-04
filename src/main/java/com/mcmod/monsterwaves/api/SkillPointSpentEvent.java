package com.mcmod.monsterwaves.api;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/**
 * 技能点消耗事件：玩家为某属性加点消耗 1 技能点时触发（可取消）。
 */
@Cancelable
public class SkillPointSpentEvent extends PlayerEvent {
    private final String attributeId;
    private int amount;

    public SkillPointSpentEvent(Player player, String attributeId, int amount) {
        super(player);
        this.attributeId = attributeId;
        this.amount = amount;
    }

    /** 目标属性注册名（如 minecraft:generic.attack_damage） */
    public String getAttributeId() {
        return attributeId;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = Math.max(1, amount);
    }
}
