package com.mcmod.monsterwaves.client;

import com.mcmod.monsterwaves.config.MWConfig;
import com.mcmod.monsterwaves.data.PlayerDataManager;
import com.mcmod.monsterwaves.network.C2SAddPoint;
import com.mcmod.monsterwaves.network.C2SRequestSync;
import com.mcmod.monsterwaves.network.C2SResetAll;
import com.mcmod.monsterwaves.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * v9.0 技能点加点界面（简易版，非 FTB Library）：
 * - 按 P 键或 /monsterwaves skill gui 打开
 * - 顶部：等级 / 可用技能点 / 已分配 / 重置按钮
 * - 属性列表（动态读取玩家全部属性，含模组属性）：显示名 + 当前值 + 已分配 + [+]按钮
 * - 数据经 SimpleChannel 与服务端同步（打开时请求，加点/重置后回发刷新）
 */
@OnlyIn(Dist.CLIENT)
public class SkillScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static SkillScreen openInstance;

    private final List<AttributeEntry> entries = new ArrayList<>();
    private int scrollOffset = 0;

    public SkillScreen() {
        super(Component.literal("技能加点"));
    }

    public static SkillScreen getOpenInstance() {
        return openInstance;
    }

    @Override
    protected void init() {
        openInstance = this;
        // 请求服务端同步最新技能点数据
        NetworkHandler.sendToServer(new C2SRequestSync());
        rebuild();
    }

    @Override
    public void onClose() {
        openInstance = null;
        super.onClose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!entries.isEmpty()) {
            int visible = visibleRows();
            int max = Math.max(0, entries.size() - visible);
            scrollOffset = Math.max(0, Math.min(max, scrollOffset - (int) delta));
            rebuild();
        }
        return true;
    }

    /** 收到服务端同步后刷新界面（由 S2CSyncData 处理回调） */
    public void refresh() {
        if (Minecraft.getInstance().screen == this) {
            rebuild();
        }
    }

    private int visibleRows() {
        int panelH = Math.min(Minecraft.getInstance().getWindow().getGuiScaledHeight() - 70, 320);
        return Math.max(1, (panelH - 28) / ROW_HEIGHT);
    }

    private void rebuild() {
        clearWidgets();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        int cx = width / 2;
        int cy = height / 2;
        int panelW = 400;
        int left = cx - panelW / 2;
        int top = cy - Math.min(height - 70, 320) / 2;

        // 顶部信息
        String info = "⚡ 等级 " + mc.player.experienceLevel
                + "   🎯 技能点 " + SkillDataCache.getPoints()
                + "   📊 已分配 " + SkillDataCache.getTotalAllocated();
        addRenderableWidget(Button.builder(Component.literal(info), b -> {
        }).bounds(left, top - 4, 300, 20).build());

        // 重置按钮（按配置收费）
        Button reset = Button.builder(Component.literal("重置"), b ->
                NetworkHandler.sendToServer(new C2SResetAll())).bounds(left + 310, top - 4, 90, 20).build();
        reset.active = MWConfig.get().resetEnabled;
        addRenderableWidget(reset);

        // 属性列表
        rebuildEntries();
        int y = top + 28;
        int visible = visibleRows();
        for (int i = scrollOffset; i < Math.min(entries.size(), scrollOffset + visible); i++) {
            AttributeEntry e = entries.get(i);
            addRenderableWidget(Button.builder(Component.literal(e.displayName + "  (当前 " + String.format("%.2f", e.currentValue)
                            + ") +" + e.allocated),
                    b -> {
                    }).bounds(left + 10, y, 280, 20).build());
            Button plus = Button.builder(Component.literal("+"), b ->
                    NetworkHandler.sendToServer(new C2SAddPoint(e.attributeId))).bounds(left + 300, y, 40, 20).build();
            plus.active = e.canAdd;
            addRenderableWidget(plus);
            y += ROW_HEIGHT;
        }
        // 滚动提示
        if (entries.size() > visible) {
            addRenderableWidget(Button.builder(Component.literal("▼"), b -> {
                scrollOffset = Math.min(entries.size() - visible, scrollOffset + visible);
                rebuild();
            }).bounds(left + 350, y - ROW_HEIGHT, 40, 20).build());
        }
    }

    /** 动态读取玩家全部属性（原版+模组），按已分配/显示名排序 */
    private void rebuildEntries() {
        entries.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        for (AttributeInstance inst : mc.player.getAttributes().getSyncableAttributes()) {
            Attribute attr = inst.getAttribute();
            String id = ForgeRegistries.ATTRIBUTES.getKey(attr).toString();
            int allocated = SkillDataCache.getAllocated(id);
            boolean enabled = PlayerDataManager.isEnabled(id);
            MWConfig cfg = MWConfig.get();
            boolean capped = cfg.perAttributeMaxPoints >= 0 && allocated >= cfg.perAttributeMaxPoints;
            entries.add(new AttributeEntry(id,
                    PlayerDataManager.displayName(id),
                    inst.getValue(),
                    allocated,
                    enabled && !capped && SkillDataCache.getPoints() > 0));
        }
        entries.sort(Comparator
                .comparingInt((AttributeEntry e) -> e.allocated).reversed()
                .thenComparing(e -> e.displayName));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        int cx = width / 2;
        int top = height / 2 - Math.min(height - 70, 320) / 2;
        graphics.drawString(font, "技能加点（滚轮滚动，+ 消耗 1 技能点）", cx - 80, top - 22, 0xFFFFFF);
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
