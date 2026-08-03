package com.mcmod.monsterwaves.config;

import com.mcmod.monsterwaves.MonsterWavesMod;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * 怪物狂潮配置（Cloth Config AutoConfig）。
 * 存于 config/monsterwaves.json5，游戏内 Mods 列表 -> Config 按钮打开 GUI 编辑。
 * 服务端逻辑通过 {@link #get()} 读取（每次读取最新值，修改即时生效）。
 *
 * <p>注：Cloth Config 11.1.136 的 @Config 仅有 name() 属性（无规格 v8.3 所述 ConfigType.SERVER），
 * 采用标准 AutoConfig 单文件配置；如需 Forge serverconfig 可后续扩展 PartitioningSerializer。
 */
@Config(name = MonsterWavesMod.MOD_ID)
public class MWConfig implements ConfigData {

    /** 便捷获取当前配置实例（每次返回最新值） */
    public static MWConfig get() {
        return AutoConfig.getConfigHolder(MWConfig.class).getConfig();
    }

    // ===== 全局（general）=====
    @ConfigEntry.Category("general")
    @ConfigEntry.Gui.Tooltip()
    public boolean enabled = true;

    // ===== 生成设置（spawn）=====
    @ConfigEntry.Category("spawn")
    @ConfigEntry.Gui.Tooltip()
    public int spawnInterval = 40;

    @ConfigEntry.Category("spawn")
    public int minDistance = 20;

    @ConfigEntry.Category("spawn")
    public int maxDistance = 32;

    @ConfigEntry.Category("spawn")
    public int maxMobsPerPlayer = 30;

    @ConfigEntry.Category("spawn")
    public int mobCountMin = 1;

    @ConfigEntry.Category("spawn")
    public int mobCountMax = 2;

    @ConfigEntry.Category("spawn")
    public double mobStatRadius = 48.0;

    /** 怪物池：每项格式 "注册名:权重"，如 "minecraft:zombie:5" */
    @ConfigEntry.Category("spawn")
    @ConfigEntry.Gui.Tooltip()
    public List<String> mobPool = new ArrayList<>(List.of(
            "minecraft:zombie:5",
            "minecraft:skeleton:3",
            "minecraft:creeper:2"));

    // ===== 难度参数（difficulty）=====
    @ConfigEntry.Category("difficulty")
    public double healthBonusPerLevel = 0.2;

    @ConfigEntry.Category("difficulty")
    public double attackBonusPerLevel = 0.5;

    // ===== 属性球（ball）=====
    /** 属性球掉落总开关（关闭则不再掉落属性球，统一掉落不受影响） */
    @ConfigEntry.Category("ball")
    @ConfigEntry.Gui.Tooltip()
    public boolean ballDropEnabled = true;

    @ConfigEntry.Category("ball")
    public double ballBaseChance = 0.2;

    @ConfigEntry.Category("ball")
    public int ballValue = 1;

    /** 非本mod生成的敌对怪物死亡是否也掉落属性球 */
    @ConfigEntry.Category("ball")
    @ConfigEntry.Gui.Tooltip()
    public boolean dropBallsFromAllMobs = true;

    /** 可用属性球类型（ATTACK/HEALTH/ARMOR 等） */
    @ConfigEntry.Category("ball")
    public List<String> ballTypes = new ArrayList<>(List.of("ATTACK", "HEALTH", "ARMOR"));

    // ===== 属性球清理（ball_cleanup）=====
    /** 是否启用属性球自动清理（防堆积） */
    @ConfigEntry.Category("ball_cleanup")
    @ConfigEntry.Gui.Tooltip()
    public boolean cleanupEnable = true;

    /** 清理检测间隔（tick） */
    @ConfigEntry.Category("ball_cleanup")
    public int cleanupInterval = 1200;

    /** 单个维度属性球数量上限，超过清理最早的 */
    @ConfigEntry.Category("ball_cleanup")
    public int cleanupMaxCount = 500;

    /** 属性球存在时间上限（tick），超时消失 */
    @ConfigEntry.Category("ball_cleanup")
    public int cleanupDespawnTime = 12000;

    /** 仅清理已加载区块中的属性球（性能优化） */
    @ConfigEntry.Category("ball_cleanup")
    public boolean cleanupIgnoreChunkLoad = false;

    /** 参与清理的掉落物物品名（注册名）；按名字匹配，符合的才清理 */
    @ConfigEntry.Category("ball_cleanup")
    @ConfigEntry.Gui.Tooltip()
    public List<String> cleanupItemNames = new ArrayList<>(List.of(
            "monsterwaves:attack_ball", "monsterwaves:health_ball", "monsterwaves:armor_ball"));

    /** 清理时尝试将超限属性球吸向最近玩家 */
    @ConfigEntry.Category("ball_cleanup")
    @ConfigEntry.Gui.Tooltip()
    public boolean cleanupAutoAttract = true;

    // ===== 维度开关（dimensions）=====
    /** 启用本模组刷怪的维度列表；空列表 = 全部维度启用（休息维度始终不刷怪） */
    @ConfigEntry.Category("dimensions")
    @ConfigEntry.Gui.Tooltip()
    public List<String> enabledDimensions = new ArrayList<>();

    // ===== 统一掉落（loot）=====
    /** 统一掉落开关（与属性球掉落并行） */
    @ConfigEntry.Category("loot")
    @ConfigEntry.Gui.Tooltip()
    public boolean lootEnabled = true;

    /** 全局概率倍率（再乘难度） */
    @ConfigEntry.Category("loot")
    @ConfigEntry.Gui.Tooltip()
    public double lootGlobalChanceMultiplier = 1.0;

    /** 每点难度额外数量（物品掉落） */
    @ConfigEntry.Category("loot")
    @ConfigEntry.Gui.Tooltip()
    public double lootExtraCountPerLevel = 0.0;

    /** 普通怪掉落条目 */
    @ConfigEntry.Category("loot")
    public List<LootEntry> normalLoot = new ArrayList<>(List.of(
            new LootEntry("minecraft:diamond", 1, 1, 0.1)));

    /** 掉落条目：item 注册名 / nbt（可选）/ min-max 数量 / 概率（0~1）/ 是否受抢夺影响 */
    public static class LootEntry {
        public String item = "minecraft:diamond";
        public String nbt = "";
        public int minCount = 1;
        public int maxCount = 1;
        public double chance = 0.1;
        public boolean isPlayerAffected = true;

        public LootEntry() {
        }

        public LootEntry(String item, int minCount, int maxCount, double chance) {
            this.item = item;
            this.minCount = minCount;
            this.maxCount = maxCount;
            this.chance = chance;
        }

        @Override
        public String toString() {
            // 列表项显示本地化物品名（中文环境显示中文名）+ 数量/概率
            net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                    .getValue(net.minecraft.resources.ResourceLocation.tryParse(this.item));
            String name = item == null
                    ? this.item
                    : item.getName(new net.minecraft.world.item.ItemStack(item)).getString();
            return name + " ×" + minCount + "-" + maxCount + "（" + (int) (chance * 100) + "%）";
        }
    }

    // ===== 战斗符咒（battle）=====
    /** 战斗符咒开关 */
    @ConfigEntry.Category("battle")
    @ConfigEntry.Gui.Tooltip()
    public boolean battleCharmEnabled = true;

    /** 首次加入是否给予战斗符咒 */
    @ConfigEntry.Category("battle")
    @ConfigEntry.Gui.Tooltip()
    public boolean battleCharmGiveOnJoin = true;

    /** 战斗符咒冷却（tick） */
    @ConfigEntry.Category("battle")
    @ConfigEntry.Gui.Tooltip()
    public int battleCharmCooldown = 600;

    // ===== 刷怪维度（arena）=====
    /** 是否启用刷怪维度 */
    @ConfigEntry.Category("arena")
    @ConfigEntry.Gui.Tooltip()
    public boolean arenaEnabled = true;

    /** 刷怪维度出生点 Y（X/Z 固定为 0） */
    @ConfigEntry.Category("arena")
    public int arenaSpawnY = 4;

    // ===== 休息维度（safe）=====
    /** 是否启用休息维度与返回符咒 */
    @ConfigEntry.Category("safe")
    @ConfigEntry.Gui.Tooltip()
    public boolean safeEnabled = true;

    /** 空岛半径（格，自中心向外） */
    @ConfigEntry.Category("safe")
    public int islandRadius = 20;

    /** 空岛顶部方块 */
    @ConfigEntry.Category("safe")
    public String islandBlock = "minecraft:grass_block";

    /** 首次加入是否给予返回符咒 */
    @ConfigEntry.Category("safe")
    @ConfigEntry.Gui.Tooltip()
    public boolean giveOnJoin = true;

    /** 返回符咒冷却（tick） */
    @ConfigEntry.Category("safe")
    @ConfigEntry.Gui.Tooltip()
    public int safeCooldown = 600;

    /** 跳下传送触发 Y 坐标（低于此值传送） */
    @ConfigEntry.Category("safe")
    public int fallTeleportY = -10;

    /** 休息维度出生点 Y（X/Z 固定为 0） */
    @ConfigEntry.Category("safe")
    public int safeSpawnY = 65;

    /** 坠落传送目标维度（arena 已建立，默认改为刷怪维度） */
    @ConfigEntry.Category("safe")
    public String fallDestinationDimension = "monsterwaves:arena";

    /** 传送到重生点（开启=主世界用玩家重生点/其他维度用目标出生点；关闭=用下方自定义坐标，开启时隐藏自定义坐标） */
    @ConfigEntry.Category("safe")
    @ConfigEntry.Gui.Tooltip()
    public boolean fallToRespawnPoint = true;

    @ConfigEntry.Category("safe")
    public int fallDestinationX = 0;

    @ConfigEntry.Category("safe")
    public int fallDestinationY = 64;

    @ConfigEntry.Category("safe")
    public int fallDestinationZ = 0;
}
