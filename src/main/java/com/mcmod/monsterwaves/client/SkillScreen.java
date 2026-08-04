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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v9.3 技能点加点界面（FTB Library ButtonListBaseScreen）重构版：
 * - 自带搜索框（按属性名过滤）与滚动列表；标题栏实时显示 等级/技能点/已分配
 * - 按 mod 分组（自动读取属性 namespace 来源，minecraft 置顶），组内按注册名固定排序
 * - **折叠状态独立存于字段**（Map<namespace, Boolean>），重建/刷新不丢失；
 *   组标题点击切换折叠，列表延迟到下一 tick 重建（避免事件分发中修改列表）
 * - 折叠组显示"▶ 组名 (N)"且不渲染属性行；展开显示"▼ 组名"
 */
@OnlyIn(Dist.CLIENT)
public class SkillScreen extends ButtonListBaseScreen {
    private static SkillScreen openInstance;
    /** 折叠状态：属性 namespace -> 是否折叠（独立于组对象，刷新/重建不丢失） */
    private final Map<String, Boolean> collapsed = new HashMap<>();
    private final List<AttributeGroup> groups = new ArrayList<>();

    public SkillScreen() {
        setTitle(headerTitle());
        setHasSearchBox(true);
    }

    public static SkillScreen getOpenInstance() {
        return openInstance;
    }

    // ===== 打开状态判断 =====

    private boolean isOpen() {
        return Minecraft.getInstance().screen instanceof IScreenWrapper sw && sw.getGui() == this;
    }

    // ===== 标题 =====

    private static Component headerTitle() {
        Minecraft mc = Minecraft.getInstance();
        int level = mc.player == null ? 0 : mc.player.experienceLevel;
        return Component.literal("技能加点  ⚡等级 " + level
                + "  🎯技能点 " + SkillDataCache.getPoints()
                + "  📊已分配 " + SkillDataCache.getTotalAllocated());
    }

    // ===== 按钮构建（ButtonListBaseScreen 回调，每次 refreshWidgets 重建） =====

    @Override
    public void addButtons(Panel panel) {
        openInstance = this;
        setTitle(headerTitle());

        // 首行：重置全部加点
        panel.add(resetButton(panel));

        // 按 mod 分组（minecraft 置顶、组内注册名固定排序）；折叠组只显示标题
        rebuildGroups();
        for (AttributeGroup g : groups) {
            panel.add(groupHeaderButton(panel, g));
            if (collapsed.getOrDefault(g.namespace, false)) {
                continue;
            }
            for (AttributeEntry e : g.entries) {
                panel.add(attributeButton(panel, e));
            }
        }
    }

    private SimpleTextButton resetButton(Panel panel) {
        return new SimpleTextButton(panel, resetTitle(MWConfig.get()), Icons.REMOVE) {
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
        };
    }

    private SimpleTextButton groupHeaderButton(Panel panel, AttributeGroup g) {
        return new SimpleTextButton(panel, groupTitle(g), Icons.INFO) {
            @Override
            public void onClicked(MouseButton button) {
                if (button.isLeft()) {
                    toggleGroup(g.namespace);
                }
            }

            @Override
            public Component getTitle() {
                return groupTitle(g);
            }
        };
    }

    private SimpleTextButton attributeButton(Panel panel, AttributeEntry e) {
        return new SimpleTextButton(panel, rowTitle(e), Icons.ADD) {
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
        };
    }

    // ===== 折叠逻辑（状态存字段，重建不丢失；延迟一 tick 重建避免事件分发中改列表） =====

    private void toggleGroup(String namespace) {
        collapsed.put(namespace, !collapsed.getOrDefault(namespace, false));
        Minecraft.getInstance().tell(() -> {
            if (isOpen()) {
                refreshWidgets();
            }
        });
    }

    // ===== 标题文本 =====

    private static Component resetTitle(MWConfig cfg) {
        if (!cfg.resetEnabled) {
            return Component.literal("【重置全部加点】（已禁用）");
        }
        return Component.literal("【重置全部加点】返还（已分配 - " + Math.max(0, cfg.resetCostPoints) + ")");
    }

