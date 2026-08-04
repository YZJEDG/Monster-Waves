package com.mcmod.monsterwaves.api;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/**
 * 技能点重置事件：重置全部（attributeId=null）或单属性分配时触发（可取消）。
 */
@Cancelable
public class SkillPointResetEvent extends PlayerEvent {
    /** 重置的属性注册名；null = 重置全部 */
    private final String attributeId;

    public SkillPointResetEvent(Player player, String attributeId) {
        super(player);
        this.attributeId = attributeId;
    }

    public String getAttributeId() {
        return attributeId;
    }
}
