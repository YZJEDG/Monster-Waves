package com.mcmod.monsterwaves.network;

import com.mcmod.monsterwaves.data.PlayerDataManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端请求为某属性加 1 点 */
public class C2SAddPoint {
    private final String attributeId;

    public C2SAddPoint(String attributeId) {
        this.attributeId = attributeId;
    }

    public static void encode(C2SAddPoint msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.attributeId);
    }

    public static C2SAddPoint decode(FriendlyByteBuf buf) {
        return new C2SAddPoint(buf.readUtf(256));
    }

    public static void handle(C2SAddPoint msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                PlayerDataManager.addPoint(player, msg.attributeId);
                NetworkHandler.sendTo(player, S2CSyncData.from(player));
            }
        });
        context.setPacketHandled(true);
    }
}
