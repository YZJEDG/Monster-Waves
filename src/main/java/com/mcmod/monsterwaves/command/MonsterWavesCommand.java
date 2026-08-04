package com.mcmod.monsterwaves.command;

import com.mcmod.monsterwaves.arena.ArenaDimensionManager;
import com.mcmod.monsterwaves.config.MWConfig;
import com.mcmod.monsterwaves.data.PlayerDataManager;
import com.mcmod.monsterwaves.network.NetworkHandler;
import com.mcmod.monsterwaves.network.S2COpenGui;
import com.mcmod.monsterwaves.spawn.MobSpawnManager;
import com.mcmod.monsterwaves.stage.StageData;
import com.mcmod.monsterwaves.stage.StageManager;
import com.mcmod.monsterwaves.safe.SafeDimensionManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

/**
 * /monsterwaves 指令（v9.0）：
 * - spawn <生物> [数量]           管理员：指定位置生成怪物（带难度应用与掉落标记）
 * - stats [玩家]                 查看玩家技能点与已分配属性
 * - difficulty                   查看当前阶段与难度系数
 * - stage info|next|prev|set <id> 阶段管理
 * - safe|safe reset|battle       休息/刷怪维度传送
 * - skill [points <玩家>]        查看技能点
 * - skill add|set <玩家> <数量>   管理员：发放/设置技能点
 * - skill reset <玩家> [属性]     管理员：重置加点（返还技能点）
 * - skill gui                    打开加点界面
 * - player list                  在线玩家列表
 */
public final class MonsterWavesCommand {
    private MonsterWavesCommand() {
    }

    /** Tab 补全：已注册实体（过滤非生物类 MISC 实体，如物品/箭/船） */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_MOBS = (ctx, builder) ->            SharedSuggestionProvider.suggest(
                    ForgeRegistries.ENTITY_TYPES.getKeys().stream()
                            .filter(rl -> {
                                EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(rl);
                                return type != null && type.getCategory() != MobCategory.MISC;
                            })
                            .map(ResourceLocation::toString)
                            .sorted(),
                    builder);

    /** spawn 类型补全：normal / elite / boss */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_MOB_TYPE = (ctx, builder) ->
            SharedSuggestionProvider.suggest(java.util.List.of("normal", "elite", "boss"), builder);

