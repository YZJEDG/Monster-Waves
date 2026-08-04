package com.mcmod.monsterwaves.mob;

import com.mcmod.monsterwaves.config.MWConfig;
import com.mcmod.monsterwaves.spawn.MobSpawnManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 怪物传送（v10.2）：防止玩家跑远导致怪物溢出。
 * - 在**任何启用生成引擎的维度**生效（与 MobSpawnManager 维度开关一致）
 * - 仅影响本 mod 生成的怪（MobSpawnManager.MARKER 标记）
 * - 怪与最近玩家距离超过 teleportThreshold → 传送到玩家附近（min~max 距离、随机角度）
 * - **无冷却**：每次检测超距即传送（防止玩家快速移动导致怪物溢出），每 teleportCheckInterval tick 检测一次
 */
public final class MobTeleportHandler {
    private static int tickCounter = 0;

    private MobTeleportHandler() {
    }

    /** 服务端每 tick 调用（内部按 checkInterval 节流） */
    public static void tick(MinecraftServer server) {
        MWConfig cfg = MWConfig.get();
        if (!cfg.teleportEnabled) {
            return;
        }
        if (++tickCounter % Math.max(1, cfg.teleportCheckInterval) != 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            // 仅在**启用生成引擎的维度**生效（与 MobSpawnManager 维度开关一致）；
            // 仍只对 mod 生成的怪（MARKER）生效，休息维度无 mod 生成的怪自然无效果
            if (!MobSpawnManager.isDimensionEnabled(level)) {
                continue;
            }
            processLevel(level, cfg);
        }
    }

    private static void processLevel(ServerLevel level, MWConfig cfg) {
        List<ServerPlayer> players = level.getPlayers(p -> true);
        if (players.isEmpty()) {
            return;
        }
        // 全维度扫描（box 覆盖整个维度）
        var box = new AABB(-3.0E7, level.getMinBuildHeight(), -3.0E7,
                3.0E7, level.getMaxBuildHeight(), 3.0E7);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box)) {
            if (!mob.getPersistentData().getBoolean(MobSpawnManager.MARKER)) {
                continue;
            }
            if (!mob.isAlive()) {
                continue;
            }
            ServerPlayer nearest = null;
            double best = Double.MAX_VALUE;
            for (ServerPlayer p : players) {
                double d = mob.distanceToSqr(p);
                if (d < best) {
                    best = d;
                    nearest = p;
                }
            }
            if (nearest == null) {
                continue;
            }
            double dist = Math.sqrt(best);
            if (dist > cfg.teleportThreshold) {
                teleportNear(mob, nearest, cfg);
            }
        }
    }

    private static void teleportNear(Mob mob, ServerPlayer player, MWConfig cfg) {
        double min = Math.max(0, cfg.teleportMinDistance);
        double max = Math.max(min + 1, cfg.teleportMaxDistance);
        double dist = min + mob.getRandom().nextDouble() * (max - min);
        double angle = mob.getRandom().nextDouble() * Math.PI * 2;
        double x = player.getX() + Math.cos(angle) * dist;
        double z = player.getZ() + Math.sin(angle) * dist;
        double y = Math.max(player.getY() + 1, player.level().getMinBuildHeight() + 1);
        mob.teleportTo(x, y, z);
        mob.setLastHurtByPlayer(player);
        mob.setTarget(player);
    }
}
