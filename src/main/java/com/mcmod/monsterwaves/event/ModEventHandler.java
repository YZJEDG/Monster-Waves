package com.mcmod.monsterwaves.event;

import com.mcmod.monsterwaves.config.MWConfig;
import com.mcmod.monsterwaves.data.PlayerDataManager;
import com.mcmod.monsterwaves.item.AttributeBallItem;
import com.mcmod.monsterwaves.item.ModItems;
import com.mcmod.monsterwaves.safe.SafeDimensionManager;
import com.mcmod.monsterwaves.spawn.MobSpawnManager;
import com.mcmod.monsterwaves.stage.StageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * 服务端事件监听（MVP）：
 * - Tick：阶段推进 + 生成引擎 + 属性球接触拾取
 * - 玩家登录/重生/换维度：重新应用属性 modifier
 * - 击杀本mod生成的怪：按难度概率掉落属性球
 * - 注册 /monsterwaves 指令
 *
 * 属性球拾取采用服务端 tick 接触检测（Forge 1.20.1 无拾取前事件，
 * PlayerEvent.ItemPickupEvent 为拾取后且不可取消；接触检测同样可靠且便于后续升级为实体）。
 */
public final class ModEventHandler {

    /** 属性球吸附范围（格），范围内被水平拉向玩家 */
    private static final double BALL_ATTRACT_RANGE = 3.0;
    /** 属性球吸收判定：球体膨胀半径（格），与玩家包围盒相交即吸收 */
    private static final double BALL_PICKUP_INFLATE = 0.5;
    /** 属性球拉向玩家的水平速度（格/tick） */
    private static final double BALL_FLY_SPEED = 0.35;
    /** 属性球垂直速度上限（格/tick），防止球飞起 */
    private static final double BALL_MAX_VY = 0.2;
    /** 属性球已被处理的标记（防同一 tick 内多玩家重复拾取） */
    private static final String BALL_CLAIMED = "monsterwaves_ball_claimed";

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        StageManager.serverTick(server);
        // 休息维度玩家规则：锁饥饿 + 跳下传送
        ServerLevel safeLevel = SafeDimensionManager.getSafeLevel(server);
        if (safeLevel != null) {
            // 先收集待传送玩家，遍历结束后统一传送（避免遍历 players() 时传送修改列表触发 CME）
            java.util.List<ServerPlayer> toFall = new java.util.ArrayList<>();
            for (ServerPlayer p : safeLevel.players()) {
                SafeDimensionManager.applySafeRules(p);
                if (p.getY() < MWConfig.get().fallTeleportY) {
                    toFall.add(p);
                }
            }
            for (ServerPlayer p : toFall) {
                SafeDimensionManager.handleFall(p);
            }
        }
        for (ServerLevel level : server.getAllLevels()) {
            MobSpawnManager.serverTick(level);
            pickupBalls(level);
        }
    }

    /**
     * 属性球机制（强制落地版）：
     * - 保留 ItemEntity 原生重力：球自然下落、落地静止，永不飘空
     * - 玩家 3 格内：以水平拉取为主滑向玩家（垂直最多小幅调整），接触玩家包围盒即吸收
     * - 永不进入背包
     */
    private static void pickupBalls(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            List<ItemEntity> balls = level.getEntitiesOfClass(ItemEntity.class,
                    player.getBoundingBox().inflate(BALL_ATTRACT_RANGE),
                    e -> e.getItem().getItem() instanceof AttributeBallItem);
            for (ItemEntity ball : balls) {
                if (ball.getPersistentData().getBoolean(BALL_CLAIMED)) {
                    continue;
                }
                if (ball.getBoundingBox().inflate(BALL_PICKUP_INFLATE)
                        .intersects(player.getBoundingBox())) {
                    applyBall(player, ball);
                } else {
                    attractBall(ball, player);
                }
            }
        }
    }

    /**
     * 将球水平拉向玩家（垂直仅小幅调整，上限 BALL_MAX_VY，避免飞起）。
     * 超出吸附范围时完全不干预，球靠重力自然落地。
     */
    private static void attractBall(ItemEntity ball, ServerPlayer player) {
        Vec3 toPlayer = new Vec3(
                player.getX() - ball.getX(),
                player.getY() + 0.5 - ball.getY(),
                player.getZ() - ball.getZ());
        double dist = toPlayer.length();
        if (dist <= 0.1 || dist > BALL_ATTRACT_RANGE) {
            return; // 太近（将吸收）或超出范围：不干预
        }
        Vec3 dir = toPlayer.scale(1.0 / dist);
        double vy = Math.max(-0.3, Math.min(dir.y * BALL_FLY_SPEED, BALL_MAX_VY));
        ball.setDeltaMovement(dir.x * BALL_FLY_SPEED, vy, dir.z * BALL_FLY_SPEED);
        ball.hasImpulse = true; // 速度同步到客户端，渲染跟随
    }

    private static void applyBall(ServerPlayer player, ItemEntity ball) {
        if (ball.getPersistentData().getBoolean(BALL_CLAIMED)) {
            return;
        }
        ball.getPersistentData().putBoolean(BALL_CLAIMED, true);
        AttributeBallItem ballItem = (AttributeBallItem) ball.getItem().getItem();
        int value = MWConfig.get().ballValue;
        PlayerDataManager.add(player, ballItem.getAttributeType(), value);
        int total = PlayerDataManager.get(player, ballItem.getAttributeType());
        player.displayClientMessage(
                Component.literal("你获得了 +" + value + " ")
                        .append(PlayerDataManager.attributeDisplayName(ballItem.getAttributeType()))
                        .append(Component.literal("！当前累计：" + total))
                        .withStyle(ChatFormatting.GREEN), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.0f);
        ball.discard();
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        PlayerDataManager.applyAll(event.getEntity());
        // 开局给予返回符咒（giveOnJoin）
        if (event.getEntity() instanceof ServerPlayer sp && MWConfig.get().giveOnJoin) {
            boolean has = sp.getInventory().hasAnyMatching(s -> s.getItem() == ModItems.RETURN_CHARM.get());
            if (!has) {
                sp.getInventory().add(new ItemStack(ModItems.RETURN_CHARM.get()));
            }
        }
    }

    /** 休息维度免疫一切伤害 */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && SafeDimensionManager.isSafe(p.level())) {
            event.setCanceled(true);
        }
    }

    /** 休息维度不生成任何生物（本模组及原版） */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCheckSpawn(MobSpawnEvent.PositionCheck event) {
        if (SafeDimensionManager.isSafe(event.getLevel())) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        PlayerDataManager.applyAll(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        PlayerDataManager.applyAll(event.getEntity());
    }

    /**
     * 怪物死亡触发属性球掉落：
     * 1. 发布 {@link AttributeBallDropEvent}（可取消/修改概率、类型、数量）
     * 2. 未取消则按事件参数判定概率并生成属性球
     * 适用范围：敌对怪物（Monster）。默认所有敌对怪物（含非本mod生成的）都触发，
     * 由配置 dropBallsFromAllMobs 控制；关闭时仅本mod生成的怪（带标记）触发。
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        var entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        if (!(entity instanceof Monster)) {
            return; // 仅敌对怪物参与掉落
        }
        if (!MWConfig.get().dropBallsFromAllMobs
                && !entity.getPersistentData().getBoolean(MobSpawnManager.MARKER)) {
            return; // 开关关闭：仅本mod生成的怪掉落
        }
        ServerLevel level = (ServerLevel) entity.level();
        double difficulty = StageManager.getDifficulty(level.getServer());
        double chance = Math.min(1.0, MWConfig.get().ballBaseChance * difficulty);

        // 发布掉落触发事件：怪物被杀死即触发掉落机制
        AttributeBallDropEvent dropEvent = new AttributeBallDropEvent(entity, level,
                new Vec3(entity.getX(), entity.getY() + 0.5, entity.getZ()),
                chance, null, 1);
        if (MinecraftForge.EVENT_BUS.post(dropEvent)) {
            return; // 事件被取消：本次不掉落
        }

        // 概率判定
        if (entity.getRandom().nextDouble() >= dropEvent.getChance()) {
            return;
        }

        // 属性类型：事件指定（须合法）或随机
        String type = dropEvent.getAttributeType();
        if (type == null || !isValidBallType(type)) {
            java.util.List<String> types = MWConfig.get().ballTypes;
            type = types.get(entity.getRandom().nextInt(types.size()));
        }

        // 生成属性球（数量按事件参数，至少 1）
        int count = Math.max(1, dropEvent.getBallCount());
        for (int i = 0; i < count; i++) {
            spawnBall(level, dropEvent.getDropPos(), type);
        }
    }

    private static boolean isValidBallType(String type) {
        return MWConfig.get().ballTypes.contains(type);
    }

    private static void spawnBall(ServerLevel level, Vec3 pos, String type) {
        ItemStack stack = new ItemStack(ModItems.getBall(type));
        ItemEntity ball = new ItemEntity(level, pos.x, pos.y, pos.z, stack);
        // 属性球永不进入背包；保留原生重力，球自然落地不飘空
        ball.setNeverPickUp();
        level.addFreshEntity(ball);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        com.mcmod.monsterwaves.command.MonsterWavesCommand.register(event.getDispatcher());
    }
}
