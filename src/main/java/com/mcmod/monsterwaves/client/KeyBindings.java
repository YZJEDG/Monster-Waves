package com.mcmod.monsterwaves.client;

import com.mcmod.monsterwaves.config.MWConfig;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * 技能点加点界面按键（默认 P，可配置 skillSystem.keyBinding）。
 * 注意：RegisterKeyMappingsEvent 在 **mod 事件总线** 上触发，
 * 本类必须注册到 modBus（ModEvents），否则按键不会进入游戏原生"选项→控制"设置。
 */
@OnlyIn(Dist.CLIENT)
public final class KeyBindings {
    public static KeyMapping skillGui;

    private KeyBindings() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        InputConstants.Key key = InputConstants.getKey(MWConfig.get().keyBinding);
        skillGui = new KeyMapping("key.monsterwaves.skill_gui",
                key.getType(), key.getValue(),
                "key.categories.monsterwaves");
        event.register(skillGui);
    }
}
