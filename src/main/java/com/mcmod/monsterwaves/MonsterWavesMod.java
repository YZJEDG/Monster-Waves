package com.mcmod.monsterwaves;

import com.mcmod.monsterwaves.config.MWConfig;
import com.mcmod.monsterwaves.event.ModEventHandler;
import com.mcmod.monsterwaves.item.ModItems;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MonsterWavesMod.MOD_ID)
public class MonsterWavesMod {
    public static final String MOD_ID = "monsterwaves";
    public static final Logger LOGGER = LoggerFactory.getLogger(MonsterWavesMod.class);

    /**
     * Forge 1.20.1 的 @Mod 类只支持无参构造器（IEventBus 构造器注入是 1.20.4+ 的机制）。
     * FMLJavaModLoadingContext#getModEventBus 在 47.4.10 标记为 deprecated，但仍是 1.20.1 唯一官方获取方式。
     */
    @SuppressWarnings("deprecation")
    public MonsterWavesMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        MinecraftForge.EVENT_BUS.register(ModEventHandler.class);
        // 注册 Cloth Config 配置（config/monsterwaves.json5，GUI 自动集成 Mods 列表）
        AutoConfig.register(MWConfig.class, JanksonConfigSerializer::new);
        // 条件显示：开启"传送到重生点"时隐藏自定义坐标字段（fallDestinationX/Y/Z）
        AutoConfig.getGuiRegistry(MWConfig.class).registerPredicateTransformer(
                (entries, key, field, config, defaults, registry) ->
                        ((MWConfig) config).fallToRespawnPoint ? java.util.List.of() : entries,
                field -> field.getName().equals("fallDestinationX")
                        || field.getName().equals("fallDestinationY")
                        || field.getName().equals("fallDestinationZ"));
        // 注册配置屏幕扩展点：供原版 Mods 列表与 Catalogue（模组目录）显示 Config 按钮
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((client, parent) ->
                        AutoConfig.getConfigScreen(MWConfig.class, parent).get()));
        LOGGER.info("Monster Waves (怪物狂潮) MVP 已加载");
    }
}
