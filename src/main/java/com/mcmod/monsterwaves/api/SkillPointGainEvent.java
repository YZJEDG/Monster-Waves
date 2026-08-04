package com.mcmod.monsterwaves.api;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/**
 * 技能点获取事件：玩家获得技能点时触发。
 * 其他 mod 可监听此事件实现自定义获取算法（取消发放或修改数量）。
 */
@Cancelable
public class SkillPointGainEvent extends PlayerEvent {
    private int amount;

    public SkillPointGainEvent(Player player, int amount) {
        super(player);
        this.amount = amount;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = Math.max(0, amount);
    }
}
