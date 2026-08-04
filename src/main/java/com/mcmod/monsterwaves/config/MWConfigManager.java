package com.mcmod.monsterwaves.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mcmod.monsterwaves.MonsterWavesMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 主配置管理器（v1.5，移除 Cloth Config 依赖）：
 * - 文件：config/monsterwaves.json（标准 JSON，Gson 序列化，无注释）
 * - 加载：启动时 {@link #load()}；改文件后 /monsterwaves reload 手动重载；config set/save 指令写回
 * - 无 GUI、无自动热更新（用户明确要求：仅通过配置文件 + 指令修改）
 * - Gson 反序列化为覆盖语义（List/Map 不叠加默认值）
 */
public final class MWConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static MWConfig current = null;

    private MWConfigManager() {
    }

    public static Path configPath() {
        return FMLPaths.CONFIGDIR.get().resolve(MonsterWavesMod.MOD_ID + ".json");
    }

    /** 当前配置实例（每次返回最新内存值；未加载时自动加载） */
    public static MWConfig get() {
        if (current == null) {
            load();
        }
        return current;
    }

    /** 加载配置（启动 / /monsterwaves reload）；文件不存在则生成默认并写盘 */
    public static boolean load() {
        Path path = configPath();
        try {
            if (Files.exists(path)) {
                current = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), MWConfig.class);
                if (current == null) {
                    current = new MWConfig();
                }
                current.validatePostLoad();
            } else {
                current = new MWConfig();
                current.validatePostLoad();
                save(); // 首次生成默认文件
            }
            return true;
        } catch (Exception e) {
            MonsterWavesMod.LOGGER.error("MW 配置加载失败（保留旧实例/默认），路径: {}", path, e);
            if (current == null) {
                current = new MWConfig();
                current.validatePostLoad();
            }
            return false;
        }
    }

    /** 保存当前配置到文件（config save / config set 后调用） */
    public static boolean save() {
        Path path = configPath();
        try {
            if (current == null) {
                return false;
            }
            current.validatePostLoad();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(current), StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            MonsterWavesMod.LOGGER.error("MW 配置保存失败，路径: {}", path, e);
            return false;
        }
    }
}
