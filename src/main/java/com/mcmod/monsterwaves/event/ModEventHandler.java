package com.mcmod.monsterwaves.event;

import com.mcmod.monsterwaves.MonsterWavesMod;
import com.mcmod.monsterwaves.arena.ArenaDimensionManager;
import com.mcmod.monsterwaves.config.MWConfig;
import com.mcmod.monsterwaves.data.PlayerDataManager;
import com.mcmod.monsterwaves.item.ModItems;
import com.mcmod.monsterwaves.mob.EliteBossHandler;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.TagParser;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * 服务端事件监听（v9.0）：
 * - Tick：阶段推进 + 生成引擎 + 大范围拾取 + 休息维度规则
 * - 玩家登录/重生/换维度：重新应用技能点属性 modifier
 * - 击杀敌对怪物：统一掉落 + 经验加成（替代原属性球掉落）
 * - 注册 /monsterwaves 指令
 */
public final class ModEventHandler {

    /** 状态播报计数器（每 statusNoticeInterval tick 一次聊天栏提示） */
    private static int statusTicker = 0;

    // v1.0.3 拾取黑名单缓存（配置实例变化时重建，避免逐物品 List.contains 线性扫描）
    private static java.util.Set<String> pickupBlacklistCache = null;
    private static Object pickupCfgRef = null;

    /** 掉落物归属标记（击杀者 UUID，供 onlyOwnDrops 使用） */
    private static final String DROP_OWNER = "monsterwaves_owner";

    /** 客户端轮询：配置界面中"传送到重生点"开关变化时立即重建界面（实时条件显示）；首次 tick 同步实际值（v1.4 恢复 GUI） */
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

    /** 聊天栏状态播报：向启用生成引擎维度内的玩家提示当前阶段/难度（替代 HUD 界面，默认每 30 秒） */
    private static void broadcastStatus(MinecraftServer server) {
        if (!MWConfig.get().statusNoticeEnabled) {
            return;
        }
        var stage = StageManager.getData(server).currentStage();
        double diff = StageManager.getDifficulty(server);
        net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component.literal(
                "§e【怪物狂潮】§r 当前阶段: §b" + stage.id() + "§r 难度: §c"
                        + String.format("%.1f", diff) + "§r" + (stage.isInfinite() ? " §7(无限)" : ""));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            // v1.0.3 单层遍历（替代 维度×玩家 双层），阶段/难度值上方已取一次
            if (com.mcmod.monsterwaves.spawn.MobSpawnManager.isDimensionEnabled(p.serverLevel())) {
                p.displayClientMessage(msg, false);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        StageManager.serverTick(server);
        // Boss 血条进度更新/清理
        com.mcmod.monsterwaves.mob.BossManager.tick(server);
        // 怪物传送（防溢出，仅 mod 维度）
        com.mcmod.monsterwaves.mob.MobTeleportHandler.tick(server);
        // 状态播报：聊天栏每 statusNoticeInterval tick 提示阶段/难度（替代 HUD 界面）
        if (++statusTicker % Math.max(1, MWConfig.get().statusNoticeInterval) == 0) {
            broadcastStatus(server);
        }
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
            pickupAround(level);
        }
    }

