package com.mcmod.monsterwaves.client;

import com.mcmod.monsterwaves.config.MWConfig;
import com.mcmod.monsterwaves.data.PlayerDataManager;
import com.mcmod.monsterwaves.network.C2SAddPoint;
import com.mcmod.monsterwaves.network.C2SResetAll;
import com.mcmod.monsterwaves.network.NetworkHandler;
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
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v9.3 技能点加点界面（FTB Library ButtonListBaseScreen）：
 * - 自带搜索框（按属性名过滤）与滚动列表
 * - 标题栏实时显示 等级 / 技能点 / 已分配
 * - 按 **mod 分类**（自动读取属性注册名 namespace 判断来源，minecraft 置顶，组内按注册名固定排序）
 * - 每组分一个不可点击的组标题行 + 属性行（点击 = 加 1 点）；首行为重置按钮
 * - 只显示 attributeConfigs 白名单内启用的属性；点数校验在服务端
 */
@OnlyIn(Dist.CLIENT)
public class SkillScreen extends ButtonListBaseScreen {
    private static SkillScreen openInstance;
    private final List<AttributeGroup> groups = new ArrayList<>();

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

        // 按 mod 分组：minecraft 置顶，其余按命名空间字母序；组内按注册名固定排序（不随已分配点数变化）
        rebuildGroups();
        for (AttributeGroup g : groups) {
            panel.add(new SimpleTextButton(panel, Component.literal("▶ " + g.displayName), Icons.INFO) {
                @Override
                public void onClicked(MouseButton button) {
                    // 组标题：不可点击
                }
            });
            for (AttributeEntry e : g.entries) {
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

    /** 按属性注册名 namespace 分组（自动读取属性来源 mod），组与组内均为固定顺序 */
    private void rebuildGroups() {
        groups.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Map<String, AttributeGroup> byNs = new LinkedHashMap<>();
        for (AttributeInstance inst : mc.player.getAttributes().getSyncableAttributes()) {
            String id = ForgeRegistries.ATTRIBUTES.getKey(inst.getAttribute()).toString();
            if (!PlayerDataManager.isEnabled(id)) {
                continue; // 白名单外不显示、不可加
            }
            String ns = id.contains(":") ? id.substring(0, id.indexOf(':')) : "unknown";
            byNs.computeIfAbsent(ns, AttributeGroup::new).entries.add(new AttributeEntry(id,
                    PlayerDataManager.displayName(id),
                    SkillDataCache.getValue(id, inst.getValue()),
                    SkillDataCache.getAllocated(id),
                    !isCapped(id)));
        }
        // 组顺序：minecraft 置顶，其余按命名空间字母序
        List<AttributeGroup> ordered = new ArrayList<>(byNs.values());
        ordered.sort(Comparator.comparing(g -> g.namespace.equals("minecraft") ? "" : g.namespace));
        // 组内：按属性注册名固定排序
        for (AttributeGroup g : ordered) {
            g.entries.sort(Comparator.comparing(e -> e.attributeId));
        }
        groups.addAll(ordered);
    }

    private static boolean isCapped(String id) {
        int maxPts = PlayerDataManager.maxPoints(id);
        return maxPts >= 0 && SkillDataCache.getAllocated(id) >= maxPts;
    }

    /** 属性来源 mod 显示名：minecraft→原版，其余取 ModList 显示名（无则用命名空间） */
    private static String modDisplayName(String namespace) {
        if (namespace.equals("minecraft")) {
            return "原版 Minecraft";
        }
        try {
            var container = ModList.get().getModContainerById(namespace);
            if (container.isPresent()) {
                return container.get().getModInfo().getDisplayName();
            }
        } catch (Exception ignored) {
        }
        return namespace;
    }

    private static class AttributeGroup {
        final String namespace;
        final String displayName;
        final List<AttributeEntry> entries = new ArrayList<>();

        AttributeGroup(String namespace) {
            this.namespace = namespace;
            this.displayName = modDisplayName(namespace);
        }
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
