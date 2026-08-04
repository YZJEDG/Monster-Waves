package com.mcmod.monsterwaves.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mcmod.monsterwaves.MonsterWavesMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 独立掉落配置加载器（v1.5，移除 Cloth Config 依赖）：
 * - 文件：config/monsterwaves_loot.json（标准 JSON，Gson，无注释）
 * - 加载：启动时 / 手动 /monsterwaves reload（无自动热更新，用户要求仅手动）
 * - 文件不存在：创建默认文件
 * - 迁移：首次运行时若旧主配置 monsterwaves.json 含掉落字段，自动迁移到本文件
 */
public final class LootConfigLoader {
    private static final String FILE_NAME = "monsterwaves_loot.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static LootConfig current = null;
    private static boolean migrated = false;

    private LootConfigLoader() {
    }

    public static Path configPath() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    /** 获取当前掉落配置（reload 由 load() 驱动；直接读取缓存实例） */
    public static LootConfig get() {
        if (current == null) {
            load();
        }
        return current;
    }

    /** 加载掉落配置（启动 / /monsterwaves reload 时调用）；文件不存在则生成默认 */
    public static boolean load() {
        Path path = configPath();
        try {
            if (Files.exists(path)) {
                current = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), LootConfig.class);
                if (current == null) {
                    current = new LootConfig();
                }
                current.validate();
            } else {
                createDefaultFile(path);
                current = new LootConfig();
            }
            MonsterWavesMod.LOGGER.info("MW 掉落配置已加载（{}）", FILE_NAME);
            return true;
        } catch (Exception e) {
            MonsterWavesMod.LOGGER.warn("MW 掉落配置解析失败（{}），使用默认/上次配置：{}", FILE_NAME, e.toString());
            if (current == null) {
                current = new LootConfig();
            }
            return false;
        }
    }

    /** 手动重载（/monsterwaves reload 时调用） */
    public static void reload() {
        load();
    }

    /** 创建默认配置文件（标准 JSON，无注释） */
    private static void createDefaultFile(Path path) {
        try {
            Files.createDirectories(path.getParent());
            LootConfig def = new LootConfig();
            Files.writeString(path, GSON.toJson(def), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            // 迁移：旧主配置（monsterwaves.json）若含掉落字段，附加迁移（仅一次）
            if (!migrated) {
                migrated = true;
                migrateFromMainConfig(path);
            }
        } catch (Exception e) {
            MonsterWavesMod.LOGGER.warn("MW 掉落配置默认文件创建失败：{}", e.toString());
        }
    }

    /** 迁移：从旧主配置 monsterwaves.json 提取掉落字段合并到新文件（仅当新文件刚创建且旧配置含掉落字段） */
    private static void migrateFromMainConfig(Path lootPath) {
        try {
            Path main = FMLPaths.CONFIGDIR.get().resolve("monsterwaves.json");
            if (!Files.exists(main)) {
                return;
            }
            String raw = Files.readString(main);
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(raw).getAsJsonObject();
            com.google.gson.JsonObject loot = new com.google.gson.JsonObject();
            boolean has = false;
            for (String key : new String[]{"normalLoot", "eliteLoot", "bossLoot", "stageLoot", "mobLoot"}) {
                if (root.has(key)) {
                    loot.add(key, root.get(key));
                    has = true;
                }
            }
            if (!has) {
                return;
            }
            // 用 Gson 合并：读默认 → 覆盖掉落字段 → 写回
            com.google.gson.JsonObject file = com.google.gson.JsonParser.parseString(Files.readString(lootPath)).getAsJsonObject();
            for (String key : loot.keySet()) {
                file.add(key, loot.get(key));
            }
            Files.writeString(lootPath, GSON.toJson(file), StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING);
            MonsterWavesMod.LOGGER.info("MW 已从 monsterwaves.json 迁移掉落配置到 {}（一次性）", FILE_NAME);
        } catch (Exception e) {
            MonsterWavesMod.LOGGER.warn("MW 掉落配置迁移失败：{}", e.toString());
        }
    }
}
