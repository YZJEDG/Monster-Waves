package com.mcmod.monsterwaves.data;

import com.mcmod.monsterwaves.api.SkillPointGainEvent;
import com.mcmod.monsterwaves.api.SkillPointResetEvent;
import com.mcmod.monsterwaves.config.MWConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * v9.0 技能点数据管理（替代原属性球系统）：
 * - 技能点存玩家持久 NBT（monsterwaves_data.skillPoints）
 * - 已分配点数存 monsterwaves_data.allocated（属性注册名 → 点数）
 * - 旧属性球数据（attributes 段）首次访问时一次性迁移：
 *   每属性值/2 → 已分配点数（向下取整），另补偿"原属性球拾取数量 × 20%"技能点
 * - 属性应用：每点 +1（ADDITION）；百分比属性（attributeConfigs 中 percentage=true）每点 +percentagePerPoint（MULTIPLY_TOTAL）
 * - 获取模式：LEVEL（升级得点）/ XP（积累经验得点）/ DISABLED，均由事件驱动；外部可监听 SkillPointGainEvent 自定义算法
 */
public final class PlayerDataManager {
    public static final String DATA_KEY = "monsterwaves_data";
    private static final String POINTS_KEY = "skillPoints";
    private static final String ALLOCATED_KEY = "allocated";
    private static final String MIGRATED_KEY = "skillMigrated";
    private static final String XP_BUFFER_KEY = "xpBuffer";
    private static final String OLD_ATTRIBUTES_KEY = "attributes";

    private PlayerDataManager() {
    }

    // ===== NBT 访问 =====

    private static CompoundTag data(Player player) {
        return player.getPersistentData().getCompound(DATA_KEY);
    }

