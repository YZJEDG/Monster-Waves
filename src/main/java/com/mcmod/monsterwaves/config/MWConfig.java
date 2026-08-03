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
    @ConfigEntry.Gui.Tooltip(count = 2)
    public boolean enabled = true;

    // ===== 生成设置（spawn）=====
    @ConfigEntry.Category("spawn")
    @ConfigEntry.Gui.Tooltip(count = 2)
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
    @ConfigEntry.Gui.Tooltip(count = 2)
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
    @ConfigEntry.Gui.Tooltip(count = 2)
    public boolean dropBallsFromAllMobs = true;

    /** 可用属性球类型（ATTACK/HEALTH/ARMOR 等） */
    @ConfigEntry.Category("ball")
    public List<String> ballTypes = new ArrayList<>(List.of("ATTACK", "HEALTH", "ARMOR"));
}
