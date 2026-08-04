package com.mcmod.monsterwaves.network;

import com.mcmod.monsterwaves.MonsterWavesMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * v9.0 技能点网络通信（SimpleChannel）：
 * - C2S：请求同步 / 加点 / 重置全部
 * - S2C：技能点数据同步 / 打开加点界面
 */
public final class NetworkHandler {
    public static final String VERSION = "1";

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MonsterWavesMod.MOD_ID, "main"),
            () -> VERSION,
            VERSION::equals,
            VERSION::equals);

    private NetworkHandler() {
    }

    public static void register() {
        int id = 0;
        registerMessage(id++, C2SRequestSync.class, C2SRequestSync::encode, C2SRequestSync::decode, C2SRequestSync::handle);
        registerMessage(id++, C2SAddPoint.class, C2SAddPoint::encode, C2SAddPoint::decode, C2SAddPoint::handle);
        registerMessage(id++, C2SResetAll.class, C2SResetAll::encode, C2SResetAll::decode, C2SResetAll::handle);
        registerMessage(id++, S2CSyncData.class, S2CSyncData::encode, S2CSyncData::decode, S2CSyncData::handle);
        registerMessage(id++, S2COpenGui.class, S2COpenGui::encode, S2COpenGui::decode, S2COpenGui::handle);
    }

    private static <T> void registerMessage(int id, Class<T> type,
                                            BiConsumer<T, FriendlyByteBuf> encoder,
                                            Function<FriendlyByteBuf, T> decoder,
                                            BiConsumer<T, Supplier<net.minecraftforge.network.NetworkEvent.Context>> handler) {
        CHANNEL.registerMessage(id, type, encoder, decoder, handler);
    }

    public static SimpleChannel channel() {
        return CHANNEL;
    }

    /** 服务端 → 单个玩家 */
    public static void sendTo(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** 客户端 → 服务端 */
    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}
