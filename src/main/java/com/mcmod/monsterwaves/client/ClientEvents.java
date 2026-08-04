package com.mcmod.monsterwaves.client;

import com.mcmod.monsterwaves.network.C2SRequestSync;
import com.mcmod.monsterwaves.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** 客户端事件：按键注册 + P 键打开加点界面 */
@OnlyIn(Dist.CLIENT)
public final class ClientEvents {

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        KeyBindings.register(event);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (KeyBindings.skillGui != null && KeyBindings.skillGui.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.screen == null) {
                NetworkHandler.sendToServer(new C2SRequestSync());
                mc.setScreen(new SkillScreen());
            }
        }
    }
}
