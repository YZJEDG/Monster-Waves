package com.mcmod.monsterwaves.client;

import com.mcmod.monsterwaves.config.MWConfig;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import com.mojang.blaze3d.platform.InputConstants;

/** 技能点加点界面按键（默认 P，可配置 skillSystem.keyBinding） */
@OnlyIn(Dist.CLIENT)
public final class KeyBindings {
    public static KeyMapping skillGui;

    private KeyBindings() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        InputConstants.Key key = InputConstants.getKey(MWConfig.get().keyBinding);
        skillGui = new KeyMapping("key.monsterwaves.skill_gui",
                key.getType(), key.getValue(),
                "key.categories.monsterwaves");
        event.register(skillGui);
    }
}
