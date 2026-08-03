package com.mcmod.monsterwaves.data;

import com.mcmod.monsterwaves.config.MWConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 玩家属性存储与应用。
 * 数据存于玩家持久化 NBT 的 "monsterwaves_data" 中，跨维度、跨会话保留。
 * 属性类型通过配置 attributeMapping 直接映射到**原版/模组属性注册名**（如 minecraft:generic.attack_damage），
 * 支持任意 mod 注册的自定义属性，动态解析并应用 AttributeModifier。
 */
public final class PlayerDataManager {
    public static final String DATA_KEY = "monsterwaves_data";
    public static final String ATTRS_KEY = "attributes";

    public static final String ATK = "ATTACK";
    public static final String HP = "HEALTH";
    public static final String ARMOR = "ARMOR";

    /** 历史固定 UUID（兼容已存档玩家旧 modifier，避免重复叠加） */
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
        for (String type : MWConfig.get().ballTypes) {
            int value = a.getInt(type);
            Attribute attribute = resolveAttribute(type);
            if (attribute == null) {
                continue;
            }
            applyModifier(player, attribute, uuidFor(type), value);
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /** 按类型名从配置 attributeMapping 解析属性（支持原版/模组属性注册名） */
    private static Attribute resolveAttribute(String type) {
        String id = MWConfig.get().attributeMapping.get(type);
        if (id == null || id.isEmpty()) {
            return null;
        }
        return ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.tryParse(id));
    }

    /** 按类型名生成稳定 UUID：保留历史 3 类型固定 UUID（兼容旧存档），自定义类型用名称哈希（跨会话一致） */
    private static UUID uuidFor(String type) {
        return switch (type) {
            case ATK -> ATK_UUID;
            case HP -> HP_UUID;
            case ARMOR -> ARMOR_UUID;
            default -> UUID.nameUUIDFromBytes(("monsterwaves:" + type).getBytes(StandardCharsets.UTF_8));
        };
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