    /** 周期兜底：每 5 秒检查在线玩家是否有白名单外分配（配置移出白名单后自动清理并返还，无需重进） */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer p)) {
            return;
        }
        if (p.level().getGameTime() % 100 != 0) {
            return;
        }
        PlayerDataManager.cleanupOutOfWhitelist(p);
    }

    /**
     * 大范围拾取：自动拾取玩家周围掉落物入背包，并把经验球拉向玩家（接触自动吸收）。
     * - 黑名单过滤；onlyOwnDrops 时仅拾取归属自己的掉落（DROP_OWNER 标记）
     */
    private static void pickupAround(ServerLevel level) {
        MWConfig cfg = MWConfig.get();
        if (!cfg.pickupEnable) {
            return;
        }
        if (level.getServer().getTickCount() % Math.max(1, cfg.pickupInterval) != 0) {
            return;
        }
        double range = Math.max(1.0, cfg.pickupRange);
        // v1.0.3 黑名单转 HashSet 缓存（配置实例变化才重建）
        if (pickupBlacklistCache == null || pickupCfgRef != cfg) {
            pickupCfgRef = cfg;
            pickupBlacklistCache = cfg.pickupBlacklist == null
                    ? java.util.Set.of()
                    : new java.util.HashSet<>(cfg.pickupBlacklist);
        }
        for (ServerPlayer player : level.players()) {
            // v1.0.3 合并为一次实体查询（少一次 section 遍历），按类型分流
            java.util.List<net.minecraft.world.entity.Entity> entities =
                    level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
                            player.getBoundingBox().inflate(range),
                            e -> (e instanceof net.minecraft.world.entity.ExperienceOrb && cfg.pickupXp)
                                    || (e instanceof ItemEntity && cfg.pickupItems));
            for (net.minecraft.world.entity.Entity entity : entities) {
                if (entity.isRemoved()) {
                    continue;
                }
                if (entity instanceof net.minecraft.world.entity.ExperienceOrb orb) {
                    // 经验球：拉向玩家（原版接触自动吸收经验），不受 onlyOwnDrops 限制
                    net.minecraft.world.phys.Vec3 dir = player.position().add(0, 0.8, 0)
                            .subtract(orb.position()).normalize();
                    orb.setDeltaMovement(dir.scale(0.6));
                    orb.hasImpulse = true; // 服务端速度同步到客户端
                    continue;
                }
                ItemEntity item = (ItemEntity) entity;
                String reg = ForgeRegistries.ITEMS.getKey(item.getItem().getItem()).toString();
                if (pickupBlacklistCache.contains(reg)) {
                    continue;
                }
                if (cfg.pickupOnlyOwnDrops) {
                    String owner = item.getPersistentData().getString(DROP_OWNER);
                    if (owner.isEmpty() || !owner.equals(player.getStringUUID())) {
                        continue;
                    }
                }
                ItemStack stack = item.getItem();
                if (player.getInventory().add(stack)) {
                    item.discard();
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2f, 1.0f);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        PlayerDataManager.applyAll(event.getEntity());
        // 开局给予休息符咒与战斗符咒（各自 giveOnJoin 开关）
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
            if (cfg.giveHomeCharmOnJoin) {
                boolean hasHome = sp.getInventory().hasAnyMatching(s -> s.getItem() == ModItems.HOME_CHARM.get());
                if (!hasHome) {
                    sp.getInventory().add(new ItemStack(ModItems.HOME_CHARM.get()));
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

    /** 怪物死亡：统一掉落 + 击杀者归属标记（v9.0 移除属性球掉落，经验加成由 LivingExperienceDropEvent 单独处理） */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        var entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        if (!(entity instanceof Monster)) {
            return; // 仅敌对怪物参与掉落
        }
        ServerLevel level = (ServerLevel) entity.level();
        double difficulty = StageManager.getDifficulty(level.getServer());
        // 击杀者归属（供 onlyOwnDrops 使用）
        String owner = "";
        if (event.getSource().getEntity() instanceof net.minecraft.world.entity.player.Player killer) {
            owner = killer.getStringUUID();
        }
        // Boss 死亡：移除 Boss 血条
        if (com.mcmod.monsterwaves.mob.EliteBossHandler.isBoss(entity)
                && entity instanceof net.minecraft.world.entity.Mob bossMob) {
            com.mcmod.monsterwaves.mob.BossManager.hide(bossMob);
        }
        // 统一掉落（v1.0.12：只对本 mod 生成的生物生效；原版/其他 mod 生物不受本 mod 掉落系统影响）
        if (MobSpawnManager.isTracked(entity)) {
            dropLoot(entity, level, difficulty, owner);
            // v1.0.9 延迟移除 UUID 追踪（下 tick 执行，确保 LivingDropsEvent 掉落过滤先完成）
            level.getServer().execute(() -> MobSpawnManager.untrack(entity.getUUID()));
        }
    }

    /** 原版掉落过滤 + 归属标记：只追踪本 mod 生成的怪（UUID 集合 + NBT 双判断，兼容任意 mod 生物） */
    @SubscribeEvent
    public static void onLivingDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        // v1.0.11：对本 mod 生成的生物，只删除「原版原生掉落」（该生物 loot table 的物品），
        // 保留其他 mod 的掉落（loot modifier / 掉落事件）与本 mod 掉落表（dropLoot 独立流程）——不再整体 setCanceled
        if (MobSpawnManager.isTracked(event.getEntity()) && MWConfig.get().lootOverrideVanilla) {
            java.util.Set<net.minecraft.world.item.Item> vanilla = vanillaLootItems(event.getEntity());
            if (!vanilla.isEmpty()) {
                event.getDrops().removeIf(d -> d != null && vanilla.contains(d.getItem().getItem()));
                MonsterWavesMod.LOGGER.debug("MW 掉落过滤：已移除 {} 的原版掉落物（剩 {} 个非原版）",
                        event.getEntity(), event.getDrops().size());
            }
        }
        if (event.getSource().getEntity() instanceof net.minecraft.world.entity.player.Player p) {
            String owner = p.getStringUUID();
            for (net.minecraft.world.entity.item.ItemEntity e : event.getDrops()) {
                e.getPersistentData().putString(DROP_OWNER, owner);
            }
        }
    }

    /**
     * 技能点获取（gainMode 配置）：
     * - LEVEL（默认）：每升 1 级 +pointsPerLevel 技能点
     * - XP：每积累 xpPerPoint 经验 +1 技能点（存 NBT 缓冲）
     * - DISABLED：不自动获取（仅指令/API）
     * 开发者可监听 SkillPointGainEvent 自定义获取算法。
     */
    @SubscribeEvent
    public static void onLevelChange(PlayerXpEvent.LevelChange event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!"LEVEL".equalsIgnoreCase(MWConfig.get().gainMode)) {
            return;
        }
        int levels = event.getLevels();
        if (levels > 0) {
            PlayerDataManager.grantPoints(event.getEntity(), levels * MWConfig.get().pointsPerLevel);
        }
    }

    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.XpChange event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!"XP".equalsIgnoreCase(MWConfig.get().gainMode)) {
            return;
        }
        int amount = event.getAmount();
        if (amount <= 0) {
            return;
        }
        MWConfig cfg = MWConfig.get();
        int perPoint = Math.max(1, cfg.xpPerPoint);
        Player player = event.getEntity();
        int buffer = PlayerDataManager.getXpBuffer(player) + amount;
        int gained = 0;
        while (buffer >= perPoint) {
            buffer -= perPoint;
            gained++;
        }
        PlayerDataManager.setXpBuffer(player, buffer);
        if (gained > 0) {
            PlayerDataManager.grantPoints(player, gained);
        }
    }

    /** 经验加成（drop.experience）：仅本 mod 生成的生物获得额外经验 = 原值 × (multiplier + bonusPerDifficulty × (难度-1))（v1.0.13） */
    @SubscribeEvent
    public static void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof Monster)) {
            return;
        }
        // v1.0.13：经验加成只对本 mod 生成的生物生效；原版/其他 mod 生物保持原版经验
        if (!MobSpawnManager.isTracked(event.getEntity())) {
            return;
        }
        MWConfig cfg = MWConfig.get();
        if (!cfg.experienceEnabled) {
            return;
        }
        ServerLevel level = (ServerLevel) event.getEntity().level();
        double difficulty = StageManager.getDifficulty(level.getServer());
        double factor = cfg.experienceMultiplier + cfg.experienceBonusPerDifficulty * (difficulty - 1);
        // 精英/Boss 经验倍率（精英 ×2、Boss ×20 等，配置）
        factor *= com.mcmod.monsterwaves.mob.EliteBossHandler.xpMultiplier(event.getEntity());
        int newXp = (int) Math.round(event.getOriginalExperience() * factor);
        event.setDroppedExperience(newXp);
    }

    /** 统一掉落：普通表所有怪；精英追加精英表；Boss 再追加 Boss 表；再加阶段掉落表（概率/数量受难度影响） */
    private static void dropLoot(LivingEntity entity, ServerLevel level, double difficulty, String owner) {
        MWConfig cfg = MWConfig.get();
        if (!cfg.lootEnabled) {
            return;
        }
        dropTable(entity, level, difficulty, owner, cfg.normalLoot);
        if (EliteBossHandler.isBoss(entity)) {
            dropTable(entity, level, difficulty, owner, cfg.eliteLoot);
            dropTable(entity, level, difficulty, owner, cfg.bossLoot);
        } else if (EliteBossHandler.isElite(entity)) {
            dropTable(entity, level, difficulty, owner, cfg.eliteLoot);
        }
        // v1.0.7 阶段掉落表（追加）：匹配当前阶段 id + 怪物等级（tier）
        String stageId = StageManager.getData(level.getServer()).currentStage().id();
        for (MWConfig.StageLoot sl : cfg.stageLoot) {
            if (sl == null || sl.entries == null) {
                continue;
            }
            if (!sl.stageId.isEmpty() && !sl.stageId.equals(stageId)) {
                continue;
            }
            if (!matchesTier(sl.tier, entity)) {
                continue;
            }
            dropTable(entity, level, difficulty, owner, sl.entries);
        }
    }

    /** 阶段掉落 tier 匹配：any=全部 / normal=普通 / elite=精英(不含Boss) / boss=Boss */
    private static boolean matchesTier(String tier, LivingEntity entity) {
        boolean boss = EliteBossHandler.isBoss(entity);
        boolean elite = EliteBossHandler.isElite(entity);
        if (tier == null) {
            return true;
        }
        return switch (tier) {
            case "normal" -> !elite && !boss;
            case "elite" -> elite && !boss;
            case "boss" -> boss;
            default -> true; // any
        };
    }

    /** 原版 loot table 物品缓存（按实体类型）——v1.0.11 掉落过滤用 */
    private static final java.util.Map<net.minecraft.world.entity.EntityType<?>,
            java.util.Set<net.minecraft.world.item.Item>> VANILLA_LOOT_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    // 1.20.1 这些字段是 package-private 且无 getter，用反射访问（mapped 字段名）
    private static final java.lang.reflect.Field TABLE_POOLS_FIELD = reflectField(
            net.minecraft.world.level.storage.loot.LootTable.class, "pools");
    private static final java.lang.reflect.Field POOL_ENTRIES_FIELD = reflectField(
            net.minecraft.world.level.storage.loot.LootPool.class, "entries");
    private static final java.lang.reflect.Field LOOT_ITEM_FIELD = reflectField(
            net.minecraft.world.level.storage.loot.entries.LootItem.class, "item");
    private static final java.lang.reflect.Field TABLE_REF_NAME_FIELD = reflectField(
            net.minecraft.world.level.storage.loot.entries.LootTableReference.class, "name");

    private static java.lang.reflect.Field reflectField(Class<?> cls, String name) {
        try {
            java.lang.reflect.Field f = cls.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (Exception e) {
            MonsterWavesMod.LOGGER.warn("MW 原版掉落解析字段不可用：{}.{}", cls.getSimpleName(), name);
            return null;
        }
    }

    /** 解析生物原版 loot table 会掉的物品（静态解析 pools/entries，递归子表）——含其他 mod 生物的表 */
    private static java.util.Set<net.minecraft.world.item.Item> vanillaLootItems(LivingEntity entity) {
        net.minecraft.world.entity.EntityType<?> type = entity.getType();
        return VANILLA_LOOT_CACHE.computeIfAbsent(type, t -> {
            var set = new java.util.HashSet<net.minecraft.world.item.Item>();
            try {
                var server = entity.level().getServer();
                if (server == null) {
                    return set;
                }
                var lootData = server.getLootData();
                var table = lootData.getLootTable(entity.getLootTable());
                if (table == null || table == net.minecraft.world.level.storage.loot.LootTable.EMPTY) {
                    return set;
                }
                collectTableEntries(table, set, lootData, new java.util.HashSet<>());
            } catch (Exception e) {
                MonsterWavesMod.LOGGER.debug("MW 原版掉落解析失败：{}", e.toString());
            }
            return set;
        });
    }

    private static void collectTableEntries(net.minecraft.world.level.storage.loot.LootTable table,
                                            java.util.Set<net.minecraft.world.item.Item> out,
                                            net.minecraft.world.level.storage.loot.LootDataManager lootData,
                                            java.util.Set<net.minecraft.resources.ResourceLocation> seen) {
        if (table == null || table == net.minecraft.world.level.storage.loot.LootTable.EMPTY) {
            return;
        }
        if (TABLE_POOLS_FIELD == null) {
            return;
        }
        try {
            var pools = (net.minecraft.world.level.storage.loot.LootPool[]) TABLE_POOLS_FIELD.get(table);
            if (pools == null) {
                return;
            }
            for (net.minecraft.world.level.storage.loot.LootPool pool : pools) {
                if (POOL_ENTRIES_FIELD == null) {
                    continue;
                }
                try {
                    var arr = (net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer[]) POOL_ENTRIES_FIELD.get(pool);
                    for (net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer entry : arr) {
                        collectEntry(entry, out, lootData, seen);
                    }
                } catch (Exception e) {
                    // 单个池解析失败忽略
                }
            }
        } catch (Exception e) {
            // 表解析失败忽略
        }
    }

    private static void collectEntry(net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer entry,
                                     java.util.Set<net.minecraft.world.item.Item> out,
                                     net.minecraft.world.level.storage.loot.LootDataManager lootData,
                                     java.util.Set<net.minecraft.resources.ResourceLocation> seen) {
        if (entry instanceof net.minecraft.world.level.storage.loot.entries.LootItem li && LOOT_ITEM_FIELD != null) {
            try {
                var item = (net.minecraft.world.item.Item) LOOT_ITEM_FIELD.get(li);
                if (item != null) {
                    out.add(item);
                }
            } catch (Exception e) {
                // 忽略
            }
        } else if (entry instanceof net.minecraft.world.level.storage.loot.entries.LootTableReference ref
                && TABLE_REF_NAME_FIELD != null) {
            try {
                var loc = (net.minecraft.resources.ResourceLocation) TABLE_REF_NAME_FIELD.get(ref);
                if (loc != null && seen.add(loc)) {
                    collectTableEntries(lootData.getLootTable(loc), out, lootData, seen);
                }
            } catch (Exception e) {
                // 忽略
            }
        }
    }

    /** 掉落一张掉落表 */
    private static void dropTable(LivingEntity entity, ServerLevel level, double difficulty, String owner,
                                  java.util.List<MWConfig.LootEntry> table) {
        if (table == null || table.isEmpty()) {
            return;
        }
        MWConfig cfg = MWConfig.get();
        for (MWConfig.LootEntry entry : table) {
            if (entry == null || entry.item == null || entry.item.isEmpty()) {
                continue;
            }
            double chance = Math.min(1.0, entry.chance * difficulty * cfg.lootGlobalChanceMultiplier);
            if (entity.getRandom().nextDouble() >= chance) {
                continue;
            }
            int min = Math.max(1, entry.minCount);
            int max = Math.min(4096, Math.max(min, entry.maxCount)); // v1.0.2 clamp：防 nextInt 整数溢出与堆叠爆炸
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
            ItemEntity drop = new ItemEntity(level, entity.getX(), entity.getY() + 0.5, entity.getZ(), stack);
            if (!owner.isEmpty()) {
                drop.getPersistentData().putString(DROP_OWNER, owner);
            }
            level.addFreshEntity(drop);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        com.mcmod.monsterwaves.command.MonsterWavesCommand.register(event.getDispatcher());
    }
}
