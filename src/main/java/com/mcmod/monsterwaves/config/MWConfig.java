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

    // ===== 维度开关（dimensions）=====
    /** 启用本模组刷怪的维度列表；空列表 = 全部维度启用（休息维度始终不刷怪） */
    @ConfigEntry.Category("dimensions")
    @ConfigEntry.Gui.Tooltip()
    public List<String> enabledDimensions = new ArrayList<>();

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

    /** 坠落传送目标维度（arena 建立后改回 monsterwaves:arena） */
    @ConfigEntry.Category("safe")
    public String fallDestinationDimension = "minecraft:overworld";

    @ConfigEntry.Category("safe")
    public int fallDestinationX = 0;

    @ConfigEntry.Category("safe")
    public int fallDestinationY = 64;

    @ConfigEntry.Category("safe")
    public int fallDestinationZ = 0;

    /** 是否使用自定义坠落坐标（false 用目标维度出生点） */
    @ConfigEntry.Category("safe")
    @ConfigEntry.Gui.Tooltip()
    public boolean useCustomFallDestination = true;
}
