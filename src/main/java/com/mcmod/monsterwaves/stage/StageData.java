package com.mcmod.monsterwaves.stage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.List;

/**
 * 阶段状态持久化（全局共享一份，存于主世界 level.dat）。
 * MVP 阶段不分维度；后续阶段将扩展为按维度独立存储。
 * 阶段列表动态读取自配置（各阶段难度/时长可分别调整）。
 */
public class StageData extends SavedData {
    public static final String DATA_NAME = "monsterwaves_stage";

    private int index = 0;
    private long timer = 0;

    public static StageData get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return storage.computeIfAbsent(StageData::load, StageData::new, DATA_NAME);
    }

    public static StageData load(CompoundTag tag) {
        StageData data = new StageData();
        data.index = tag.getInt("stageIndex");
        data.timer = tag.getLong("stageTimer");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("stageIndex", index);
        tag.putLong("stageTimer", timer);
        return tag;
    }

    /** 推进阶段计时器；返回是否发生了阶段切换 */
    public boolean tick() {
        StageManager.Stage stage = currentStage();
        if (stage.isInfinite()) {
            return false;
        }
        timer++;
        setDirty(); // 计时器持续变化，标记持久化以便跨重启保留阶段进度
        if (timer >= stage.durationTicks()) {
            index = Math.floorMod(index + 1, stageCount());
            timer = 0;
            setDirty();
            return true;
        }
        return false;
    }

    public StageManager.Stage currentStage() {
        List<StageManager.Stage> stages = StageManager.getStages();
        if (stages.isEmpty()) {
            return new StageManager.Stage("空", 1.0, -1);
        }
        return stages.get(Math.floorMod(index, stages.size()));
    }

    private int stageCount() {
        return Math.max(1, StageManager.getStages().size());
    }

    public int getIndex() {
        return index;
    }

    public long getTimer() {
        return timer;
    }

    public boolean next() {
        index = Math.floorMod(index + 1, stageCount());
        timer = 0;
        setDirty();
        return true;
    }

    public boolean prev() {
        index = Math.floorMod(index - 1, stageCount());
        timer = 0;
        setDirty();
        return true;
    }

    public boolean setStage(int newIndex) {
        if (newIndex < 0 || newIndex >= StageManager.getStages().size()) {
            return false;
        }
        index = newIndex;
        timer = 0;
        setDirty();
        return true;
    }
}
