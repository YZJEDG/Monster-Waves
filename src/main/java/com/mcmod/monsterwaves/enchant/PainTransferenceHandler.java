package com.mcmod.monsterwaves.enchant;

import com.mcmod.monsterwaves.config.MWConfig;
import com.mcmod.monsterwaves.mob.EliteBossHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 苦痛传递触发逻辑（v10.5）：
 * - 近战（剑/斧/三叉戟）：LivingHurtEvent，直接攻击者为玩家且主手武器带附魔
 * - 弓/弩（原版箭）：ProjectileImpactEvent，箭射手为玩家且手持弓/弩带附魔
 * - TaCZ 枪械：EntityHurtByGunEvent，攻击者主手枪带附魔（射击用主手，从主手读附魔）
 * - 对目标周围半径内其他怪物造成「主伤害 × 百分比」伤害（受 affectSameTypeOnly/affectEliteBoss/excludeSource/cooldown 配置约束）
 */
public final class PainTransferenceHandler {
    public static final String COOLDOWN_KEY = "monsterwaves_pain_cooldown";

    private PainTransferenceHandler() {
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide || !MWConfig.get().painTransferenceEnabled) {
            return;
        }
        var source = event.getSource();
        if (source.getDirectEntity() instanceof Player player) {
            int level = EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.PAIN_TRANSFERENCE.get(), player.getMainHandItem());
            if (level > 0) {
                trigger(player, event.getEntity(), event.getAmount(), level);
            }
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getEntity().level().isClientSide || !MWConfig.get().painTransferenceEnabled) {
            return;
        }
        var projectile = event.getProjectile();
        if (!(projectile instanceof AbstractArrow arrow)) {
            return;
        }
        if (!(arrow.getOwner() instanceof Player player)) {
            return;
        }
        int level = bowLevel(player);
        if (level <= 0 || !(event.getRayTraceResult() instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof LivingEntity target)) {
            return;
        }
        trigger(player, target, (float) arrow.getBaseDamage(), level);
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
            trigger(player, target, event.getAmount(), level);
        }
    }

    /** 读取主手/副手弓弩的附魔等级 */
    private static int bowLevel(Player player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        var ench = ModEnchantments.PAIN_TRANSFERENCE.get();
        int level = 0;
        if (main.getItem() instanceof BowItem || main.getItem() instanceof CrossbowItem) {
            level = EnchantmentHelper.getItemEnchantmentLevel(ench, main);
        }
        if (level == 0 && (off.getItem() instanceof BowItem || off.getItem() instanceof CrossbowItem)) {
            level = EnchantmentHelper.getItemEnchantmentLevel(ench, off);
        }
        return level;
    }

    /** 传递伤害：对目标周围其他怪物造成主伤害 × 百分比 */
    private static void trigger(Player player, LivingEntity target, float mainDamage, int level) {
        MWConfig cfg = MWConfig.get();
        double radius = cfg.painTransferenceBaseRadius + (level - 1) * cfg.painTransferenceRadiusPerLevel;
        double percent = cfg.painTransferenceBaseDamagePercent + (level - 1) * cfg.painTransferenceDamagePercentPerLevel;
        float dmg = (float) (mainDamage * percent);
        if (dmg <= 0 || target.level().isClientSide) {
            return;
        }
        // 冷却（玩家 NBT 存 gameTime）
        if (cfg.painTransferenceCooldown > 0) {
            long now = target.level().getGameTime();
            if (now < player.getPersistentData().getLong(COOLDOWN_KEY)) {
                return;
            }
            player.getPersistentData().putLong(COOLDOWN_KEY, now + cfg.painTransferenceCooldown);
        }
        var box = target.getBoundingBox().inflate(radius);
        for (LivingEntity e : target.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != target && e.isAlive() && e != player)) {
            if (cfg.painTransferenceAffectSameTypeOnly && e.getType() != target.getType()) {
                continue;
            }
            if (!cfg.painTransferenceAffectEliteBoss
                    && (EliteBossHandler.isElite(e) || EliteBossHandler.isBoss(e))) {
                continue;
            }
            e.hurt(e.damageSources().playerAttack(player), dmg);
        }
    }
}
