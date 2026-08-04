package com.mcmod.monsterwaves.enchant;

import com.mcmod.monsterwaves.config.MWConfig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * TaCZ 枪械的苦痛传递触发（v10.5.1）：
 * **仅在 tacz mod 加载时注册**（ModList.isLoaded 判断）——本类方法签名引用 tacz 事件类，
 * tacz 缺失时绝不能注册/加载本类，否则 NoClassDefFoundError 崩溃。
 */
public final class GunPainTransferenceHandler {
    private GunPainTransferenceHandler() {
    }

    @SubscribeEvent
    public static void onGunHurt(com.tacz.guns.api.event.common.EntityHurtByGunEvent event) {
        if (!MWConfig.get().painTransferenceEnabled) {
            return;
        }
        if (!(event.getAttacker() instanceof Player player)) {
            return;
        }
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.PAIN_TRANSFERENCE.get(), player.getMainHandItem());
        if (level > 0 && event.getHurtEntity() instanceof LivingEntity target) {
            PainTransferenceHandler.trigger(player, target, event.getAmount(), level);
        }
    }
}
