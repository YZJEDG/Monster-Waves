package com.mcmod.monsterwaves.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * 玩家属性存储与应用。
 * 数据存于玩家持久化 NBT 的 "monsterwaves_data" 中，跨维度、跨会话保留。
 * 通过持久 AttributeModifier 将累计值映射到原版属性（攻击力/生命上限/护甲）。
 */
public final class PlayerDataManager {
    public static final String DATA_KEY = "monsterwaves_data";
    public static final String ATTRS_KEY = "attributes";

    public static final String ATK = "ATTACK";
    public static final String HP = "HEALTH";
    public static final String ARMOR = "ARMOR";

    private static final UUID ATK_UUID = UUID.fromString("1a2b3c4d-5e6f-4a8b-9c0d-1e2f3a4b5c6d");
    private static final UUID HP_UUID = UUID.fromString("2b3c4d5e-6f7a-4b9c-8d0e-2f3a4b5c6d7e");
    private static final UUID ARMOR_UUID = UUID.fromString("3c4d5e6f-7a8b-4c0d-9e1f-3a4b5c6d7e8f");

    private PlayerDataManager() {
    }

    private static CompoundTag attrs(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(DATA_KEY)) {
            root.put(DATA_KEY, new CompoundTag());
        }
        CompoundTag data = root.getCompound(DATA_KEY);
        if (!data.contains(ATTRS_KEY)) {
            data.put(ATTRS_KEY, new CompoundTag());
        }
        return data.getCompound(ATTRS_KEY);
    }

    public static int get(Player player, String type) {
        return attrs(player).getInt(type);
    }

    public static void add(Player player, String type, int amount) {
        if (player.level().isClientSide) {
            return;
        }
        set(player, type, get(player, type) + amount);
    }

    public static void set(Player player, String type, int amount) {
        if (player.level().isClientSide) {
            return;
        }
        attrs(player).putInt(type, amount);
        applyAll(player);
    }

    /** 按当前存储值重建全部属性 modifier（登录/重生/换维度/变更时调用） */
    public static void applyAll(Player player) {
        if (player.level().isClientSide) {
            return;
        }
        CompoundTag a = attrs(player);
        applyModifier(player, Attributes.ATTACK_DAMAGE, ATK_UUID, a.getInt(ATK));
        applyModifier(player, Attributes.MAX_HEALTH, HP_UUID, a.getInt(HP));
        applyModifier(player, Attributes.ARMOR, ARMOR_UUID, a.getInt(ARMOR));
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static void applyModifier(Player player, Attribute attribute, UUID uuid, int value) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (instance.getModifier(uuid) != null) {
            instance.removeModifier(uuid);
        }
        if (value != 0) {
            instance.addTransientModifier(new AttributeModifier(
                    uuid, "monsterwaves_" + attribute.getDescriptionId(), value,
                    AttributeModifier.Operation.ADDITION));
        }
    }

    public static Component attributeDisplayName(String type) {
        return Component.translatable("attribute.monsterwaves." + type);
    }
}
