package com.mcmod.monsterwaves.item;

import com.mcmod.monsterwaves.arena.ArenaDimensionManager;
import com.mcmod.monsterwaves.config.MWConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 战斗符咒：右键传送至刷怪维度（成功后消耗，无冷却） */
public class BattleCharmItem extends Item {

    public BattleCharmItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            MWConfig cfg = MWConfig.get();
            if (!cfg.battleCharmEnabled) {
                serverPlayer.displayClientMessage(Component.literal("战斗符咒未启用"), true);
                return InteractionResultHolder.sidedSuccess(stack, false);
            }
            if (ArenaDimensionManager.teleportToArena(serverPlayer)) {
                if (!serverPlayer.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
