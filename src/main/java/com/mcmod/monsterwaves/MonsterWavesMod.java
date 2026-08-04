package com.mcmod.monsterwaves;

import com.mcmod.monsterwaves.config.MWConfig;
import com.mcmod.monsterwaves.event.ModEventHandler;
import com.mcmod.monsterwaves.item.ModItems;
import com.mcmod.monsterwaves.network.NetworkHandler;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
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
        // 技能点网络通信（v9.0）
        NetworkHandler.register();
        // 自定义属性（tacz 测试属性）注册 + 挂到玩家属性表
        // v9.4：枪械加成改用 gunsmithlib（Gunsmith Library，TaCZ 实际读取的属性：rpm/reload_speed/bullet_damage 等），不再注册 tacz 占位属性
        // 客户端：技能点按键（**mod 总线**：RegisterKeyMappingsEvent 只在 modBus 触发，注册错总线按键不会进入游戏"控制"设置）与 P 键检测（Forge 总线）
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            MinecraftForge.EVENT_BUS.register(com.mcmod.monsterwaves.client.ClientEvents.class);
            modBus.register(com.mcmod.monsterwaves.client.KeyBindings.class);
        });
        // 符咒加入创造物品栏「战斗用品」（mod 总线事件）
        modBus.addListener(com.mcmod.monsterwaves.item.ModItems::onBuildCreativeTab);
        // v10.5 苦痛传递附魔注册（mod 总线）
        com.mcmod.monsterwaves.enchant.ModEnchantments.register(modBus);
        // 苦痛传递触发：近战/弓弩（Forge 总线，无 tacz 引用，安全）
        MinecraftForge.EVENT_BUS.register(com.mcmod.monsterwaves.enchant.PainTransferenceHandler.class);
        // TaCZ 枪械触发：仅在 tacz 加载时注册（GunPainTransferenceHandler 方法签名引用 tacz 事件类，缺失时不能加载）
        if (net.minecraftforge.fml.ModList.get().isLoaded("tacz")) {
            try {
                MinecraftForge.EVENT_BUS.register(com.mcmod.monsterwaves.enchant.GunPainTransferenceHandler.class);
            } catch (Throwable t) {
                // v1.0.2：tacz 版本不兼容（事件类缺失/改名）时注册反射会抛异常，降级为不可用而非崩溃
                MonsterWavesMod.LOGGER.warn("TaCZ 事件注册失败，枪械苦痛传递不可用（其余功能正常）", t);
            }
        }
        // 注册 Cloth Config 配置（config/monsterwaves.json5 JSON5，支持注释/尾逗号，手编友好；GUI 自动集成 Mods 列表）
        // 自定义 Json5ConfigSerializer：Jankson 解析（容忍注释）+ Gson 覆盖反序列化（避免 Jankson 默认 List/Map 追加语义叠加默认值）；
        // Forge 原生 serverconfig(TOML) 不支持 Map/嵌套对象（attributeConfigs），故用 Cloth + 自定义 JSON5 序列化器。
        AutoConfig.register(MWConfig.class, com.mcmod.monsterwaves.config.Json5ConfigSerializer::new);
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
