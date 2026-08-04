package com.mcmod.monsterwaves.network;

import com.mcmod.monsterwaves.data.PlayerDataManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端请求重置全部加点（GUI 重置按钮，按配置收费） */
public class C2SResetAll {
    public static void encode(C2SResetAll msg, FriendlyByteBuf buf) {
    }

    public static C2SResetAll decode(FriendlyByteBuf buf) {
        return new C2SResetAll();
    }

    public static void handle(C2SResetAll msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && !NetworkHandler.isThrottled(player)) {
                PlayerDataManager.resetAll(player, true);
                NetworkHandler.sendTo(player, S2CSyncData.from(player));
            }
        });
        context.setPacketHandled(true);
    }
}
