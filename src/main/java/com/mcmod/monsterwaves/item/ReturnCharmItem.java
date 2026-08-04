package com.mcmod.monsterwaves.item;

import com.mcmod.monsterwaves.safe.SafeDimensionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 休息符咒：右键传送至休息维度（带冷却，成功后消耗） */
public class ReturnCharmItem extends Item {
    public ReturnCharmItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (SafeDimensionManager.teleportToSafe(serverPlayer)) {
                // 传送成功：非创造模式消耗符咒
                if (!serverPlayer.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
