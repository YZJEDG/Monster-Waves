package com.mcmod.monsterwaves.item;

import com.mcmod.monsterwaves.MonsterWavesMod;
import com.mcmod.monsterwaves.data.PlayerDataManager;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 物品注册：MVP 阶段仅注册 3 种属性球 */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MonsterWavesMod.MOD_ID);

    public static final RegistryObject<Item> ATTACK_BALL = ITEMS.register("attack_ball",
            () -> new AttributeBallItem(PlayerDataManager.ATK));
    public static final RegistryObject<Item> HEALTH_BALL = ITEMS.register("health_ball",
            () -> new AttributeBallItem(PlayerDataManager.HP));
    public static final RegistryObject<Item> ARMOR_BALL = ITEMS.register("armor_ball",
            () -> new AttributeBallItem(PlayerDataManager.ARMOR));

    public static final RegistryObject<Item> RETURN_CHARM = ITEMS.register("return_charm",
            ReturnCharmItem::new);

    private ModItems() {
    }

    public static Item getBall(String attributeType) {
        return switch (attributeType) {
            case PlayerDataManager.ATK -> ATTACK_BALL.get();
            case PlayerDataManager.HP -> HEALTH_BALL.get();
            case PlayerDataManager.ARMOR -> ARMOR_BALL.get();
            default -> ATTACK_BALL.get();
        };
    }
}
