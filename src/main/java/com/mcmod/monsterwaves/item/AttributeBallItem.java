package com.mcmod.monsterwaves.item;

import net.minecraft.world.item.Item;

/** 属性球物品：携带属性类型标识，拾取时由事件处理器应用属性并阻止进入背包 */
public class AttributeBallItem extends Item {
    private final String attributeType;

    public AttributeBallItem(String attributeType) {
        super(new Item.Properties());
        this.attributeType = attributeType;
    }

    public String getAttributeType() {
        return attributeType;
    }
}
