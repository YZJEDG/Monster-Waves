package com.mcmod.monsterwaves.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.serializer.ConfigSerializer;
import me.shedaniel.autoconfig.util.Utils;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Jankson;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.JsonObject;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JSON5 配置序列化器（v1.2）。
 * 文件：config/monsterwaves.json5（支持注释 // 与 /* *\/、尾逗号，手编友好）。
 *
 * <p>为什么不用 Cloth 自带的 JanksonConfigSerializer：
 * 其默认 Marshaller 对 {@code List}/{@code Map} 字段是<b>追加</b>语义而非覆盖——
 * MWConfig 的 stages/attributeConfigs 等字段自带非空默认值，直接用它会导致
 * 每次加载把默认值叠加进文件值（如 6 阶段变 12、再读变 18…）。Gson 才是覆盖语义。
 *
 * <p>实现：
 * <ul>
 *   <li>deserialize：Jankson 解析 json5（容忍注释/尾逗号）→ 序列化为标准 JSON 字符串
 *       → Gson 反序列化（覆盖语义，不叠加默认值）</li>
 *   <li>serialize：Jankson 输出 json5（宽松格式，字段值写回；POJO 无注释元数据，不含注释属正常）</li>
 *   <li>createDefault：默认实例 + validatePostLoad（与 GsonConfigSerializer 一致）</li>
 * </ul>
 */
public class Json5ConfigSerializer<T extends ConfigData> implements ConfigSerializer<T> {

    private final Config definition;
    private final Class<T> configClass;
    private final Jankson jankson;
    private final Gson gson;

    public Json5ConfigSerializer(Config definition, Class<T> configClass) {
        this.definition = definition;
        this.configClass = configClass;
        this.jankson = Jankson.builder().build();
        this.gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }

    private Path getConfigPath() {
        return Utils.getConfigFolder().resolve(definition.name() + ".json5");
    }

    /** 静态锁：serialize/deserialize 互斥（防单机服务端 tick 与 GUI 保存线程并发操作同一 Gson/Jankson 实例） */
    private static final Object LOCK = new Object();

    @Override
    public void serialize(T config) throws SerializationException {
        Path path = getConfigPath();
        synchronized (LOCK) {
            try {
                Files.createDirectories(path.getParent());
                try (BufferedWriter writer = Files.newBufferedWriter(path)) {
                    writer.write(jankson.toJson(config).toJson(true, true));
                }
            } catch (Throwable t) {
                com.mcmod.monsterwaves.MonsterWavesMod.LOGGER.error("MW 配置保存失败（serialize），路径: {}", path, t);
                throw new SerializationException(t);
            }
        }
    }

    @Override
    public T deserialize() throws SerializationException {
        Path path = getConfigPath();
        synchronized (LOCK) {
            if (Files.exists(path)) {
                try {
                    // Jankson 解析 json5（容忍手写注释/尾逗号），再转标准 JSON 字符串交给 Gson 覆盖反序列化
                    JsonObject obj = jankson.load(path.toFile());
                    String json = obj.toJson(false, false);
                    return gson.fromJson(json, configClass);
                } catch (Throwable t) {
                    com.mcmod.monsterwaves.MonsterWavesMod.LOGGER.error("MW 配置读取失败（deserialize），路径: {}", path, t);
                    throw new SerializationException(t);
                }
            }
            return createDefault();
        }
    }

    @Override
    public T createDefault() {
        try {
            T config = configClass.getDeclaredConstructor().newInstance();
            config.validatePostLoad();
            return config;
        } catch (Exception e) {
            throw new RuntimeException("无法创建默认配置实例: " + configClass.getName(), e);
        }
    }
}
