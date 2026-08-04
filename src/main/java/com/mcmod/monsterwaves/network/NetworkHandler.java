package com.mcmod.monsterwaves.network;

import com.mcmod.monsterwaves.MonsterWavesMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
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
        // 带方向注册（v1.0.2）：C2S 只接受 PLAY_TO_SERVER、S2C 只接受 PLAY_TO_CLIENT，
        // 防止作弊客户端伪造 S2C 包发往服务端（触发客户端类加载 NoClassDefFoundError 崩溃 / DoS）
        registerMessage(id++, C2SRequestSync.class, C2SRequestSync::encode, C2SRequestSync::decode, C2SRequestSync::handle, NetworkDirection.PLAY_TO_SERVER);
        registerMessage(id++, C2SAddPoint.class, C2SAddPoint::encode, C2SAddPoint::decode, C2SAddPoint::handle, NetworkDirection.PLAY_TO_SERVER);
        registerMessage(id++, C2SResetAll.class, C2SResetAll::encode, C2SResetAll::decode, C2SResetAll::handle, NetworkDirection.PLAY_TO_SERVER);
        registerMessage(id++, S2CSyncData.class, S2CSyncData::encode, S2CSyncData::decode, S2CSyncData::handle, NetworkDirection.PLAY_TO_CLIENT);
        registerMessage(id++, S2COpenGui.class, S2COpenGui::encode, S2COpenGui::decode, S2COpenGui::handle, NetworkDirection.PLAY_TO_CLIENT);
    }

    private static <T> void registerMessage(int id, Class<T> type,
                                            BiConsumer<T, FriendlyByteBuf> encoder,
                                            Function<FriendlyByteBuf, T> decoder,
                                            BiConsumer<T, Supplier<net.minecraftforge.network.NetworkEvent.Context>> handler,
                                            NetworkDirection direction) {
        CHANNEL.registerMessage(id, type, encoder, decoder, handler, Optional.of(direction));
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

    // v1.0.3 C2S 限流：每玩家每 5 tick（0.25 秒）最多处理 1 个 C2S 请求，
    // 防作弊客户端高频伪造请求造成带宽/主线程放大（加点/重置本身有业务校验，不受影响）
    private static final java.util.Map<java.util.UUID, Long> LAST_C2S_TICK = new java.util.concurrent.ConcurrentHashMap<>();

    /** C2S 包 handle 开头调用：返回 true 表示该请求应被丢弃（限流） */
    public static boolean isThrottled(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return false;
        }
        long tick = player.getServer().getTickCount();
        Long last = LAST_C2S_TICK.get(player.getUUID());
        if (last != null && tick - last < 5) {
            return true;
        }
        LAST_C2S_TICK.put(player.getUUID(), tick);
        return false;
    }
}
