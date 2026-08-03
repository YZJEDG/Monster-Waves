package com.mcmod.monsterwaves.event;

import com.mcmod.monsterwaves.MonsterWavesMod;
import com.mcmod.monsterwaves.arena.ArenaDimensionManager;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;
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

    /** 客户端轮询：配置界面中"传送到重生点"开关变化时立即重建界面（实时条件显示）；首次 tick 同步实际值 */
    private static boolean lastFallToRespawn = true;

    /** 客户端 tick：检测配置开关变化并实时重建 Cloth Config 界面 */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen == null) {
            lastFallToRespawn = MWConfig.get().fallToRespawnPoint;
            return;
        }
        if (mc.screen instanceof me.shedaniel.clothconfig2.gui.ClothConfigScreen
                && MWConfig.get().fallToRespawnPoint != lastFallToRespawn) {
            lastFallToRespawn = MWConfig.get().fallToRespawnPoint;
            mc.setScreen(me.shedaniel.autoconfig.AutoConfig.getConfigScreen(
                    MWConfig.class, mc.screen).get());
        }
    }

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
            cleanupBalls(level);
        }
    }

    /**
     * 属性球/掉落物清理（防堆积）：
     * - 按配置的**物品名名单**匹配要清理的掉落物（不限于本模组属性球）
     * - **追踪时间**（实体存在 tick）：超时清理
     * - 超上限时按时间升序清理**最早的一批**；cleanupAutoAttract=true 时先尝试吸向最近玩家
     */
    private static void cleanupBalls(ServerLevel level) {
        MWConfig cfg = MWConfig.get();
        if (!cfg.cleanupEnable) {
            return;
        }
        if (level.getServer().getTickCount() % Math.max(1, cfg.cleanupInterval) != 0) {
            return;
        }
        java.util.List<String> names = cfg.cleanupItemNames;
        if (names == null || names.isEmpty()) {
            return;
        }
        java.util.List<ItemEntity> balls = level.getEntitiesOfClass(ItemEntity.class,
                new net.minecraft.world.phys.AABB(-3.0E7, -3.0E7, -3.0E7, 3.0E7, 3.0E7, 3.0E7),
                e -> names.contains(net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getKey(e.getItem().getItem()).toString()));
        if (balls.isEmpty()) {
            return;
        }
        // 追踪时间：超时清理
        balls.removeIf(b -> {
            if (b.tickCount > cfg.cleanupDespawnTime) {
                b.discard();
                return true;
            }
            return false;
        });
        // 数量上限：按存在时间升序，清理最早的一批
        if (balls.size() > cfg.cleanupMaxCount) {
            balls.sort(java.util.Comparator.comparingInt(b -> b.tickCount));
            int toRemove = balls.size() - cfg.cleanupMaxCount;
            for (int i = 0; i < toRemove; i++) {
                ItemEntity ball = balls.get(i);
                if (cfg.cleanupAutoAttract) {
                    // 尝试吸向最近玩家（3 格内），否则移除
                    var nearest = level.getNearestPlayer(ball, 3.0);
                    if (nearest != null) {
                        continue;
                    }
                }
                ball.discard();
            }
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
        // 开局给予返回符咒与战斗符咒（各自 giveOnJoin 开关）
        if (event.getEntity() instanceof ServerPlayer sp) {
            MWConfig cfg = MWConfig.get();
            if (cfg.giveOnJoin) {
                boolean hasReturn = sp.getInventory().hasAnyMatching(s -> s.getItem() == ModItems.RETURN_CHARM.get());
                if (!hasReturn) {
                    sp.getInventory().add(new ItemStack(ModItems.RETURN_CHARM.get()));
                }
            }
            if (cfg.battleCharmGiveOnJoin) {
                boolean hasBattle = sp.getInventory().hasAnyMatching(s -> s.getItem() == ModItems.BATTLE_CHARM.get());
                if (!hasBattle) {
                    sp.getInventory().add(new ItemStack(ModItems.BATTLE_CHARM.get()));
                }
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

    /**
     * 休息维度与刷怪维度禁止**原版自然生成**（仅 NATURAL 类型拦截；
     * 本模组 MOB_SUMMONED 生成不受影响，否则会连本模组刷怪一起被 DENY）
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCheckSpawn(MobSpawnEvent.PositionCheck event) {
        if (event.getSpawnType() == MobSpawnType.NATURAL
                && (SafeDimensionManager.isSafe(event.getLevel()) || ArenaDimensionManager.isArena(event.getLevel()))) {
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
        MWConfig cfg = MWConfig.get();

        // 属性球掉落（可开关）
        if (cfg.ballDropEnabled) {
            double chance = Math.min(1.0, cfg.ballBaseChance * difficulty);
            // 发布掉落触发事件：怪物被杀死即触发掉落机制
            AttributeBallDropEvent dropEvent = new AttributeBallDropEvent(entity, level,
                    new Vec3(entity.getX(), entity.getY() + 0.5, entity.getZ()),
                    chance, null, 1);
            if (!MinecraftForge.EVENT_BUS.post(dropEvent)) {
                // 概率判定
                if (entity.getRandom().nextDouble() < dropEvent.getChance()) {
                    // 属性类型：事件指定（须合法）或随机
                    String type = dropEvent.getAttributeType();
                    if (type == null || !isValidBallType(type)) {
                        java.util.List<String> types = cfg.ballTypes;
                        type = types.get(entity.getRandom().nextInt(types.size()));
                    }
                    // 生成属性球（数量按事件参数，至少 1）
                    int count = Math.max(1, dropEvent.getBallCount());
                    for (int i = 0; i < count; i++) {
                        spawnBall(level, dropEvent.getDropPos(), type);
                    }
                }
            }
        }

        // 统一掉落（与属性球并行，不受属性球开关影响）
        dropLoot(entity, level, difficulty);
    }

    /** 统一掉落：按配置 normalLoot 生成掉落物（概率/数量受难度影响） */
    private static void dropLoot(LivingEntity entity, ServerLevel level, double difficulty) {
        MWConfig cfg = MWConfig.get();
        if (!cfg.lootEnabled || cfg.normalLoot.isEmpty()) {
            return;
        }
        for (MWConfig.LootEntry entry : cfg.normalLoot) {
            double chance = Math.min(1.0, entry.chance * difficulty * cfg.lootGlobalChanceMultiplier);
            if (entity.getRandom().nextDouble() >= chance) {
                continue;
            }
            int min = Math.max(1, entry.minCount);
            int max = Math.max(min, entry.maxCount);
            int count = min + entity.getRandom().nextInt(max - min + 1);
            count += (int) Math.floor(cfg.lootExtraCountPerLevel * (difficulty - 1));
            Item item = ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.tryParse(entry.item));
            if (item == null) {
                MonsterWavesMod.LOGGER.warn("MW 掉落物品不存在：{}", entry.item);
                continue;
            }
            ItemStack stack = new ItemStack(item, Math.max(1, count));
            if (entry.nbt != null && !entry.nbt.isEmpty()) {
                try {
                    stack.setTag(TagParser.parseTag(entry.nbt));
                } catch (Exception e) {
                    MonsterWavesMod.LOGGER.warn("MW 掉落 NBT 解析失败：{}（{}）", entry.nbt, entry.item);
                }
            }
            level.addFreshEntity(new ItemEntity(level, entity.getX(), entity.getY() + 0.5, entity.getZ(), stack));
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
