package com.mcmod.monsterwaves.network;

import com.mcmod.monsterwaves.data.PlayerDataManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端请求为某属性加 1 点（成功/失败均回发同步；失败附带原因提示） */
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
            if (player != null && !NetworkHandler.isThrottled(player)) {
                boolean ok = PlayerDataManager.addPoint(player, msg.attributeId);
                if (!ok) {
                    player.displayClientMessage(
                            Component.literal("无法加点：技能点不足、已达上限或属性不可用")
                                    .withStyle(ChatFormatting.RED), true);
                }
                // 无论成败都回发最新数据，界面立即刷新（含属性当前值，客户端属性同步后自动一致）
                NetworkHandler.sendTo(player, S2CSyncData.from(player));
            }
        });
        context.setPacketHandled(true);
    }
}
