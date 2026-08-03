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
import net.minecraft.world.item.ItemStack;
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

    /** 属性球接触检测间隔（tick） */
    private static final int BALL_PICKUP_INTERVAL = 5;
    /** 属性球吸附半径（格） */
    private static final double BALL_PICKUP_RADIUS = 1.5;

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

    private static void pickupBalls(ServerLevel level) {
        if (level.getServer().getTickCount() % BALL_PICKUP_INTERVAL != 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            List<ItemEntity> balls = level.getEntitiesOfClass(ItemEntity.class,
                    player.getBoundingBox().inflate(BALL_PICKUP_RADIUS),
                    e -> e.getItem().getItem() instanceof AttributeBallItem);
            for (ItemEntity ball : balls) {
                applyBall(player, ball);
            }
        }
    }

    private static void applyBall(ServerPlayer player, ItemEntity ball) {
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

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        var entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        if (!entity.getPersistentData().getBoolean(MobSpawnManager.MARKER)) {
            return;
        }
        ServerLevel level = (ServerLevel) entity.level();
        double difficulty = StageManager.getDifficulty(level.getServer());
        double chance = Math.min(1.0, MWConfig.BALL_BASE_CHANCE * difficulty);
        if (entity.getRandom().nextDouble() >= chance) {
            return;
        }
        String type = MWConfig.BALL_TYPES[entity.getRandom().nextInt(MWConfig.BALL_TYPES.length)];
        ItemStack stack = new ItemStack(ModItems.getBall(type));
        level.addFreshEntity(new ItemEntity(level,
                entity.getX(), entity.getY() + 0.5, entity.getZ(), stack));
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        com.mcmod.monsterwaves.command.MonsterWavesCommand.register(event.getDispatcher());
    }
}
