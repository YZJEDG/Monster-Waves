package com.mcmod.monsterwaves.attribute;

import com.mcmod.monsterwaves.MonsterWavesMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 自定义属性注册（v9.0 技能点系统测试用）：
 * - 注册到 **tacz** namespace（TaCZ 枪械相关）：`tacz:gun_fire_rate`（射速）、`tacz:gun_reload_speed`（换弹速度）
 * - 百分比型（base 1.0 = 100%，每点 +percentagePerPoint）
 * - 注：TaCZ 1.1.8 本身未注册射速/换弹属性（枪械性能由枪包数据驱动），此二属性为**占位测试**，
 *   验证"mod 属性 + 白名单 + 分组（自动归入 TaCZ mod 组）+ 百分比加点"全流程；
 *   真实桥接 TaCZ 枪械效果需对接 TaCZ API（开发路线"最后做"范畴）。
 * - 风险：若未来 TaCZ 版本注册同名属性会重复注册冲突，需届时改 namespace。
 */
public final class ModAttributes {
    public static final DeferredRegister<Attribute> TACZ_ATTRIBUTES =
            DeferredRegister.create(ForgeRegistries.ATTRIBUTES, "tacz");

    /** 射速（百分比型，base 1.0） */
    public static final RegistryObject<Attribute> GUN_FIRE_RATE = TACZ_ATTRIBUTES.register("gun_fire_rate",
            () -> new RangedAttribute("attribute.name.tacz.gun_fire_rate", 1.0, 0.0, 1024.0).setSyncable(true));

    /** 换弹速度（百分比型，base 1.0） */
    public static final RegistryObject<Attribute> GUN_RELOAD_SPEED = TACZ_ATTRIBUTES.register("gun_reload_speed",
            () -> new RangedAttribute("attribute.name.tacz.gun_reload_speed", 1.0, 0.0, 1024.0).setSyncable(true));

    private ModAttributes() {
    }

    /** 将测试属性挂到玩家属性表（mod 事件总线，EntityAttributeModificationEvent） */
    public static void onAttributeModification(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, GUN_FIRE_RATE.get());
        event.add(EntityType.PLAYER, GUN_RELOAD_SPEED.get());
    }
}
