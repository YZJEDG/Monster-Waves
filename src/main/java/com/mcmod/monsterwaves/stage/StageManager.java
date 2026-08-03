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
    public record Stage(String id, double difficulty, long durationTicks) {
        public boolean isInfinite() {
            return durationTicks < 0;
        }
    }

    public static final List<Stage> STAGES = List.of(
            new Stage("萌芽期", 1.0, 6000L),
            new Stage("激战期", 2.5, 12000L),
            new Stage("终局之战", 5.0, -1L)
    );

    private StageManager() {
    }

    public static StageData getData(MinecraftServer server) {
        return StageData.get(server);
    }

    /** 每 tick 调用一次：推进阶段计时器 */
    public static void serverTick(MinecraftServer server) {
        if (server == null || server.overworld() == null) {
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

    public static double getDifficulty(MinecraftServer server) {
        return getData(server).currentStage().difficulty();
    }
}
