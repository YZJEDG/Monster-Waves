package com.mcmod.monsterwaves.team;

import com.mcmod.monsterwaves.MonsterWavesMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 团队系统：软依赖 FTB Teams。
 * - 检测 {@code ftbteams} mod 是否加载（ModList）
 * - 通过反射调用 FTB Teams API 获取玩家团队（不引入编译期硬依赖）
 * - 未安装 FTB Teams 或调用异常时：团队功能禁用（返回 null / false），不影响本 mod 其他功能
 *
 * <p>反射链（FTB Teams 2001.3.2）：
 * {@code FTBTeamsAPI.api() → API.isManagerLoaded()/getManager() → TeamManager.getTeamForPlayer(player) → Team}
 */
public final class TeamManager {
    public static final String FTB_TEAMS_MOD_ID = "ftbteams";
    private static final String FTB_API_CLASS = "dev.ftb.mods.ftbteams.api.FTBTeamsAPI";

    private TeamManager() {
    }

    /** FTB Teams 是否已加载 */
    public static boolean isFtbTeamsLoaded() {
        return ModList.get().isLoaded(FTB_TEAMS_MOD_ID);
    }

    /**
     * 获取玩家团队信息。未安装 FTB Teams / 管理器未就绪 / 调用异常 → 返回 null（团队功能禁用）。
     */
    public static TeamInfo getTeam(ServerPlayer player) {
        if (player == null || !isFtbTeamsLoaded()) {
            return null;
        }
        try {
            Object manager = getManager();
            if (manager == null) {
                return null;
            }
            Optional<?> opt = (Optional<?>) manager.getClass()
                    .getMethod("getTeamForPlayer", ServerPlayer.class).invoke(manager, player);
            if (opt == null || opt.isEmpty()) {
                return null;
            }
            Object team = opt.get();
            Class<?> cls = team.getClass();

            UUID teamId = (UUID) cls.getMethod("getId").invoke(team);
            String shortName = (String) cls.getMethod("getShortName").invoke(team);
            Object nameComp = cls.getMethod("getName").invoke(team);
            String name = nameComp == null ? shortName : nameComp.toString();
            boolean party = (Boolean) cls.getMethod("isPartyTeam").invoke(team);
            Set<?> members = (Set<?>) cls.getMethod("getMembers").invoke(team);
            Collection<?> online = (Collection<?>) cls.getMethod("getOnlineMembers").invoke(team);

            @SuppressWarnings("unchecked")
            List<ServerPlayer> onlinePlayers = online == null
                    ? List.of()
                    : online.stream().filter(o -> o instanceof ServerPlayer).map(o -> (ServerPlayer) o).toList();
            return new TeamInfo(teamId, name, shortName, party,
                    members == null ? 0 : members.size(), onlinePlayers);
        } catch (Exception e) {
            // FTB API 变更或异常：静默禁用团队功能
            MonsterWavesMod.LOGGER.debug("FTB Teams 调用失败，团队功能禁用", e);
            return null;
        }
    }

    /** 两名玩家是否同队。未安装 FTB Teams / 异常 → false */
    public static boolean areInSameTeam(ServerPlayer a, ServerPlayer b) {
        if (a == null || b == null || !isFtbTeamsLoaded()) {
            return false;
        }
        try {
            Object manager = getManager();
            if (manager == null) {
                return false;
            }
            return (Boolean) manager.getClass()
                    .getMethod("arePlayersInSameTeam", UUID.class, UUID.class)
                    .invoke(manager, a.getUUID(), b.getUUID());
        } catch (Exception e) {
            return false;
        }
    }

    /** 反射获取 FTB Teams 的 TeamManager（管理器未就绪返回 null） */
    private static Object getManager() throws Exception {
        Class<?> apiClass = Class.forName(FTB_API_CLASS);
        Object api = apiClass.getMethod("api").invoke(null);
        if (api == null) {
            return null;
        }
        Method isLoaded = api.getClass().getMethod("isManagerLoaded");
        if (!(Boolean) isLoaded.invoke(api)) {
            return null;
        }
        return api.getClass().getMethod("getManager").invoke(api);
    }
}
