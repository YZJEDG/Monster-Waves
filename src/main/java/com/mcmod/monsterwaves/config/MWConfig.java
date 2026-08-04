package com.mcmod.monsterwaves.config;

import com.mcmod.monsterwaves.MonsterWavesMod;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    // ===== 阶段系统（stage）=====
    /** 阶段列表（可分别调整各阶段难度/时长/怪物池/属性倍率/BUFF；字段见开发手册"阶段字段"表） */
    @ConfigEntry.Category("stage")
    @ConfigEntry.Gui.Tooltip()
    public List<StageConfig> stages = new ArrayList<>(List.of(
            new StageConfig("萌芽期", 1.0, 6000),
            new StageConfig("激战期", 2.5, 12000),
            new StageConfig("终局之战", 5.0, -1)));

    /** 阶段条目：id 名称 / difficulty 难度系数 / duration 时长 / mobListOverride 专属怪物池 / attributeMultipliers 属性倍率 / mobEffects 自带BUFF */
    public static class StageConfig {
        public String id = "萌芽期";
        public double difficulty = 1.0;
        public int duration = 6000;
        /** 阶段专属怪物池（"注册名:权重"，空=用全局 mobPool） */
        public List<String> mobListOverride = new ArrayList<>();
        /** 阶段属性倍率（1.0=无加成） */
        public AttributeMultipliers attributeMultipliers = new AttributeMultipliers();
        /** 阶段怪物自带 BUFF */
        public List<EffectEntry> mobEffects = new ArrayList<>();

        public StageConfig() {
        }

        public StageConfig(String id, double difficulty, int duration) {
            this.id = id;
            this.difficulty = difficulty;
            this.duration = duration;
        }

        public static class AttributeMultipliers {
            public double healthMultiplier = 1.0;
            public double attackMultiplier = 1.0;
            public double armorMultiplier = 1.0;
        }

        public static class EffectEntry {
            public String effect = "minecraft:strength";
            public int amplifier = 0;
            public int duration = -1;
            public double chance = 1.0;
            public boolean showParticles = true;
            public boolean showIcon = true;
        }
    }

    // ===== 难度参数（difficulty）=====
    @ConfigEntry.Category("difficulty")
    public double healthBonusPerLevel = 0.2;

    @ConfigEntry.Category("difficulty")
    public double attackBonusPerLevel = 0.5;

    @ConfigEntry.Category("difficulty")
    public double armorBonusPerLevel = 0.5;

    // ===== 技能点系统（skillSystem）=====
    /** 技能点系统总开关 */
    @ConfigEntry.Category("skillSystem")
    @ConfigEntry.Gui.Tooltip()
    public boolean skillEnabled = true;

    /** 每升一级获得的技能点 */
    @ConfigEntry.Category("skillSystem")
    @ConfigEntry.Gui.Tooltip()
    public int pointsPerLevel = 1;

    /** 技能点获取模式：LEVEL=升级获得 / XP=每积累指定经验获得 / DISABLED=仅指令与API（开发者可监听 SkillPointGainEvent 自定义算法） */
    @ConfigEntry.Category("skillSystem")
    @ConfigEntry.Gui.Tooltip()
    public String gainMode = "LEVEL";

    /** XP 模式下每获得多少经验给 1 技能点 */
    @ConfigEntry.Category("skillSystem")
    @ConfigEntry.Gui.Tooltip()
    public int xpPerPoint = 100;

    /** 总技能点上限（-1=无限） */
    @ConfigEntry.Category("skillSystem")
    @ConfigEntry.Gui.Tooltip()
    public int maxTotalPoints = -1;

    /** 打开加点界面的按键（KeyMapping 名称，如 key.keyboard.p） */
    @ConfigEntry.Category("skillSystem")
    public String keyBinding = "key.keyboard.p";

    /** 属性显示名（属性注册名→显示名；缺省用原版属性名） */
    @ConfigEntry.Category("skillSystem")
    public Map<String, String> attributeDisplayNames = new java.util.HashMap<>(Map.ofEntries(
            Map.entry("minecraft:generic.attack_damage", "攻击力"),
            Map.entry("minecraft:generic.max_health", "最大生命"),
            Map.entry("minecraft:generic.armor", "护甲"),
            Map.entry("minecraft:generic.armor_toughness", "盔甲韧性"),
            Map.entry("minecraft:generic.movement_speed", "移动速度"),
            Map.entry("minecraft:generic.attack_speed", "攻击速度"),
            Map.entry("minecraft:generic.luck", "幸运"),
            Map.entry("gunsmithlib:rpm", "射速"),
            Map.entry("gunsmithlib:reload_speed", "换弹速度"),
            Map.entry("gunsmithlib:bullet_damage", "子弹伤害"),
            Map.entry("gunsmithlib:bullet_speed", "子弹速度")));

    /**
     * 属性加点白名单（属性注册名 → 配置）。**未列出的属性不可加点**，开发者可自行增删。
     * 默认：攻击/护甲/生命/速度/挖掘速度/攻击速度 + **额外属性** tacz 射速/换弹（百分比型，测试 TaCZ 对接）。
     * 注：1.20.1 原版无 block_break_speed（挖掘速度）属性（1.21+），模组提供同名属性则自动生效。
     */
    @ConfigEntry.Category("skillSystem")
    @ConfigEntry.Gui.Tooltip()
    public Map<String, AttributeConfig> attributeConfigs = new java.util.HashMap<>(Map.ofEntries(
            // 默认全部百分比加成（每点 +percentagePerPoint；每属性可单独改回数值/幅度/上限）
            Map.entry("minecraft:generic.attack_damage", new AttributeConfig(true, true, 50)),
            Map.entry("minecraft:generic.armor", new AttributeConfig(true, true, 50)),
            Map.entry("minecraft:generic.max_health", new AttributeConfig(true, true, 50)),
            Map.entry("minecraft:generic.movement_speed", new AttributeConfig(true, true, 50)),
            Map.entry("minecraft:generic.attack_speed", new AttributeConfig(true, true, 50)),
            Map.entry("minecraft:generic.block_break_speed", new AttributeConfig(true, true, 50)),
            Map.entry("minecraft:generic.luck", new AttributeConfig(true, true, 50)),
            Map.entry("minecraft:generic.armor_toughness", new AttributeConfig(true, true, 50)),
            // 枪械加成（gunsmithlib = Gunsmith Library，TaCZ 实际读取的属性）：
            // rpm 射速 / reload_speed 换弹 / bullet_damage 子弹伤害 / bullet_speed 子弹速度 / ammo_capacity 弹匣容量
            // 全部百分比型（每点 +percentagePerPoint）：rpm 基值大（数百级）需乘算，reload_speed 基值 1.0（乘数）乘算即 +10% 换弹
            Map.entry("gunsmithlib:rpm", new AttributeConfig(true, true, 50)),
            Map.entry("gunsmithlib:reload_speed", new AttributeConfig(true, true, 50)),
            Map.entry("gunsmithlib:bullet_damage", new AttributeConfig(true, true, 50)),
            Map.entry("gunsmithlib:bullet_speed", new AttributeConfig(true, true, 50))));

    /** 单属性加点配置（白名单内每个属性独立配置） */
    public static class AttributeConfig {
        /** 是否允许加点 */
        public boolean enabled = true;
        /** 是否百分比加成（默认 true：每点 +percentagePerPoint；false = 每点 +1 数值） */
        public boolean percentage = true;
        /** 该属性加点上限（-1=无限） */
        public int maxPoints = 50;
        /** 该属性百分比加成幅度（每点；0 = 跟随全局 percentagePerPoint） */
        public double percentagePerPoint = 0.0;

        public AttributeConfig() {
        }

        public AttributeConfig(boolean enabled, boolean percentage, int maxPoints) {
            this.enabled = enabled;
            this.percentage = percentage;
            this.maxPoints = maxPoints;
        }

        /** 该属性的实际每点百分比幅度（属性配置 > 全局 percentagePerPoint） */
        public double effectivePercentagePerPoint() {
            return percentagePerPoint > 0 ? percentagePerPoint : MWConfig.get().percentagePerPoint;
        }
    }

    /** 百分比属性每点加成比例（0.1 = +10%） */
    @ConfigEntry.Category("skillSystem")
    @ConfigEntry.Gui.Tooltip()
    public double percentagePerPoint = 0.1;

    /** 重置消耗的技能点（0=免费） */
    @ConfigEntry.Category("skillSystem")
    @ConfigEntry.Gui.Tooltip()
    public int resetCostPoints = 5;

    /** 是否允许重置加点 */
    @ConfigEntry.Category("skillSystem")
    @ConfigEntry.Gui.Tooltip()
    public boolean resetEnabled = true;

    // ===== 经验加成（drop.experience）=====
    /** 经验加成开关（替代原属性球掉落，加速技能点获取） */
    @ConfigEntry.Category("drop.experience")
    @ConfigEntry.Gui.Tooltip()
    public boolean experienceEnabled = true;

    /** 击杀怪物经验倍率 */
    @ConfigEntry.Category("drop.experience")
    @ConfigEntry.Gui.Tooltip()
    public double experienceMultiplier = 1.0;

    /** 每点难度额外经验加成（0.2 = 每点难度 +20%） */
    @ConfigEntry.Category("drop.experience")
    @ConfigEntry.Gui.Tooltip()
    public double experienceBonusPerDifficulty = 0.2;

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

    /** 普通怪掉落条目（POJO 表单，字段见开发手册"掉落条目字段"表） */
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

    /** 刷怪维度出生点 Y（X/Z 固定为 0；配合超平坦地表 y=159（stone×158+dirt+grass），海平面以上避免光影把低处当地下而变暗） */
    @ConfigEntry.Category("arena")
    public int arenaSpawnY = 160;

    // ===== 大范围拾取（pickup）=====
    /** 大范围拾取总开关 */
    @ConfigEntry.Category("pickup")
    @ConfigEntry.Gui.Tooltip()
    public boolean pickupEnable = true;

    /** 拾取半径（格） */
    @ConfigEntry.Category("pickup")
    public double pickupRange = 6.0;

    /** 是否拾取普通物品实体 */
    @ConfigEntry.Category("pickup")
    public boolean pickupItems = true;

    /** 是否拾取经验球（拉到玩家身边，原版接触自动吸收） */
    @ConfigEntry.Category("pickup")
    @ConfigEntry.Gui.Tooltip()
    public boolean pickupXp = true;

    /** 拾取检测间隔（tick） */
    @ConfigEntry.Category("pickup")
    public int pickupInterval = 5;

    /** 是否只拾取自己击杀产生的掉落物 */
    @ConfigEntry.Category("pickup")
    @ConfigEntry.Gui.Tooltip()
    public boolean pickupOnlyOwnDrops = false;

    /** 黑名单物品（注册名），不被拾取 */
    @ConfigEntry.Category("pickup")
    @ConfigEntry.Gui.Tooltip()
    public List<String> pickupBlacklist = new ArrayList<>();

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
