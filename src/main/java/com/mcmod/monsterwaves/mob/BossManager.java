package com.mcmod.monsterwaves.mob;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Boss 血条管理（ServerBossEvent，紫色条，Boss 存活期间对全体在线玩家显示） */
public final class BossManager {
    private static final Map<UUID, Entry> BARS = new HashMap<>();

    private record Entry(ServerLevel level, ServerBossEvent event) {
    }

    private BossManager() {
    }

    /** Boss 生成/升级时调用 */
    public static void show(Mob boss) {
        hide(boss);
        if (boss.level().isClientSide) {
            return;
        }
        ServerBossEvent event = new ServerBossEvent(boss.getDisplayName(),
                BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
        MinecraftServer server = boss.getServer();
        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                event.addPlayer(p);
            }
        }
        BARS.put(boss.getUUID(), new Entry((ServerLevel) boss.level(), event));
    }

    /** Boss 死亡/卸载时调用 */
    public static void hide(Mob boss) {
        Entry entry = BARS.remove(boss.getUUID());
        if (entry != null) {
            entry.event().removeAllPlayers();
        }
    }

    /** 服务端每 tick 更新血条进度，清理已死亡的 Boss */
    public static void tick(MinecraftServer server) {
        if (BARS.isEmpty()) {
            return;
        }
        var it = BARS.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Entry entry = e.getValue();
            Entity entity = entry.level().getEntity(e.getKey());
            if (!(entity instanceof Mob boss) || !boss.isAlive()) {
                entry.event().removeAllPlayers();
                it.remove();
                continue;
            }
            float progress = boss.getMaxHealth() <= 0 ? 0 : boss.getHealth() / boss.getMaxHealth();
            entry.event().setProgress(Math.max(0, Math.min(1, progress)));
            entry.event().setName(boss.getDisplayName());
        }
    }
}
