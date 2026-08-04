package com.mcmod.monsterwaves.client;

import com.mcmod.monsterwaves.network.S2CSyncData;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/** 客户端技能点数据缓存（由 S2CSyncData 更新，加点界面读取） */
@OnlyIn(Dist.CLIENT)
public final class SkillDataCache {
    private static int points = 0;
    private static int totalAllocated = 0;
    private static Map<String, Integer> allocated = new HashMap<>();

    private SkillDataCache() {
    }

    public static void apply(S2CSyncData data) {
        points = data.getPoints();
        totalAllocated = data.getTotalAllocated();
        allocated = new HashMap<>(data.getAllocated());
    }

    public static int getPoints() {
        return points;
    }

    public static int getTotalAllocated() {
        return totalAllocated;
    }

    public static int getAllocated(String attrId) {
        return allocated.getOrDefault(attrId, 0);
    }
}
