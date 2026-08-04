package com.mcmod.monsterwaves.stage;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.List;

/**
 * 阶段系统：维护硬编码的阶段列表（MVP），支持按时间自动推进与手动切换。
 * 阶段切换时向全体玩家广播提示。
 */
public final class StageManager {
    public record Stage(String id, double difficulty, long durationTicks,
                        java.util.List<String> mobListOverride,
                        double healthMultiplier, double attackMultiplier, double armorMultiplier,
                        java.util.List<com.mcmod.monsterwaves.config.MWConfig.StageConfig.EffectEntry> effects) {
        public boolean isInfinite() {
            return durationTicks < 0;
        }

        public boolean hasMobListOverride() {
            return mobListOverride != null && !mobListOverride.isEmpty();
        }
    }

    private StageManager() {
    }

    /** 从配置读取阶段列表（各阶段难度/时长/怪物池/属性倍率/BUFF 可分别调整） */
    // v1.0.3 5 秒窗口缓存：避免每 tick 重建阶段对象；GUI/指令改配置后最多 5 秒生效
    private static java.util.List<Stage> cachedStages = null;
    private static long cachedWindow = -1;

    public static java.util.List<Stage> getStages() {
        long window = System.currentTimeMillis() / 5000;
        if (cachedStages == null || cachedWindow != window) {
            cachedWindow = window;
            cachedStages = com.mcmod.monsterwaves.config.MWConfig.get().stages.stream()
                    .map(s -> new Stage(s.id, s.difficulty, s.duration,
                            s.mobListOverride == null ? java.util.List.of() : s.mobListOverride,
                            s.attributeMultipliers == null ? 1.0 : s.attributeMultipliers.healthMultiplier,
                            s.attributeMultipliers == null ? 1.0 : s.attributeMultipliers.attackMultiplier,
                            s.attributeMultipliers == null ? 1.0 : s.attributeMultipliers.armorMultiplier,
                            s.mobEffects == null ? java.util.List.of() : s.mobEffects))
                    .toList();
        }
        return cachedStages;
    }

    public static StageData getData(MinecraftServer server) {
        return StageData.get(server);
    }

    /** 每 tick 调用一次：推进阶段计时器（v1.0.6 仅当有玩家处于启用刷怪引擎的维度时计时） */
    public static void serverTick(MinecraftServer server) {
        if (server == null || server.overworld() == null) {
            return;
        }
        // v1.0.6：无玩家在启用维度（enabledDimensions 白名单内）→ 暂停阶段计时；
        // 玩家全部离开（如都回主世界/休息维度）阶段冻结，回到启用维度后继续推进
        boolean anyActivePlayer = false;
        for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level() instanceof net.minecraft.server.level.ServerLevel lv
                    && com.mcmod.monsterwaves.spawn.MobSpawnManager.isDimensionEnabled(lv)) {
                anyActivePlayer = true;
                break;
            }
        }
        if (!anyActivePlayer) {
            return;
        }
        StageData data = getData(server);
        if (data.tick()) {
            broadcastSwitch(server, "自动推进");
        }
    }

    /** 广播阶段切换消息（自动推进或手动指令共用） */
    public static void broadcastSwitch(MinecraftServer server, String reason) {
        Stage stage = getData(server).currentStage();
        Component msg = Component.literal("【怪物狂潮】阶段" + reason + " → ")
                .append(Component.literal(stage.id()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("（难度 x" + stage.difficulty() + "）"));
        server.getPlayerList().broadcastSystemMessage(msg, false);
    }

    /** 当前生效的难度系数 = 当前阶段难度（各阶段分别配置），参与各类参数运算 */
    public static double getDifficulty(MinecraftServer server) {
        return getData(server).currentStage().difficulty();
    }
}
