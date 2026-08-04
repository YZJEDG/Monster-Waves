package com.mcmod.monsterwaves.config;

import java.util.ArrayList;
import java.util.List;

/**
 * 怪物狂潮【独立掉落配置】（v1.0.18）——从主配置 config/monsterwaves.json 拆出，
 * 存于 config/monsterwaves_loot.json5（支持注释，Jankson 解析；保存自动热重载）。
 * 5 张掉落表全部独立于此文件，与主配置解耦，可单独编辑/备份/分享。
 */
public class LootConfig {

    /** 普通掉落表（所有本 mod 生成的怪） */
    public List<LootEntry> normalLoot = new ArrayList<>(List.of(
            new LootEntry("minecraft:diamond", 1, 1, 0.1)));

    /** 精英怪专属掉落表（仅精英掉落；Boss 也会掉落本表） */
    public List<LootEntry> eliteLoot = new ArrayList<>();

    /** Boss 专属掉落表（仅 Boss 掉落） */
    public List<LootEntry> bossLoot = new ArrayList<>();

    /** 阶段掉落表：stageId 阶段id（空=所有阶段）/ tier any|normal|elite|boss / entries */
    public List<StageLoot> stageLoot = new ArrayList<>();

    /** 怪物掉落表：mobType 怪物注册名（空=所有怪）/ tier any|normal|elite|boss / entries */
    public List<MobLoot> mobLoot = new ArrayList<>();

    /** 掉落条目：item 注册名 / nbt（可选）/ min-max 数量 / 概率（0~1）/ 是否受抢夺影响 */
    public static class LootEntry {
        public String item = "minecraft:diamond";
        public String nbt = "";
        public int minCount = 1;
        public int maxCount = 1;
        public double chance = 0.1;
        public boolean isPlayerAffected = true;

        public LootEntry() {
        }

        public LootEntry(String item, int minCount, int maxCount, double chance) {
            this.item = item;
            this.minCount = minCount;
            this.maxCount = maxCount;
            this.chance = chance;
        }

        @Override
        public String toString() {
            // 列表项显示本地化物品名（中文环境显示中文名）+ 数量/概率
            net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                    .getValue(net.minecraft.resources.ResourceLocation.tryParse(this.item));
            String name = item == null ? this.item : item.getName(net.minecraft.world.item.ItemStack.EMPTY).getString();
            return name + " ×" + minCount + "~" + maxCount + "  (" + String.format("%.0f", chance * 100) + "%)";
        }
    }

    /** 阶段掉落条目 */
    public static class StageLoot {
        public String stageId = "";
        public String tier = "any";
        public List<LootEntry> entries = new ArrayList<>();
    }

    /** 怪物掉落条目 */
    public static class MobLoot {
        public String mobType = "";
        public String tier = "any";
        public List<LootEntry> entries = new ArrayList<>();
    }

    /** 校验/clamp：加载后调用（null 兜底、tier 非法回退 any、条目数值 clamp） */
    public void validate() {
        for (List<LootEntry> table : List.of(normalLoot, eliteLoot, bossLoot)) {
            if (table == null) {
                continue;
            }
            table.removeIf(e -> e == null || e.item == null || e.item.isBlank());
            for (LootEntry e : table) {
                e.chance = Math.max(0.0, Math.min(1.0, e.chance));
                e.minCount = Math.max(1, e.minCount);
                e.maxCount = Math.max(e.minCount, Math.min(4096, e.maxCount));
            }
        }
        if (stageLoot == null) {
            stageLoot = new ArrayList<>();
        }
        stageLoot.removeIf(s -> s == null || s.entries == null);
        for (StageLoot s : stageLoot) {
            if (s.stageId == null) {
                s.stageId = "";
            }
            if (s.tier == null || !s.tier.matches("any|normal|elite|boss")) {
                s.tier = "any";
            }
            s.entries.removeIf(e -> e == null || e.item == null || e.item.isBlank());
            for (LootEntry e : s.entries) {
                e.chance = Math.max(0.0, Math.min(1.0, e.chance));
                e.minCount = Math.max(1, e.minCount);
                e.maxCount = Math.max(e.minCount, Math.min(4096, e.maxCount));
            }
        }
        if (mobLoot == null) {
            mobLoot = new ArrayList<>();
        }
        mobLoot.removeIf(m -> m == null || m.entries == null);
        for (MobLoot m : mobLoot) {
            if (m.mobType == null) {
                m.mobType = "";
            }
            if (m.tier == null || !m.tier.matches("any|normal|elite|boss")) {
                m.tier = "any";
            }
            m.entries.removeIf(e -> e == null || e.item == null || e.item.isBlank());
            for (LootEntry e : m.entries) {
                e.chance = Math.max(0.0, Math.min(1.0, e.chance));
                e.minCount = Math.max(1, e.minCount);
                e.maxCount = Math.max(e.minCount, Math.min(4096, e.maxCount));
            }
        }
    }
}
