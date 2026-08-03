package com.mcmod.monsterwaves.event;

import com.mcmod.monsterwaves.config.MWConfig;
import com.mcmod.monsterwaves.data.PlayerDataManager;
import com.mcmod.monsterwaves.item.AttributeBallItem;
import com.mcmod.monsterwaves.item.ModItems;
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
import net.minecraftforge.event.entity.player.PlayerEvent;
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

    /** 属性球吸附范围（格），参考经验球机制：范围内自动飞向玩家 */
    private static final double BALL_ATTRACT_RANGE = 3.0;
    /** 属性球吸收判定：球体膨胀半径（格），与玩家包围盒相交即吸收（经验球式接触判定） */
    private static final double BALL_PICKUP_INFLATE = 0.5;
    /** 属性球重力加速度（经验球为 0.04 格/tick²，用于抵消上飘并自然下落） */
    private static final double BALL_GRAVITY = 0.04;
    /** 属性球出生后不被吸引的时长（tick，经验球为 10 = 0.5 秒，防出生即吸） */
    private static final int BALL_ATTRACT_DELAY = 10;
    /** 属性球已被处理的标记（防同一 tick 内多玩家重复拾取） */
    private static final String BALL_CLAIMED = "monsterwaves_ball_claimed";

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        StageManager.serverTick(server);
        for (ServerLevel level : server.getAllLevels()) {
            MobSpawnManager.serverTick(level);
            pickupBalls(level);
        }
    }

    /**
     * 属性球采用经验球式机制（服务端每 tick 处理）：
     * - 出生 0.5 秒内只受重力自由下落，不被吸引
     * - 进入吸附范围（3 格）后按距离衰减地加速飞向玩家（胸口高度）
     * - 球体接触玩家包围盒（AABB 相交）即吸收，永不进入背包
     * （Forge 1.20.1 无拾取前事件，且自定义实体留待后续阶段，此方案最贴近经验球体验）
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
     * 经验球式移动（对齐原版 ExperienceOrb.tick 逻辑）：
     * 重力 + 空气阻力 + 向玩家加速（位移归一化到吸引范围，越近加速度越大），
     * 而非直接设置速度，避免球无限上飘；目标高度为玩家胸口（eyeHeight/2）。
     */
    private static void attractBall(ItemEntity ball, ServerPlayer player) {
        Vec3 motion = ball.getDeltaMovement();

        // 出生延迟：0.5 秒内只受重力下落，不被吸引
        if (ball.tickCount < BALL_ATTRACT_DELAY) {
            motion = motion.add(0.0, -BALL_GRAVITY, 0.0).multiply(0.98, 0.98, 0.98);
            ball.setDeltaMovement(motion);
            ball.hasImpulse = true;
            return;
        }

        // 重力 + 空气阻力（经验球：每 tick 减 0.04，速度乘 0.98）
        motion = motion.add(0.0, -BALL_GRAVITY, 0.0).multiply(0.98, 0.98, 0.98);

        // 向玩家胸口加速：位移归一化到吸引范围，d12=1-归一化距离，越近加速度越大
        Vec3 toPlayer = new Vec3(
                player.getX() - ball.getX(),
                player.getY() + player.getEyeHeight() * 0.5 - ball.getY(),
                player.getZ() - ball.getZ());
        double dist = toPlayer.length();
        if (dist > 0.01) {
            Vec3 dir = toPlayer.scale(1.0 / dist);
            double d12 = 1.0 - dist / BALL_ATTRACT_RANGE;
            if (d12 > 0.0) {
                double accel = d12 * d12 * 0.1;
                motion = motion.add(dir.x * accel, dir.y * accel, dir.z * accel);
            }
        }

        ball.setDeltaMovement(motion);
        ball.hasImpulse = true; // 强制将速度同步到客户端，保证渲染跟随
    }

    private static void applyBall(ServerPlayer player, ItemEntity ball) {
        if (ball.getPersistentData().getBoolean(BALL_CLAIMED)) {
            return;
        }
        ball.getPersistentData().putBoolean(BALL_CLAIMED, true);
        AttributeBallItem ballItem = (AttributeBallItem) ball.getItem().getItem();
        PlayerDataManager.add(player, ballItem.getAttributeType(), MWConfig.BALL_VALUE);
        int total = PlayerDataManager.get(player, ballItem.getAttributeType());
        player.displayClientMessage(
                Component.literal("你获得了 +" + MWConfig.BALL_VALUE + " ")
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
     * 由 MWConfig.DROP_BALLS_FROM_ALL_MOBS 控制；关闭时仅本mod生成的怪（带标记）触发。
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
        if (!MWConfig.DROP_BALLS_FROM_ALL_MOBS
                && !entity.getPersistentData().getBoolean(MobSpawnManager.MARKER)) {
            return; // 开关关闭：仅本mod生成的怪掉落
        }
        ServerLevel level = (ServerLevel) entity.level();
        double difficulty = StageManager.getDifficulty(level.getServer());
        double chance = Math.min(1.0, MWConfig.BALL_BASE_CHANCE * difficulty);

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
            type = MWConfig.BALL_TYPES[entity.getRandom().nextInt(MWConfig.BALL_TYPES.length)];
        }

        // 生成属性球（数量按事件参数，至少 1）
        int count = Math.max(1, dropEvent.getBallCount());
        for (int i = 0; i < count; i++) {
            spawnBall(level, dropEvent.getDropPos(), type);
        }
    }

    private static boolean isValidBallType(String type) {
        for (String t : MWConfig.BALL_TYPES) {
            if (t.equals(type)) {
                return true;
            }
        }
        return false;
    }

    private static void spawnBall(ServerLevel level, Vec3 pos, String type) {
        ItemStack stack = new ItemStack(ModItems.getBall(type));
        ItemEntity ball = new ItemEntity(level, pos.x, pos.y, pos.z, stack);
        // 属性球永不进入背包，只由本模组的接触检测处理；
        // 关闭 ItemEntity 自带重力，由服务端按经验球式逻辑模拟重力与吸附
        ball.setNeverPickUp();
        ball.setNoGravity(true);
        level.addFreshEntity(ball);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        com.mcmod.monsterwaves.command.MonsterWavesCommand.register(event.getDispatcher());
    }
}
