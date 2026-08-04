package com.mcmod.monsterwaves.config;

import com.mcmod.monsterwaves.MonsterWavesMod;
import com.google.gson.JsonElement;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 独立掉落配置加载器（v1.0.18）：
 * - 文件：config/monsterwaves_loot.json5（支持注释，Jankson 解析——cloth-config shadowed 包）
 * - 每 {@link #CHECK_INTERVAL_TICKS} tick 检查 mtime，保存后自动热重载（无需指令/重启）
 * - 文件不存在：创建默认文件（含示例注释）
 * - 迁移：首次运行时若旧主配置 monsterwaves.json 含掉落字段（normalLoot 等），自动迁移到本文件
 */
public final class LootConfigLoader {
    private static final String FILE_NAME = "monsterwaves_loot.json5";
    private static final int CHECK_INTERVAL_TICKS = 100; // 5 秒

    private static LootConfig current = new LootConfig();
    private static long lastMtime = -1;
    private static boolean migrated = false;
    private static int tickCounter = 0;

    private LootConfigLoader() {
    }

    public static Path configPath() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    /** 获取当前掉落配置（热重载由 tick() 驱动；直接读取缓存实例） */
    public static LootConfig get() {
        return current;
    }

    /** 服务端每 tick 调用：按间隔检查文件 mtime，变化则重载 */
    public static void tick() {
        if (++tickCounter % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        Path path = configPath();
        if (!Files.exists(path)) {
            if (lastMtime != -1) {
                // 文件被删：重置为默认
                current = new LootConfig();
                lastMtime = -1;
            }
            return;
        }
        try {
            long mtime = Files.getLastModifiedTime(path).toMillis();
            if (lastMtime == -1 || mtime != lastMtime) {
                lastMtime = mtime;
                load();
            }
        } catch (Exception e) {
            MonsterWavesMod.LOGGER.warn("MW 掉落配置 mtime 检查失败：{}", e.toString());
        }
    }

    /** 强制重新加载（/monsterwaves reload 时调用） */
    public static void reload() {
        lastMtime = -1;
        load();
    }

    /** 加载配置（Jankson 解析 json5 → Gson 树 → LootConfig） */
    private static void load() {
        Path path = configPath();
        try {
            if (!Files.exists(path)) {
                createDefaultFile(path);
                current = new LootConfig();
                return;
            }
            var jankson = me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Jankson.builder().build();
            var obj = jankson.load(path.toFile());
            var gson = new com.google.gson.Gson();
            LootConfig cfg = gson.fromJson(toGson(obj), LootConfig.class);
            if (cfg == null) {
                cfg = new LootConfig();
            }
            cfg.validate();
            current = cfg;
            MonsterWavesMod.LOGGER.info("MW 掉落配置已加载（{}）", FILE_NAME);
        } catch (Exception e) {
            MonsterWavesMod.LOGGER.warn("MW 掉落配置解析失败（{}），使用默认/上次配置：{}",
                    FILE_NAME, e.toString());
        }
    }

    /** Jankson 树 → Gson 树（Jankson 对象无法直接给 Gson 用） */
    private static JsonElement toGson(me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.JsonElement e) {
        if (e instanceof me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.JsonObject obj) {
            var g = new com.google.gson.JsonObject();
            for (String key : obj.keySet()) {
                g.add(key, toGson(obj.get(key)));
            }
            return g;
        } else if (e instanceof me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.JsonArray arr) {
            var g = new com.google.gson.JsonArray();
            for (me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.JsonElement el : arr) {
                g.add(toGson(el));
            }
            return g;
        } else if (e instanceof me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.JsonPrimitive prim) {
            Object v = prim.getValue();
            if (v instanceof Boolean b) {
                return new com.google.gson.JsonPrimitive(b);
            }
            if (v instanceof Number n) {
                return new com.google.gson.JsonPrimitive(n);
            }
            return new com.google.gson.JsonPrimitive(String.valueOf(v));
        }
        return com.google.gson.JsonNull.INSTANCE;
    }

    /** 创建默认配置文件（含示例注释） */
    private static void createDefaultFile(Path path) {
        try {
            Files.createDirectories(path.getParent());
            String content = "// ============================================================\n"
                    + "// 怪物狂潮（Monster Waves）— 独立掉落配置\n"
                    + "// 支持 // 注释（json5）。修改保存后自动热重载（约 5 秒），无需重启/指令。\n"
                    + "// 只对本 mod 生成的生物生效（掉落表 + 经验加成均限定）。\n"
                    + "// ============================================================\n"
                    + "{\n"
                    + "  // 普通掉落表（所有本 mod 生成的怪）\n"
                    + "  \"normalLoot\": [\n"
                    + "    { \"item\": \"minecraft:diamond\", \"minCount\": 1, \"maxCount\": 1, \"chance\": 0.1 }\n"
                    + "  ],\n"
                    + "  // 精英怪专属掉落表（仅精英；Boss 也会掉落本表）\n"
                    + "  \"eliteLoot\": [],\n"
                    + "  // Boss 专属掉落表（仅 Boss）\n"
                    + "  \"bossLoot\": [],\n"
                    + "  // 阶段掉落表：stageId=阶段id(空=所有阶段) / tier=any|normal|elite|boss / entries\n"
                    + "  \"stageLoot\": [\n"
                    + "    // { \"stageId\": \"终局之战\", \"tier\": \"boss\",\n"
                    + "    //   \"entries\": [ { \"item\": \"minecraft:dragon_egg\", \"minCount\": 1, \"maxCount\": 1, \"chance\": 1.0 } ] }\n"
                    + "  ],\n"
                    + "  // 怪物掉落表：mobType=怪物注册名(空=所有怪，如 minecraft:zombie / cataclysm:ender_golem) / tier / entries\n"
                    + "  \"mobLoot\": [\n"
                    + "    // { \"mobType\": \"minecraft:zombie\", \"tier\": \"any\",\n"
                    + "    //   \"entries\": [ { \"item\": \"minecraft:golden_apple\", \"minCount\": 1, \"maxCount\": 1, \"chance\": 0.1 } ] }\n"
                    + "  ]\n"
                    + "}\n";
            Files.write(path, content.getBytes(java.nio.charset.StandardCharsets.UTF_8),
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

    /** 迁移：从旧主配置 monsterwaves.json 提取掉落字段追加到新文件（仅当新文件刚创建且旧配置含掉落字段） */
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
            // 把新文件内容（默认）与迁移字段合并：读默认 → 覆盖掉落字段 → 写回
            String content = Files.readString(lootPath);
            // 简单方式：去掉末尾的 "}"，插入迁移字段
            int brace = content.lastIndexOf('}');
            StringBuilder sb = new StringBuilder(content);
            sb.setLength(brace);
            sb.append("  // ↓↓↓ 以下字段自动迁移自旧配置 monsterwaves.json（删除注释后请自行清理）\n");
            sb.append(com.google.gson.JsonParser.parseString(loot.toString()).toString());
            sb.append("}\n");
            Files.write(lootPath, sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    StandardOpenOption.TRUNCATE_EXISTING);
            MonsterWavesMod.LOGGER.info("MW 已从 monsterwaves.json 迁移掉落配置到 {}（一次性）", FILE_NAME);
        } catch (Exception e) {
            MonsterWavesMod.LOGGER.warn("MW 掉落配置迁移失败：{}", e.toString());
        }
    }
}