    private static CompoundTag mutable(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(DATA_KEY)) {
            root.put(DATA_KEY, new CompoundTag());
        }
        return root.getCompound(DATA_KEY);
    }

    /** 确保 allocated 段存在并返回其引用（getCompound 对缺失键返回临时对象，直接 putInt 会丢失！） */
    private static CompoundTag allocatedTag(Player player) {
        CompoundTag d = mutable(player);
        if (!d.contains(ALLOCATED_KEY)) {
            d.put(ALLOCATED_KEY, new CompoundTag());
        }
        return d.getCompound(ALLOCATED_KEY);
    }

    /** 首次访问迁移旧属性球数据（一次性；无旧数据则仅打迁移标记） */
    public static void migrateIfNeeded(Player player) {
        if (player.level().isClientSide) {
            return;
        }
        CompoundTag root = player.getPersistentData();
        if (!root.contains(DATA_KEY)) {
            return;
        }
        CompoundTag d = root.getCompound(DATA_KEY);
        if (d.getBoolean(MIGRATED_KEY)) {
            return;
        }
        CompoundTag old = d.getCompound(OLD_ATTRIBUTES_KEY);
        if (!old.isEmpty()) {
            CompoundTag allocated = allocatedTag(player);
            int total = 0;
            for (String k : old.getAllKeys()) {
                int v = old.getInt(k);
                total += v;
                int points = v / 2;
                if (points > 0) {
                    allocated.putInt(k, allocated.getInt(k) + points);
                }
            }
            // 补偿：原属性球拾取数量（=各属性值总和）的 20%
            int bonus = (int) Math.floor(total * 0.2);
            d.putInt(POINTS_KEY, d.getInt(POINTS_KEY) + bonus);
            d.remove(OLD_ATTRIBUTES_KEY);
        }
        d.putBoolean(MIGRATED_KEY, true);
        applyAll(player);
    }

    // ===== 技能点 =====

    public static int getPoints(Player player) {
        return data(player).getInt(POINTS_KEY);
    }

    public static void setPoints(Player player, int amount) {
        mutable(player).putInt(POINTS_KEY, Math.max(0, amount));
    }

    /** 发放技能点（触发 SkillPointGainEvent，可被取消/修改数量） */
    public static void grantPoints(Player player, int amount) {
        if (player.level().isClientSide || amount <= 0) {
            return;
        }
        SkillPointGainEvent event = new SkillPointGainEvent(player, amount);
        if (MinecraftForge.EVENT_BUS.post(event)) {
            return;
        }
        int granted = Math.max(0, event.getAmount());
        if (granted > 0) {
            mutable(player).putInt(POINTS_KEY, getPoints(player) + granted);
        }
    }

    /** 扣除技能点（返回是否成功） */
    public static boolean spendPoints(Player player, int amount) {
        if (amount < 0 || getPoints(player) < amount) {
            return false;
        }
        mutable(player).putInt(POINTS_KEY, getPoints(player) - amount);
        return true;
    }

    // ===== 已分配点数 =====

    public static int getAllocated(Player player, String attrId) {
        return data(player).getCompound(ALLOCATED_KEY).getInt(attrId);
    }

    public static Map<String, Integer> getAllAllocated(Player player) {
        Map<String, Integer> map = new HashMap<>();
        CompoundTag a = data(player).getCompound(ALLOCATED_KEY);
        for (String k : a.getAllKeys()) {
            map.put(k, a.getInt(k));
        }
        return map;
    }

    public static int totalAllocated(Player player) {
        int total = 0;
        for (int v : getAllAllocated(player).values()) {
            total += v;
        }
        return total;
    }

    // ===== 加点 =====

    /** 为玩家给指定属性加 1 点（校验：开关/属性存在/上限/可用点数；触发 SkillPointSpentEvent） */
    public static boolean addPoint(Player player, String attrId) {
        if (player.level().isClientSide) {
            return false;
        }
        MWConfig cfg = MWConfig.get();
        if (!cfg.skillEnabled) {
            return false;
        }
        Attribute attr = resolveAttribute(attrId);
        if (attr == null) {
            return false;
        }
        if (!isEnabled(attrId)) {
            return false;
        }
        int allocated = getAllocated(player, attrId);
        int maxPts = maxPoints(attrId);
        if (maxPts >= 0 && allocated >= maxPts) {
            return false;
        }
        if (cfg.maxTotalPoints >= 0 && totalAllocated(player) >= cfg.maxTotalPoints) {
            return false;
        }
        if (getPoints(player) <= 0) {
            return false;
        }
        com.mcmod.monsterwaves.api.SkillPointSpentEvent spent =
                new com.mcmod.monsterwaves.api.SkillPointSpentEvent(player, attrId, 1);
        if (MinecraftForge.EVENT_BUS.post(spent)) {
            return false;
        }
        if (!spendPoints(player, spent.getAmount())) {
            return false;
        }
        allocatedTag(player).putInt(attrId, allocated + 1);
        applyModifier(player, attr, allocated + 1);
        return true;
    }

    // ===== 重置 =====

    /** 重置全部属性分配；charge=true（GUI）时从返还点数中扣 resetCostPoints，charge=false（指令）免费 */
    public static int resetAll(Player player, boolean charge) {
        if (player.level().isClientSide) {
            return 0;
        }
        MWConfig cfg = MWConfig.get();
        if (charge && !cfg.resetEnabled) {
            return 0;
        }
        SkillPointResetEvent event = new SkillPointResetEvent(player, null);
        if (MinecraftForge.EVENT_BUS.post(event)) {
            return 0;
        }
        int refund = totalAllocated(player);
        // 移除所有已加点的 modifier（否则属性加成残留：界面/服务端值不回落，需重新加点才"修正"）
        for (String k : getAllAllocated(player).keySet()) {
            Attribute attr = resolveAttribute(k);
            if (attr != null) {
                removeModifier(player, attr);
            }
        }
        mutable(player).remove(ALLOCATED_KEY);
        int net = Math.max(0, refund - (charge ? Math.max(0, cfg.resetCostPoints) : 0));
        if (net > 0) {
            mutable(player).putInt(POINTS_KEY, getPoints(player) + net);
        }
        applyAll(player);
        return net;
    }

    /** 重置单属性（免费返还该属性点数） */
    public static int resetAttribute(Player player, String attrId) {
        if (player.level().isClientSide) {
            return 0;
        }
        int allocated = getAllocated(player, attrId);
        if (allocated <= 0) {
            return 0;
        }
        SkillPointResetEvent event = new SkillPointResetEvent(player, attrId);
        if (MinecraftForge.EVENT_BUS.post(event)) {
            return 0;
        }
        allocatedTag(player).remove(attrId);
        mutable(player).putInt(POINTS_KEY, getPoints(player) + allocated);
        Attribute attr = resolveAttribute(attrId);
        if (attr != null) {
            removeModifier(player, attr);
        }
        return allocated;
    }

    /** 轻量检查：玩家是否有白名单外已分配属性（有则触发 applyAll 清理并返还技能点） */
    public static void cleanupOutOfWhitelist(Player player) {
        if (player.level().isClientSide) {
            return;
        }
        boolean dirty = false;
        CompoundTag allocated = data(player).getCompound(ALLOCATED_KEY);
        for (String k : allocated.getAllKeys()) {
            if (!isEnabled(k)) {
                dirty = true;
                break;
            }
        }
        if (dirty) {
            applyAll(player);
        }
    }

    // ===== 属性应用 =====

    /** 重新应用玩家全部技能点属性 modifier（登录/重生/换维度时调用）。
     * 白名单（attributeConfigs）外的属性：移除 modifier、清除分配并返还技能点——移出白名单后不受加点系统影响。 */
    public static void applyAll(Player player) {
        if (player.level().isClientSide) {
            return;
        }
        migrateIfNeeded(player);
        // 清理白名单外属性（一次性修正 + 每次进入时兜底）
        java.util.List<String> toRemove = new java.util.ArrayList<>();
        for (String k : data(player).getCompound(ALLOCATED_KEY).getAllKeys()) {
            if (!isEnabled(k)) {
                toRemove.add(k);
            }
        }
        for (String k : toRemove) {
            int v = getAllocated(player, k);
            Attribute attr = resolveAttribute(k);
            if (attr != null) {
                removeModifier(player, attr);
            }
            allocatedTag(player).remove(k);
            if (v > 0) {
                mutable(player).putInt(POINTS_KEY, getPoints(player) + v);
            }
        }
        // 应用白名单内属性
        for (Map.Entry<String, Integer> entry : getAllAllocated(player).entrySet()) {
            if (!isEnabled(entry.getKey())) {
                continue;
            }
            Attribute attr = resolveAttribute(entry.getKey());
            if (attr != null) {
                applyModifier(player, attr, entry.getValue());
            }
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /** 应用/更新指定属性的技能点 modifier（固定 UUID = 属性注册名 hash，跨会话稳定） */
    private static void applyModifier(Player player, Attribute attr, int allocated) {
        if (allocated <= 0) {
            removeModifier(player, attr);
            return;
        }
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) {
            return;
        }
        UUID uuid = uuidFor(attr);
        inst.removeModifier(uuid);
        boolean percentage = isPercentage(ForgeRegistries.ATTRIBUTES.getKey(attr).toString());
        AttributeModifier mod;
        if (percentage) {
            double per = perAttributePercentagePerPoint(ForgeRegistries.ATTRIBUTES.getKey(attr).toString());
            mod = new AttributeModifier(uuid, "monsterwaves_skill",
                    allocated * per, AttributeModifier.Operation.MULTIPLY_TOTAL);
        } else {
            mod = new AttributeModifier(uuid, "monsterwaves_skill",
                    allocated, AttributeModifier.Operation.ADDITION);
        }
        inst.addPermanentModifier(mod);
    }

    private static void removeModifier(Player player, Attribute attr) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst != null) {
            inst.removeModifier(uuidFor(attr));
        }
    }

    private static UUID uuidFor(Attribute attr) {
        return UUID.nameUUIDFromBytes(
                ("monsterwaves:" + ForgeRegistries.ATTRIBUTES.getKey(attr)).getBytes(StandardCharsets.UTF_8));
    }

    // ===== 属性工具 =====

    public static Attribute resolveAttribute(String attrId) {
        return ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.tryParse(attrId));
    }

    /** 属性是否可加点：白名单（attributeConfigs）中存在且 enabled=true */
    public static boolean isEnabled(String attrId) {
        MWConfig.AttributeConfig cfg = MWConfig.get().attributeConfigs.get(attrId);
        return cfg != null && cfg.enabled;
    }

    /** 属性加点上限（白名单外返回 0=不可加） */
    public static int maxPoints(String attrId) {
        MWConfig.AttributeConfig cfg = MWConfig.get().attributeConfigs.get(attrId);
        return cfg == null ? 0 : cfg.maxPoints;
    }

    /** 属性是否百分比加成 */
    public static boolean isPercentage(String attrId) {
        MWConfig.AttributeConfig cfg = MWConfig.get().attributeConfigs.get(attrId);
        return cfg != null && cfg.percentage;
    }

    /** 该属性每点百分比加成幅度（属性配置 > 全局 percentagePerPoint） */
    public static double perAttributePercentagePerPoint(String attrId) {
        MWConfig.AttributeConfig cfg = MWConfig.get().attributeConfigs.get(attrId);
        return cfg == null ? MWConfig.get().percentagePerPoint : cfg.effectivePercentagePerPoint();
    }

    /** 属性显示名：配置 attributeDisplayNames > 属性注册的本地化名（原版/任何 mod 的 lang，如 attribute.name.generic.movement_speed → 移动速度）> 注册名 */
    public static String displayName(String attrId) {
        String custom = MWConfig.get().attributeDisplayNames.get(attrId);
        if (custom != null && !custom.isEmpty()) {
            return custom;
        }
        Attribute attr = resolveAttribute(attrId);
        if (attr == null) {
            return attrId;
        }
        String localized = net.minecraft.network.chat.Component.translatable(attr.getDescriptionId()).getString();
        return localized != null && !localized.isEmpty() && !localized.equals(attr.getDescriptionId())
                ? localized : attrId;
    }

    // ===== XP 模式缓冲 =====

    public static int getXpBuffer(Player player) {
        return data(player).getInt(XP_BUFFER_KEY);
    }

    public static void setXpBuffer(Player player, int value) {
        mutable(player).putInt(XP_BUFFER_KEY, value);
    }
}
