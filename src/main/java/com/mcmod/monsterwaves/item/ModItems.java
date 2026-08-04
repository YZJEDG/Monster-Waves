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

    /** 回归符咒：右键回主世界重生点 */
    public static final RegistryObject<Item> HOME_CHARM = ITEMS.register("home_charm",
            HomeCharmItem::new);

    /** 三个符咒加入原版创造物品栏「战斗用品（Combat）」 */
    public static void onBuildCreativeTab(net.minecraftforge.event.BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.COMBAT) {
            event.accept(RETURN_CHARM.get());
            event.accept(BATTLE_CHARM.get());
            event.accept(HOME_CHARM.get());
        }
    }

    private ModItems() {
    }
}
