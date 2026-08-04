package com.mcmod.monsterwaves.client;

import com.mcmod.monsterwaves.mob.EliteBossHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * 精英/Boss 体型缩放（v10.3）：
 * 1.20.1 无 Entity.setScale，改用 RenderLivingEvent——Pre 事件 push+scale，Post 事件 pop 还原（Set 记录配对）。
 * 缩放倍率来自 EliteBossHandler.getScale（配置 eliteScale/bossScale）。仅渲染放大，碰撞箱保持原大小。
 */
@OnlyIn(Dist.CLIENT)
public final class MobScaleRenderer {
    private static final Set<Integer> SCALED = new HashSet<>();

    private MobScaleRenderer() {
    }

    @SubscribeEvent
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        float scale = EliteBossHandler.getScale(event.getEntity());
        if (scale != 1.0f) {
            event.getPoseStack().pushPose();
            event.getPoseStack().scale(scale, scale, scale);
            SCALED.add(event.getEntity().getId());
        }
    }

    @SubscribeEvent
    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        if (SCALED.remove(event.getEntity().getId())) {
            event.getPoseStack().popPose();
        }
    }
}
