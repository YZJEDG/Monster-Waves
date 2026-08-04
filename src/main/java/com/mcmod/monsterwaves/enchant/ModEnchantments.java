package com.mcmod.monsterwaves.enchant;

import com.mcmod.monsterwaves.MonsterWavesMod;
import com.mcmod.monsterwaves.config.MWConfig;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * v10.5 苦痛传递附魔（monsterwaves:pain_transference，设计见《mod概述.md》第 13 节）：
 * 命中怪物时对周围一定半径内其他怪物造成「主伤害 × 百分比」的伤害（近战/弓弩/TaCZ 枪械均生效）。
 */
public final class ModEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, MonsterWavesMod.MOD_ID);

    public static final RegistryObject<Enchantment> PAIN_TRANSFERENCE =
            ENCHANTMENTS.register("pain_transference", PainTransferenceEnchantment::new);

    private ModEnchantments() {
    }

    public static void register(IEventBus bus) {
        ENCHANTMENTS.register(bus);
    }

    /** 苦痛传递：剑/斧/三叉戟/弓/弩/TaCZ 枪械可附（canEnchant 覆盖，不依赖原版 category） */
    public static class PainTransferenceEnchantment extends Enchantment {
        public PainTransferenceEnchantment() {
            super(Rarity.RARE, EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
        }

        @Override
        public int getMaxLevel() {
            return Math.max(1, MWConfig.get().painTransferenceMaxLevel);
        }

        @Override
        public boolean canEnchant(ItemStack stack) {
            var item = stack.getItem();
            if (item instanceof SwordItem || item instanceof AxeItem
                    || item instanceof TridentItem || item instanceof BowItem
                    || item instanceof CrossbowItem) {
                return true;
            }
            // TaCZ 枪械（无 tacz 时安全返回 false）
            try {
                return com.tacz.guns.api.item.IGun.getIGunOrNull(stack) != null;
            } catch (Throwable t) {
                return false;
            }
        }

        @Override
        public int getMinCost(int level) {
            return 10 + level * 8;
        }

        @Override
        public int getMaxCost(int level) {
            return getMinCost(level) + 30;
        }
    }
}
