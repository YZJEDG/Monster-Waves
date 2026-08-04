// ============================================================
// 怪物狂潮（Monster Waves）— 掉落拦截脚本（KubeJS）
// 版本：v1.0.16
// 作用：拦截【本 mod 生成的生物】的原版/其他 mod 表内掉落物，
//       只保留本 mod 掉落表（normalLoot/eliteLoot/bossLoot/stageLoot，
//       由 mod 内部 dropLoot 独立生成，不受本脚本影响）。
// 安装：放入 <游戏目录>/kubejs/server_scripts/ 下（KubeJS 2001.x 用 server_scripts），重启游戏自动加载。
// ============================================================

// 总开关：false = 不拦截（保留原版掉落），true = 拦截（默认）
const MW_OVERRIDE_VANILLA = true;

// 本 mod 生成生物的 NBT 标记（与 mod 内 MobSpawnManager.MARKER 一致）
const MW_MARKER = 'monsterwaves_spawned';

EntityEvents.drops(event => {
    if (!MW_OVERRIDE_VANILLA) return;
    const { entity } = event;
    if (!entity || !entity.persistentData) return;
    // 只处理本 mod 生成的生物（NBT 标记）
    if (entity.persistentData.getBoolean(MW_MARKER)) {
        // 清空原掉落（原版 loot table + 其他 mod 表内掉落物）
        event.drops.clear();
        // 本 mod 掉落表由 mod 内部独立生成，无需在此添加
    }
});
