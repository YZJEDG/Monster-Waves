package com.mcmod.monsterwaves.event;

import com.mcmod.monsterwaves.MonsterWavesMod;
import com.mcmod.monsterwaves.arena.ArenaDimensionManager;
import com.mcmod.monsterwaves.config.MWConfig;
import com.mcmod.monsterwaves.data.PlayerDataManager;
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

    /** 掉落物归属标记（击杀者 UUID，供 onlyOwnDrops 使用） */
    private static final String DROP_OWNER = "monsterwaves_owner";

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
        // Boss 血条进度更新/清理
        com.mcmod.monsterwaves.mob.BossManager.tick(server);
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
        for (ServerPlayer player : level.players()) {
            // 经验球：拉向玩家（原版接触自动吸收经验），不受 onlyOwnDrops 限制
            if (cfg.pickupXp) {
                java.util.List<net.minecraft.world.entity.ExperienceOrb> orbs =
                        level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,
                                player.getBoundingBox().inflate(range));
                for (net.minecraft.world.entity.ExperienceOrb orb : orbs) {
                    if (orb.isRemoved()) {
                        continue;
                    }
                    net.minecraft.world.phys.Vec3 dir = player.position().add(0, 0.8, 0)
                            .subtract(orb.position()).normalize();
                    orb.setDeltaMovement(dir.scale(0.6));
                    orb.hasImpulse = true; // 服务端速度同步到客户端
                }
            }
            java.util.List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class,
                    player.getBoundingBox().inflate(range));
            for (ItemEntity item : items) {
                if (item.isRemoved()) {
                    continue;
                }
                // 普通物品
                if (!cfg.pickupItems) {
                    continue;
                }
                String reg = ForgeRegistries.ITEMS.getKey(item.getItem().getItem()).toString();
                if (cfg.pickupBlacklist.contains(reg)) {
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
        // 统一掉落
        dropLoot(entity, level, difficulty, owner);
    }

    /** 原版掉落也打归属标记（击杀者玩家） */
    @SubscribeEvent
    public static void onLivingDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
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

    /** 经验加成（drop.experience）：击杀敌对怪物获得额外经验 = 原值 × (multiplier + bonusPerDifficulty × (难度-1)) */
    @SubscribeEvent
    public static void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof Monster)) {
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

    /** 统一掉落：按配置 normalLoot 生成掉落物（概率/数量受难度影响） */
    private static void dropLoot(LivingEntity entity, ServerLevel level, double difficulty, String owner) {
        MWConfig cfg = MWConfig.get();
        if (!cfg.lootEnabled || cfg.normalLoot.isEmpty()) {
            return;
        }
        for (MWConfig.LootEntry entry : cfg.normalLoot) {
            if (entry == null || entry.item == null || entry.item.isEmpty()) {
                continue;
            }
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
