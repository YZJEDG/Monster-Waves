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
 * - TaCZ 枪械：见 {@link GunPainTransferenceHandler}（独立类，tacz 加载时才注册）
 * - 对目标周围半径内其他怪物造成「主伤害 × 百分比」伤害（无冷却；受 affectSameTypeOnly/affectEliteBoss/excludeSource 配置约束）
 */
public final class PainTransferenceHandler {
    /** v1.0.5 防连锁标记：被苦痛传递伤害打中的实体记当前 gameTime，当 tick 不再触发传递（防 StackOverflowError 无限递归） */
    static final String PAIN_CHAIN_KEY = "monsterwaves_pain_chain";

    private PainTransferenceHandler() {
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide || !MWConfig.get().painTransferenceEnabled) {
            return;
        }
        var target = event.getEntity();
        // v1.0.5 防连锁：本次伤害若是苦痛传递造成的（标记 == 当前 tick），直接跳过，禁止递归
        if (target.getPersistentData().getInt(PAIN_CHAIN_KEY) >= target.level().getGameTime()) {
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
    static void trigger(Player player, LivingEntity target, float mainDamage, int level) {
        MWConfig cfg = MWConfig.get();
        double radius = cfg.painTransferenceBaseRadius + (level - 1) * cfg.painTransferenceRadiusPerLevel;
        double percent = cfg.painTransferenceBaseDamagePercent + (level - 1) * cfg.painTransferenceDamagePercentPerLevel;
        float dmg = (float) (mainDamage * percent);
        if (dmg <= 0 || target.level().isClientSide) {
            return;
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
            // v1.0.5 防连锁：hurt 前打标记（当前 gameTime），该实体当 tick 的 hurt 事件不再触发传递
            e.getPersistentData().putInt(PAIN_CHAIN_KEY, (int) e.level().getGameTime());
            e.hurt(e.damageSources().playerAttack(player), dmg);
        }
    }
}