    private static Component groupTitle(AttributeGroup g) {
        boolean collapsed = SkillScreen.getOpenInstance() != null
                && SkillScreen.getOpenInstance().collapsed.getOrDefault(g.namespace, false);
        return Component.literal((collapsed ? "▶ " : "▼ ") + g.displayName
                + (collapsed ? "  (" + g.entries.size() + ")" : ""));
    }

    private static Component rowTitle(AttributeEntry e) {
        // 当前值**实时读取**客户端玩家属性（服务端 modifier 变更经属性同步即时反映，不依赖构造快照）
        double value = e.currentValue;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            var attr = PlayerDataManager.resolveAttribute(e.attributeId);
            var inst = attr == null ? null : mc.player.getAttribute(attr);
            if (inst != null) {
                value = inst.getValue();
            }
        }
        return Component.literal(e.displayName + "   当前 " + String.format("%.2f", value)
                + "   +" + e.allocated + (e.canAdd ? "" : "  (已满)"));
    }

    // ===== 数据构建 =====

    /** 收到服务端同步后刷新（重建按钮列表；折叠状态保留在字段中） */
    public void refresh() {
        if (isOpen()) {
            refreshWidgets();
        }
    }

    /** 按属性注册名 namespace 分组（自动读取属性来源 mod）；组与组内均为固定顺序。
     * 枚举方式：**白名单驱动**（遍历 attributeConfigs 键解析属性，不依赖 getSyncableAttributes 过滤，
     * 保证 tacz 等自注册属性必然列出），再用 syncable 属性兜底补充。 */
    private void rebuildGroups() {
        groups.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        Map<String, AttributeGroup> byNs = new LinkedHashMap<>();
        // 1) 白名单驱动：每个白名单属性都尝试加入（已注册且玩家有实例才显示）
        for (String id : MWConfig.get().attributeConfigs.keySet()) {
            if (!PlayerDataManager.isEnabled(id)) {
                continue;
            }
            var attr = PlayerDataManager.resolveAttribute(id);
            var inst = attr == null ? null : mc.player.getAttribute(attr);
            if (attr == null || inst == null) {
                continue;
            }
            addEntry(byNs, id, inst);
            seen.add(id);
        }
        // 2) syncable 兜底：白名单内但上面漏掉的可同步属性（如非注册名匹配差异）
        for (AttributeInstance inst : mc.player.getAttributes().getSyncableAttributes()) {
            String id = ForgeRegistries.ATTRIBUTES.getKey(inst.getAttribute()).toString();
            if (seen.contains(id) || !PlayerDataManager.isEnabled(id)) {
                continue;
            }
            addEntry(byNs, id, inst);
        }
        // 首次打开时输出排查日志：tacz 属性注册/实例状态
        if (!debugLogged) {
            debugLogged = true;
            var taczAttr = PlayerDataManager.resolveAttribute("tacz:gun_fire_rate");
            var taczInst = taczAttr == null ? null : mc.player.getAttribute(taczAttr);
            com.mcmod.monsterwaves.MonsterWavesMod.LOGGER.info("[MonsterWaves] UI: tacz属性已注册={} 玩家有实例={}；本玩家可同步属性数={}",
                    taczAttr != null, taczInst != null,
                    mc.player.getAttributes().getSyncableAttributes().size());
        }
        List<AttributeGroup> ordered = new ArrayList<>(byNs.values());
        ordered.sort(Comparator.comparing(g -> g.namespace.equals("minecraft") ? "" : g.namespace));
        for (AttributeGroup g : ordered) {
            g.entries.sort(Comparator.comparing(e -> e.attributeId));
        }
        groups.addAll(ordered);
    }

    private static boolean debugLogged = false;

    private void addEntry(Map<String, AttributeGroup> byNs, String id, AttributeInstance inst) {
        String ns = id.contains(":") ? id.substring(0, id.indexOf(':')) : "unknown";
        byNs.computeIfAbsent(ns, AttributeGroup::new).entries.add(new AttributeEntry(id,
                PlayerDataManager.displayName(id),
                SkillDataCache.getValue(id, inst.getValue()),
                SkillDataCache.getAllocated(id),
                !isCapped(id)));
    }

    private static boolean isCapped(String id) {
        int maxPts = PlayerDataManager.maxPoints(id);
        return maxPts >= 0 && SkillDataCache.getAllocated(id) >= maxPts;
    }

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

    // ===== 内部结构 =====

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
