package com.mcmod.monsterwaves.item;

import com.mcmod.monsterwaves.MonsterWavesMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 物品注册（v9.0：移除属性球，仅保留符咒） */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MonsterWavesMod.MOD_ID);

    public static final RegistryObject<Item> RETURN_CHARM = ITEMS.register("return_charm",
            ReturnCharmItem::new);

    public static final RegistryObject<Item> BATTLE_CHARM = ITEMS.register("battle_charm",
            BattleCharmItem::new);

    private ModItems() {
    }
}
