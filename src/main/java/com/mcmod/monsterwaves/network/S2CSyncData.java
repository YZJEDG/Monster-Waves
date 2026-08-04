package com.mcmod.monsterwaves.network;

import com.mcmod.monsterwaves.data.PlayerDataManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** 服务端 → 客户端：技能点数据同步（加点/重置/请求后回发） */
public class S2CSyncData {
    private final int points;
    private final int totalAllocated;
    private final Map<String, Integer> allocated;

    public S2CSyncData(int points, int totalAllocated, Map<String, Integer> allocated) {
        this.points = points;
        this.totalAllocated = totalAllocated;
        this.allocated = allocated;
    }

    public static S2CSyncData from(ServerPlayer player) {
        return new S2CSyncData(
                PlayerDataManager.getPoints(player),
                PlayerDataManager.totalAllocated(player),
                PlayerDataManager.getAllAllocated(player));
    }

    public int getPoints() {
        return points;
    }

    public int getTotalAllocated() {
        return totalAllocated;
    }

    public Map<String, Integer> getAllocated() {
        return allocated;
    }

    public static void encode(S2CSyncData msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.points);
        buf.writeInt(msg.totalAllocated);
        buf.writeVarInt(msg.allocated.size());
        for (Map.Entry<String, Integer> e : msg.allocated.entrySet()) {
            buf.writeUtf(e.getKey(), 256);
            buf.writeVarInt(e.getValue());
        }
    }

    public static S2CSyncData decode(FriendlyByteBuf buf) {
        int points = buf.readInt();
        int total = buf.readInt();
        int size = buf.readVarInt();
        Map<String, Integer> allocated = new HashMap<>();
        for (int i = 0; i < size; i++) {
            allocated.put(buf.readUtf(256), buf.readVarInt());
        }
        return new S2CSyncData(points, total, allocated);
    }

    public static void handle(S2CSyncData msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            com.mcmod.monsterwaves.client.SkillDataCache.apply(msg);
            com.mcmod.monsterwaves.client.SkillScreen screen =
                    com.mcmod.monsterwaves.client.SkillScreen.getOpenInstance();
            if (screen != null) {
                screen.refresh();
            }
        });
        context.setPacketHandled(true);
    }
}
