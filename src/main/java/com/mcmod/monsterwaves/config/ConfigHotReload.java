package com.mcmod.monsterwaves.config;

import com.mcmod.monsterwaves.MonsterWavesMod;
import com.mcmod.monsterwaves.data.PlayerDataManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;
import me.shedaniel.autoconfig.AutoConfig;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 配置文件自动热更新（v1.2，configAutoReload 开关，默认开启）。
 * 每 {@link #CHECK_INTERVAL_TICKS} tick（5 秒）检查一次 config/monsterwaves.json5 的修改时间；
 * 手编文件保存后（mtime 变化）自动重新加载配置并重新应用全部在线玩家属性，无需重启/敲指令。
 *
 * <p>与手动指令的区别：/monsterwaves config load 仍是即时手动入口；本类只做低频轮询，开销可忽略。
 * 失败（如 json5 语法错误）会记录日志并保留旧配置，修好后再保存一次文件即可重试。
 */
public final class ConfigHotReload {
    private static final int CHECK_INTERVAL_TICKS = 100; // 5 秒
    private static long lastMtime = -1;

    private ConfigHotReload() {
    }

    /** 服务端每 tick 调用（ModEventHandler.onServerTick） */
    public static void tick(MinecraftServer server) {
        if (!MWConfig.get().configAutoReload) {
            lastMtime = -1; // 关闭期间重置，重新开启后立即能检测到文件变化
            return;
        }
        if (server.getTickCount() % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        Path configPath = FMLPaths.CONFIGDIR.get().resolve(MonsterWavesMod.MOD_ID + ".json5");
        if (!Files.exists(configPath)) {
            lastMtime = -1; // 文件被删除（如重置配置）时重置，出现后重新检测
            return;
        }
        long mtime;
        try {
            mtime = Files.getLastModifiedTime(configPath).toMillis();
        } catch (Exception e) {
            return; // 读不到 mtime 就跳过本轮
        }
        if (mtime != lastMtime) {
            lastMtime = mtime;
            reload(server);
        }
    }

    private static void reload(MinecraftServer server) {
        try {
            AutoConfig.getConfigHolder(MWConfig.class).load();
        } catch (Exception e) {
            MonsterWavesMod.LOGGER.error("MW 配置热更新失败（保留旧配置），文件: monsterwaves.json5，错误: {}", e.toString());
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("【怪物狂潮】配置热更新失败（JSON 语法错误？），已保留旧配置，请检查 config/monsterwaves.json5"),
                    false);
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PlayerDataManager.applyAll(p);
        }
        MonsterWavesMod.LOGGER.info("MW 配置已自动热更新（monsterwaves.json5），玩家属性已重新应用");
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("【怪物狂潮】配置已热更新（无需重启）"), false);
    }
}
