package com.mcmod.monsterwaves.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端请求服务端同步技能点数据（打开加点界面时发送） */
public class C2SRequestSync {
    public static void encode(C2SRequestSync msg, FriendlyByteBuf buf) {
    }

    public static C2SRequestSync decode(FriendlyByteBuf buf) {
        return new C2SRequestSync();
    }

    public static void handle(C2SRequestSync msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && !NetworkHandler.isThrottled(player)) {
                NetworkHandler.sendTo(player, S2CSyncData.from(player));
            }
        });
        context.setPacketHandled(true);
    }
}
