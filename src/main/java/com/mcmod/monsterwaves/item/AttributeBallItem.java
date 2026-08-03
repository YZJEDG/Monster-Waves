package com.mcmod.monsterwaves.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 属性球物品：携带属性类型标识，拾取时由事件处理器应用属性并阻止进入背包 */
public class AttributeBallItem extends Item {
    public static final String NBT_TYPE_KEY = "mw_type";

    private final String attributeType;

    public AttributeBallItem(String attributeType) {
        super(new Item.Properties());
        this.attributeType = attributeType;
    }

    public String getAttributeType() {
        return attributeType;
    }

    /** 优先读物品 NBT 中的类型（通用属性球用），无 NBT 时用物品默认类型 */
    public String getAttributeType(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(NBT_TYPE_KEY)) {
            return stack.getTag().getString(NBT_TYPE_KEY);
        }
        return attributeType;
    }
}
