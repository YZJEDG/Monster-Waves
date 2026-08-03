package com.mcmod.monsterwaves.team;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/** 玩家团队信息（来自 FTB Teams，经反射获取；无 FTB 时为 null 表示团队功能禁用） */
public record TeamInfo(UUID teamId, String name, String shortName, boolean party,
                       int memberCount, List<ServerPlayer> onlineMembers) {
}
