package com.mcmod.monsterwaves.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 服务端 → 客户端：打开技能点加点界面（指令 /monsterwaves skill gui 用） */
public class S2COpenGui {
    public static void encode(S2COpenGui msg, FriendlyByteBuf buf) {
    }

    public static S2COpenGui decode(FriendlyByteBuf buf) {
        return new S2COpenGui();
    }

    public static void handle(S2COpenGui msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(S2COpenGui::openScreen);
        context.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void openScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        // 与 P 键一致：打开前先请求服务端同步最新技能点数据
        NetworkHandler.sendToServer(new C2SRequestSync());
        mc.setScreen(new com.mcmod.monsterwaves.client.SkillScreen());
    }
}