    /** Tab 补全：可用阶段 id */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_STAGE_IDS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    StageManager.getStages().stream().map(StageManager.Stage::id).toList(), builder);

    /** Tab 补全：白名单属性注册名（attributeConfigs 键，含 tacz 等模组属性） */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_ATTRIBUTES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(MWConfig.get().attributeConfigs.keySet(), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("monsterwaves")
                .then(Commands.literal("spawn")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("mob", ResourceLocationArgument.id()).suggests(SUGGEST_MOBS)
                                .executes(ctx -> spawn(ctx, 1, "normal"))
                                .then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 100))
                                        .executes(ctx -> spawn(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "count"), "normal"))
                                        .then(Commands.argument("type", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .suggests(SUGGEST_MOB_TYPE)
                                                .executes(ctx -> spawn(ctx,
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "count"),
                                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "type")))))))
                .then(Commands.literal("stats")
                        .executes(ctx -> stats(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(src -> src.hasPermission(2)) // v1.0.3 查他人需 op
                                .executes(ctx -> stats(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("difficulty")
                        .executes(MonsterWavesCommand::difficulty))
                .then(Commands.literal("safe")
                        .requires(src -> src.hasPermission(2))
                        .executes(MonsterWavesCommand::safe)
                        .then(Commands.literal("reset")
                                .requires(src -> src.hasPermission(2))
                                .executes(MonsterWavesCommand::safeReset)))
                .then(Commands.literal("battle")
                        .requires(src -> src.hasPermission(2))
                        .executes(MonsterWavesCommand::battle))
                .then(Commands.literal("leave")
                        .executes(MonsterWavesCommand::leave))
                .then(Commands.literal("stage")
                        .then(Commands.literal("info").executes(MonsterWavesCommand::stageInfo))
                        .then(Commands.literal("next").requires(src -> src.hasPermission(2))
                                .executes(MonsterWavesCommand::stageNext))
                        .then(Commands.literal("prev").requires(src -> src.hasPermission(2))
                                .executes(MonsterWavesCommand::stagePrev))
                        .then(Commands.literal("set").requires(src -> src.hasPermission(2))
                                .then(Commands.argument("id", StringArgumentType.greedyString()).suggests(SUGGEST_STAGE_IDS)
                                        .executes(MonsterWavesCommand::stageSet))))
                .then(Commands.literal("skill")
                        .executes(ctx -> stats(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.literal("points")
                                .executes(ctx -> stats(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .requires(src -> src.hasPermission(2)) // v1.0.3 查他人需 op
                                        .executes(ctx -> stats(ctx, EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("add")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 99999))
                                                .executes(MonsterWavesCommand::skillAdd))))
                        .then(Commands.literal("set")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 99999))
                                                .executes(MonsterWavesCommand::skillSet))))
                        .then(Commands.literal("reset")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(MonsterWavesCommand::skillResetAll)
                                        .then(Commands.argument("attribute", StringArgumentType.greedyString())
                                                .suggests(SUGGEST_ATTRIBUTES)
                                                .executes(MonsterWavesCommand::skillResetAttr))))
                        .then(Commands.literal("gui")
                                .executes(MonsterWavesCommand::skillGui)))
                .then(Commands.literal("reload")
                        .requires(src -> src.hasPermission(2))
                        .executes(MonsterWavesCommand::reloadConfig))
                .then(Commands.literal("config")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("save")
                                .executes(MonsterWavesCommand::configSave)))
                .then(Commands.literal("player")
                        .then(Commands.literal("list")
                                .executes(MonsterWavesCommand::playerList)))
        );
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, int count, String type) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        // v1.0.3 type 枚举校验（不再静默按 normal 处理）
        if (!"normal".equals(type) && !"elite".equals(type) && !"boss".equals(type)) {
            ctx.getSource().sendFailure(Component.literal("无效类型：" + type + "（可用 normal / elite / boss）"));
            return 0;
        }
        ResourceLocation rl = ResourceLocationArgument.getId(ctx, "mob");
        String mobId = rl.toString();
        EntityType<?> type_ = ForgeRegistries.ENTITY_TYPES.getValue(rl);
        if (type_ == null) {
            ctx.getSource().sendFailure(Component.literal("未知生物：" + mobId + "（格式：minecraft:zombie）"));
            return 0;
        }
        ServerLevel level = ctx.getSource().getLevel();
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        double difficulty = StageManager.getDifficulty(level.getServer());
        StageManager.Stage stage = StageManager.getData(level.getServer()).currentStage();
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            Entity entity = type_.spawn(level,
                    player.blockPosition().offset(level.getRandom().nextInt(5) - 2, 0,
                            level.getRandom().nextInt(5) - 2),
                    MobSpawnType.COMMAND);
            if (entity instanceof Mob mob) {
                mob.getPersistentData().putBoolean(MobSpawnManager.MARKER, true);
                MobSpawnManager.applyDifficultyTo(mob, difficulty, stage);
                // 强制类型：normal（默认）/ elite / boss（v10.4 完整指令集）
                if ("elite".equals(type)) {
                    com.mcmod.monsterwaves.mob.EliteBossHandler.makeElite(mob);
                } else if ("boss".equals(type)) {
                    com.mcmod.monsterwaves.mob.EliteBossHandler.makeBoss(mob);
                }
                spawned++;
            } else if (entity != null) {
                // 非 Mob 实体（如物品/经验球）不适用本模组流程，直接移除
                entity.discard();
            }
        }
        int finalSpawned = spawned;
        String typeLabel = "normal".equals(type) ? "" : (" §e[" + type + "]");
        String finalType = typeLabel;
        ctx.getSource().sendSuccess(() -> Component.literal("已生成 " + finalSpawned + " 只 " + mobId + finalType), true);
        return finalSpawned;
    }

    /** 查看玩家技能点与已分配属性（stats 与 skill/points 共用） */
    private static int stats(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        PlayerDataManager.migrateIfNeeded(target);
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.literal("=== " + target.getScoreboardName() + " 技能点 ===")
                .withStyle(ChatFormatting.AQUA), false);
        src.sendSuccess(() -> Component.literal("可用技能点：" + PlayerDataManager.getPoints(target)
                + "（已分配 " + PlayerDataManager.totalAllocated(target) + "）"), false);
        for (Map.Entry<String, Integer> e : PlayerDataManager.getAllAllocated(target).entrySet()) {
            if (e.getValue() <= 0) {
                continue;
            }
            src.sendSuccess(() -> Component.literal("  ")
                    .append(Component.literal(PlayerDataManager.displayName(e.getKey())).withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(" +" + e.getValue())), false);
        }
        return 1;
    }

    private static int skillAdd(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        int amount = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "amount");
        PlayerDataManager.grantPoints(target, amount);
        ctx.getSource().sendSuccess(() -> Component.literal("已为 " + target.getScoreboardName()
                + " 增加 " + amount + " 技能点"), true);
        return 1;
    }

    private static int skillSet(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        int amount = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "amount");
        PlayerDataManager.setPoints(target, amount);
        ctx.getSource().sendSuccess(() -> Component.literal("已将 " + target.getScoreboardName()
                + " 的技能点设置为 " + amount), true);
        return 1;
    }

    private static int skillResetAll(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        int refunded = PlayerDataManager.resetAll(target, false);
        ctx.getSource().sendSuccess(() -> Component.literal("已重置 " + target.getScoreboardName()
                + " 的全部加点（返还 " + refunded + " 技能点）"), true);
        return 1;
    }

    private static int skillResetAttr(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String attrId = StringArgumentType.getString(ctx, "attribute");
        int refunded = PlayerDataManager.resetAttribute(target, attrId);
        if (refunded <= 0) {
            ctx.getSource().sendFailure(Component.literal("该属性没有已分配点数或不存在：" + attrId
                    + "（格式：/monsterwaves skill reset <玩家> <属性>，无需数量参数）"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("已重置 " + target.getScoreboardName() + " 的 "
                + PlayerDataManager.displayName(attrId) + "（返还 " + refunded + " 技能点）"), true);
        return 1;
    }

    /** 打开加点界面（发送 S2C 包，由客户端渲染） */
    private static int skillGui(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        NetworkHandler.sendTo(player, new S2COpenGui());
        return 1;
    }

    /** 重新加载 JSON 配置并重新应用全部在线玩家属性（改完 config/monsterwaves.json 后无需重启） */
    private static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
        try {
            me.shedaniel.autoconfig.AutoConfig.getConfigHolder(MWConfig.class).load();
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("配置重载失败：" + e.getMessage()));
            return 0;
        }
        for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            PlayerDataManager.applyAll(p);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("配置已重载，全部玩家属性已重新应用（白名单外分配已清理并返还技能点）"), true);
        return 1;
    }

    /** 将当前内存配置（含 GUI 已修改但未点保存的值）写入 config/monsterwaves.json */
    private static int configSave(CommandContext<CommandSourceStack> ctx) {
        try {
            me.shedaniel.autoconfig.AutoConfig.getConfigHolder(MWConfig.class).save();
            ctx.getSource().sendSuccess(() -> Component.literal("配置已保存到 config/monsterwaves.json（重启后生效）"), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("配置保存失败：" + e.getMessage()));
            return 0;
        }
    }

    private static int difficulty(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        StageManager.Stage stage = StageManager.getData(level.getServer()).currentStage();
        ctx.getSource().sendSuccess(() -> Component.literal("当前阶段：")
                .append(Component.literal(stage.id()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" ｜ 难度系数：x" + stage.difficulty())), false);
        return 1;
    }

    /** 传送至休息维度（管理员指令，无冷却） */
    private static int safe(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        return SafeDimensionManager.teleportToSafe(player) ? 1 : 0;
    }

    /** 传送至刷怪维度（管理员指令） */
    private static int battle(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        return ArenaDimensionManager.teleportToArena(player) ? 1 : 0;
    }

    /** 返回主世界（全员）：优先玩家重生点（床），否则主世界出生点；从安全/战斗维度返回的常规手段 */
    private static int leave(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        return SafeDimensionManager.teleportToSpawn(player) ? 1 : 0;
    }

    /** 重置并重建空岛（调试/刷新景观用） */
    private static int safeReset(CommandContext<CommandSourceStack> ctx) {
        ServerLevel safeLevel = SafeDimensionManager.getSafeLevel(ctx.getSource().getServer());
        if (safeLevel == null) {
            ctx.getSource().sendFailure(Component.literal("休息维度不可用"));
            return 0;
        }
        SafeDimensionManager.resetIsland(safeLevel);
        ctx.getSource().sendSuccess(() -> Component.literal("空岛已重置重建"), true);
        return 1;
    }

    private static int stageInfo(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        StageData data = StageManager.getData(level.getServer());
        StageManager.Stage stage = data.currentStage();
        long remain = stage.isInfinite() ? -1 : Math.max(0, stage.durationTicks() - data.getTimer());
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.literal("当前阶段 [").append(String.valueOf(data.getIndex()))
                .append(Component.literal("] ")).append(stage.id()).withStyle(ChatFormatting.GOLD), false);
        src.sendSuccess(() -> Component.literal("难度系数：x" + stage.difficulty()), false);
        src.sendSuccess(() -> Component.literal(remain < 0
                ? "持续时间：无限"
                : "距下一阶段：" + (remain / 20) + " 秒"), false);
        return 1;
    }

    private static int stageNext(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        StageManager.getData(server).next();
        StageManager.broadcastSwitch(server, "手动切换");
        return 1;
    }

    private static int stagePrev(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        StageManager.getData(server).prev();
        StageManager.broadcastSwitch(server, "手动切换");
        return 1;
    }

    private static int stageSet(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        MinecraftServer server = ctx.getSource().getServer();
        StageData data = StageManager.getData(server);
        java.util.List<StageManager.Stage> stages = StageManager.getStages();
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).id().equals(id)) {
                data.setStage(i);
                StageManager.broadcastSwitch(server, "手动切换");
                return 1;
            }
        }
        ctx.getSource().sendFailure(Component.literal("未找到阶段：" + id + "，可用阶段：" + stageIds()));
        return 0;
    }

    private static String stageIds() {
        return String.join("、", StageManager.getStages().stream().map(StageManager.Stage::id).toList());
    }

    private static int playerList(CommandContext<CommandSourceStack> ctx) {
        var players = ctx.getSource().getServer().getPlayerList().getPlayers();
        ctx.getSource().sendSuccess(() -> Component.literal("在线玩家（" + players.size() + "）：")
                .append(Component.literal(players.stream().map(ServerPlayer::getScoreboardName).toList().toString())), false);
        return 1;
    }
}
