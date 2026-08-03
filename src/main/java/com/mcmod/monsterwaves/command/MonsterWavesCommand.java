package com.mcmod.monsterwaves.command;

import com.mcmod.monsterwaves.arena.ArenaDimensionManager;
import com.mcmod.monsterwaves.config.MWConfig;
import com.mcmod.monsterwaves.data.PlayerDataManager;
import com.mcmod.monsterwaves.item.ModItems;
import com.mcmod.monsterwaves.spawn.MobSpawnManager;
import com.mcmod.monsterwaves.stage.StageData;
import com.mcmod.monsterwaves.stage.StageManager;
import com.mcmod.monsterwaves.safe.SafeDimensionManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import com.mojang.brigadier.arguments.StringArgumentType;
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
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Arrays;

/**
 * /monsterwaves 基础指令（MVP）：
 * - spawn <生物> [数量]     管理员：指定位置生成怪物（带难度应用与掉落标记）
 * - stats [玩家]            查看玩家属性累计值
 * - difficulty              查看当前阶段与难度系数
 * - stage info|next|prev|set <id>   阶段管理
 * - ball give <玩家> <类型> <数量>  直接给予属性（测试用）
 */
public final class MonsterWavesCommand {
    private MonsterWavesCommand() {
    }

    /** Tab 补全：已注册实体（过滤非生物类 MISC 实体，如物品/箭/船） */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_MOBS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    ForgeRegistries.ENTITY_TYPES.getKeys().stream()
                            .filter(rl -> {
                                EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(rl);
                                return type != null && type.getCategory() != MobCategory.MISC;
                            })
                            .map(ResourceLocation::toString)
                            .sorted(),
                    builder);

    /** Tab 补全：可用阶段 id */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_STAGE_IDS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    StageManager.getStages().stream().map(StageManager.Stage::id).toList(), builder);

    /** Tab 补全：属性球类型 */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_BALL_TYPES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(MWConfig.get().ballTypes, builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("monsterwaves")
                .then(Commands.literal("spawn")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("mob", ResourceLocationArgument.id()).suggests(SUGGEST_MOBS)
                                .executes(ctx -> spawn(ctx, 1))
                                .then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 100))
                                        .executes(ctx -> spawn(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "count"))))))
                .then(Commands.literal("stats")
                        .executes(ctx -> stats(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
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
                .then(Commands.literal("stage")
                        .then(Commands.literal("info").executes(MonsterWavesCommand::stageInfo))
                        .then(Commands.literal("next").requires(src -> src.hasPermission(2))
                                .executes(MonsterWavesCommand::stageNext))
                        .then(Commands.literal("prev").requires(src -> src.hasPermission(2))
                                .executes(MonsterWavesCommand::stagePrev))
                        .then(Commands.literal("set").requires(src -> src.hasPermission(2))
                                .then(Commands.argument("id", StringArgumentType.greedyString()).suggests(SUGGEST_STAGE_IDS)
                                        .executes(MonsterWavesCommand::stageSet))))
                .then(Commands.literal("ball")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("give")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("type", AttributeTypeArgument.type()).suggests(SUGGEST_BALL_TYPES)
                                                .then(Commands.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 999))
                                                        .executes(MonsterWavesCommand::ballGive)))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("type", AttributeTypeArgument.type()).suggests(SUGGEST_BALL_TYPES)
                                                .then(Commands.argument("value", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 99999))
                                                        .executes(MonsterWavesCommand::ballSet))))))
                .then(Commands.literal("player")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("type", AttributeTypeArgument.type()).suggests(SUGGEST_BALL_TYPES)
                                                .then(Commands.argument("value", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 99999))
                                                        .executes(MonsterWavesCommand::playerAdd)))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("type", AttributeTypeArgument.type()).suggests(SUGGEST_BALL_TYPES)
                                                .then(Commands.argument("value", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 99999))
                                                        .executes(MonsterWavesCommand::playerSet)))))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(MonsterWavesCommand::playerReset)))
                        .then(Commands.literal("list")
                                .executes(MonsterWavesCommand::playerList)))
        );
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, int count) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ResourceLocation rl = ResourceLocationArgument.getId(ctx, "mob");
        String mobId = rl.toString();
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(rl);
        if (type == null) {
            ctx.getSource().sendFailure(Component.literal("未知生物：" + mobId + "（格式：minecraft:zombie）"));
            return 0;
        }
        ServerLevel level = ctx.getSource().getLevel();
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        double difficulty = StageManager.getDifficulty(level.getServer());
        StageManager.Stage stage = StageManager.getData(level.getServer()).currentStage();
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            Entity entity = type.spawn(level,
                    player.blockPosition().offset(level.getRandom().nextInt(5) - 2, 0,
                            level.getRandom().nextInt(5) - 2),
                    MobSpawnType.COMMAND);
            if (entity instanceof Mob mob) {
                mob.getPersistentData().putBoolean(MobSpawnManager.MARKER, true);
                MobSpawnManager.applyDifficultyTo(mob, difficulty, stage);
                spawned++;
            } else if (entity != null) {
                // 非 Mob 实体（如物品/经验球）不适用本模组流程，直接移除
                entity.discard();
            }
        }
        int finalSpawned = spawned;
        ctx.getSource().sendSuccess(() -> Component.literal("已生成 " + finalSpawned + " 只 " + mobId), true);
        return finalSpawned;
    }

    private static int stats(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.literal("=== " + target.getScoreboardName() + " 的属性 ===")
                .withStyle(ChatFormatting.AQUA), false);
        for (String type : MWConfig.get().ballTypes) {
            int value = PlayerDataManager.get(target, type);
            src.sendSuccess(() -> PlayerDataManager.attributeDisplayName(type).copy()
                    .append(Component.literal("：+" + value)).withStyle(ChatFormatting.GREEN), false);
        }
        return 1;
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

    /** 校验属性类型是否合法，非法时向执行者报错并返回 false */
    private static boolean checkType(CommandContext<CommandSourceStack> ctx, String type) {
        java.util.List<String> types = MWConfig.get().ballTypes;
        if (!types.contains(type)) {
            ctx.getSource().sendFailure(Component.literal("未知属性类型：" + type
                    + "，可用：" + String.join(", ", types)));
            return false;
        }
        return true;
    }

    private static int ballGive(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String type = AttributeTypeArgument.getType(ctx, "type");
        int amount = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "amount");
        if (!checkType(ctx, type)) {
            return 0;
        }
        PlayerDataManager.add(target, type, amount);
        ctx.getSource().sendSuccess(() -> Component.literal("已给予 " + target.getScoreboardName() + " +" + amount + " ")
                .append(PlayerDataManager.attributeDisplayName(type)), true);
        return 1;
    }

    /** 直接设置玩家属性值（覆盖） */
    private static int ballSet(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String type = AttributeTypeArgument.getType(ctx, "type");
        int value = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "value");
        if (!checkType(ctx, type)) {
            return 0;
        }
        PlayerDataManager.set(target, type, value);
        ctx.getSource().sendSuccess(() -> Component.literal("已将 " + target.getScoreboardName() + " 的 ")
                .append(PlayerDataManager.attributeDisplayName(type))
                .append(Component.literal(" 设置为 " + value)), true);
        return 1;
    }

    private static int playerAdd(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String type = AttributeTypeArgument.getType(ctx, "type");
        int value = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "value");
        if (!checkType(ctx, type)) {
            return 0;
        }
        PlayerDataManager.add(target, type, value);
        ctx.getSource().sendSuccess(() -> Component.literal("已为 " + target.getScoreboardName() + " 增加 +" + value + " ")
                .append(PlayerDataManager.attributeDisplayName(type)), true);
        return 1;
    }

    private static int playerSet(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String type = AttributeTypeArgument.getType(ctx, "type");
        int value = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "value");
        if (!checkType(ctx, type)) {
            return 0;
        }
        PlayerDataManager.set(target, type, value);
        ctx.getSource().sendSuccess(() -> Component.literal("已将 " + target.getScoreboardName() + " 的 ")
                .append(PlayerDataManager.attributeDisplayName(type))
                .append(Component.literal(" 设置为 " + value)), true);
        return 1;
    }

    private static int playerReset(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        for (String type : MWConfig.get().ballTypes) {
            PlayerDataManager.set(target, type, 0);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("已重置 " + target.getScoreboardName() + " 的全部属性"), true);
        return 1;
    }

    private static int playerList(CommandContext<CommandSourceStack> ctx) {
        var players = ctx.getSource().getServer().getPlayerList().getPlayers();
        ctx.getSource().sendSuccess(() -> Component.literal("在线玩家（" + players.size() + "）：")
                .append(Component.literal(players.stream().map(ServerPlayer::getScoreboardName).toList().toString())), false);
        return 1;
    }
}
