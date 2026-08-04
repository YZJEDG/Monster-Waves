package com.mcmod.monsterwaves.client;

import com.mcmod.monsterwaves.config.MWConfig;
import com.mcmod.monsterwaves.data.PlayerDataManager;
import com.mcmod.monsterwaves.network.C2SAddPoint;
import com.mcmod.monsterwaves.network.C2SResetAll;
import com.mcmod.monsterwaves.network.NetworkHandler;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.IScreenWrapper;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.SimpleTextButton;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.ui.misc.ButtonListBaseScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * v9.3 技能点加点界面（FTB Library ButtonListBaseScreen）：
 * - 自带搜索框（按属性名过滤）与滚动列表
 * - 标题栏实时显示 等级 / 技能点 / 已分配
 * - 列表首行为"重置全部加点"按钮（按配置收费，resetEnabled=false 时禁用），其余为属性行（点击 = 加 1 点）
 * - 只显示 attributeConfigs 白名单内启用的属性；可点性由配置/上限决定，点数校验在服务端
 */
@OnlyIn(Dist.CLIENT)
public class SkillScreen extends ButtonListBaseScreen {
    private static SkillScreen openInstance;
    private final List<AttributeEntry> entries = new ArrayList<>();

    public SkillScreen() {
        setTitle(headerTitle());
        setHasSearchBox(true);
    }

    public static SkillScreen getOpenInstance() {
        return openInstance;
    }

    /** 标题：等级 / 技能点 / 已分配（打开时与每次刷新时更新） */
    private static Component headerTitle() {
        Minecraft mc = Minecraft.getInstance();
        int level = mc.player == null ? 0 : mc.player.experienceLevel;
        return Component.literal("技能加点  ⚡等级 " + level
                + "  🎯技能点 " + SkillDataCache.getPoints()
                + "  📊已分配 " + SkillDataCache.getTotalAllocated());
    }

    @Override
    public void addButtons(Panel panel) {
        openInstance = this;
        setTitle(headerTitle());

        // 首行：重置全部加点
        MWConfig cfg = MWConfig.get();
        panel.add(new SimpleTextButton(panel, resetTitle(cfg), Icons.REMOVE) {
            @Override
            public void onClicked(MouseButton button) {
                if (button.isLeft()) {
                    NetworkHandler.sendToServer(new C2SResetAll());
                }
            }

            @Override
            public Component getTitle() {
                return resetTitle(MWConfig.get());
            }
        });

        // 属性行（白名单内启用的属性）
        rebuildEntries();
        for (AttributeEntry e : entries) {
            panel.add(new SimpleTextButton(panel, rowTitle(e), Icons.ADD) {
                @Override
                public void onClicked(MouseButton button) {
                    if (button.isLeft() && e.canAdd) {
                        NetworkHandler.sendToServer(new C2SAddPoint(e.attributeId));
                    }
                }

                @Override
                public Component getTitle() {
                    return rowTitle(e);
                }
            });
        }
    }

    private static Component resetTitle(MWConfig cfg) {
        if (!cfg.resetEnabled) {
            return Component.literal("【重置全部加点】（已禁用）");
        }
        return Component.literal("【重置全部加点】返还（已分配 - " + Math.max(0, cfg.resetCostPoints) + ")");
    }

    private static Component rowTitle(AttributeEntry e) {
        return Component.literal(e.displayName + "   当前 " + String.format("%.2f", e.currentValue)
                + "   +" + e.allocated + (e.canAdd ? "" : "  (已满)"));
    }

    /** 收到服务端同步后刷新（重建按钮列表） */
    public void refresh() {
        if (Minecraft.getInstance().screen instanceof IScreenWrapper sw && sw.getGui() == this) {
            refreshWidgets();
        }
    }

    private void rebuildEntries() {
        entries.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        for (AttributeInstance inst : mc.player.getAttributes().getSyncableAttributes()) {
            String id = ForgeRegistries.ATTRIBUTES.getKey(inst.getAttribute()).toString();
            if (!PlayerDataManager.isEnabled(id)) {
                continue; // 白名单外不显示
            }
            int allocated = SkillDataCache.getAllocated(id);
            int maxPts = PlayerDataManager.maxPoints(id);
            boolean capped = maxPts >= 0 && allocated >= maxPts;
            entries.add(new AttributeEntry(id,
                    PlayerDataManager.displayName(id),
                    SkillDataCache.getValue(id, inst.getValue()),
                    allocated,
                    !capped));
        }
        entries.sort(Comparator.comparingInt((AttributeEntry e) -> e.allocated).reversed()
                .thenComparing(e -> e.displayName));
    }

    private static class AttributeEntry {
        final String attributeId;
        final String displayName;
        final double currentValue;
        final int allocated;
        final boolean canAdd;

        AttributeEntry(String attributeId, String displayName, double currentValue, int allocated, boolean canAdd) {
            this.attributeId = attributeId;
            this.displayName = displayName;
            this.currentValue = currentValue;
            this.allocated = allocated;
            this.canAdd = canAdd;
        }
    }
}
