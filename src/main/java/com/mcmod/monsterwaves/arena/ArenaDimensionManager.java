package com.mcmod.monsterwaves.arena;

import com.mcmod.monsterwaves.MonsterWavesMod;
import com.mcmod.monsterwaves.config.MWConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

/**
 * 刷怪维度系统：
 * - 维度 monsterwaves:arena（数据包注册，超平坦 grass/dirt/stone 三层）
 * - 专用于战斗，本模组生成引擎在此生效（原版自然生成被拦截）
 * - 提供传送入口（/monsterwaves battle）与休息维度坠落目标
 */
public final class ArenaDimensionManager {
    public static final ResourceLocation ARENA_ID = new ResourceLocation(MonsterWavesMod.MOD_ID, "arena");
    public static final ResourceKey<Level> ARENA_DIMENSION = ResourceKey.create(Registries.DIMENSION, ARENA_ID);

    private ArenaDimensionManager() {
    }

    public static boolean isArena(LevelAccessor level) {
        return level instanceof Level l && l.dimension().equals(ARENA_DIMENSION);
    }

    public static ServerLevel getArenaLevel(MinecraftServer server) {
        return server == null ? null : server.getLevel(ARENA_DIMENSION);
    }

    /** 传送玩家至刷怪维度（检查启用开关，出生点可配置） */
    public static boolean teleportToArena(ServerPlayer player) {
        MWConfig cfg = MWConfig.get();
        if (!cfg.arenaEnabled) {
            player.displayClientMessage(Component.literal("刷怪维度未启用"), true);
            return false;
        }
        ServerLevel arena = getArenaLevel(player.getServer());
        if (arena == null) {
            player.displayClientMessage(Component.literal("刷怪维度不可用"), true);
            return false;
        }
        int y = Math.max(1, cfg.arenaSpawnY);
        player.teleportTo(arena, 0.5, y, 0.5, player.getYRot(), player.getXRot());
        return true;
    }
}
