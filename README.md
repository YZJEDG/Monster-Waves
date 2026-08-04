# 怪物狂潮（Monster Waves）

**Minecraft 1.20.1 · Forge 47.4.x** · 正式版 **v1.0.0** · **MIT License**

以怪物波次生存为核心的整合包增强 Mod：阶段难度递进、精英/Boss、技能点加点、枪械属性加成（TaCZ）、统一掉落、大范围拾取、专属维度与传送符咒。

---

## 功能总览

### 阶段与难度
- 按配置顺序循环推进的**阶段系统**（每阶段独立难度/时长/怪物池/属性倍率/BUFF），支持无限阶段
- 聊天栏**状态播报**（默认每 30 秒提示当前阶段/难度，可配置）

### 精英怪 / Boss
- 生成时概率升级：精英（红名/发光/属性×倍率）→ 精英基础上的 Boss（金名/大幅属性/**Boss 血条**）
- 经验倍率（精英 ×2、Boss ×20 默认）、数量上限（范围内精英 ≤5、Boss ≤1，可配）
- 概率受难度影响（×√难度，可关）；属性算法可配置（乘算/加算 + `MobAttributeCalculateEvent` 事件钩子）

### 技能点系统
- 升级得点（或按经验/关闭，`gainMode`），P 键打开加点界面
- **枪械属性**（gunsmithlib）：射速/换弹速度/子弹伤害/子弹速度（每点 +10% 可配）
- 属性显示名/白名单/算法完全可配置；开发者 API 事件（Gain/Spent/Reset）

### 苦痛传递附魔（pain_transference）
- 命中怪物时对周围其他怪物造成「主伤害 × 30%~50%」传递伤害（无冷却）
- 适用：剑/斧/三叉戟/**弓/弩**/TaCZ 枪械；创造栏「战斗用品」页含 1~5 级附魔书

### 专属维度
- **休息维度**（空岛）：锁饥饿、跳下自动传送（可回战斗维度/主世界）、初始物品
- **战斗维度**（竞技场）：刷怪主战场
- **三符咒**（无限使用）：休息符咒/回归符咒（回主世界）/战斗符咒——十字合成（中间金苹果/指南针/铁剑 + 4 末影珍珠）
- **怪物传送**：怪远离玩家自动拉回（防溢出），仅启用生成引擎的维度 + 仅 mod 生成的怪

### 掉落 / 拾取 / 经验
- 统一掉落表（概率/数量/玩家击杀限定/难度加成）；大范围自动拾取（范围/黑名单可配）；经验加成

### 配置与指令
- **Cloth Config** 全中文配置界面（热重载 `/monsterwaves reload`、`/monsterwaves config save`）
- 完整指令集：`spawn/stats/difficulty/stage/safe/battle/leave/skill/player/reload/config`

---

## 依赖

| Mod | 必需 | 说明 |
|---|---|---|
| Cloth Config（≥11.1.118） | ✅ | 配置界面 |
| FTB Library（2001 系） | ✅ | 加点界面（客户端） |
| TaCZ（1.1.7+） | ⬜ 可选 | 枪械功能（含附魔/属性），缺失时其余功能正常 |
| Gunsmith Library（tacz1.1.8 版） | ⬜ 可选 | 枪械属性（射速/换弹/伤害等），缺失时相关属性行不显示 |

> 依赖缺失不会导致 Mod 崩溃（TaCZ/Gunsmith 相关功能自动降级）。

## 安装

1. 安装 **Forge 1.20.1（47.4.x）**
2. 将 `monsterwaves-1.0.0.jar` 与上述依赖放入 `mods/`
3. 启动游戏，配置在 `config/monsterwaves.json5`（或游戏内 Catalogue → 怪物狂潮 → Config）

## 快速上手

```
/monsterwaves safe        # 去休息维度（op）
/monsterwaves battle      # 去战斗维度（op）
/monsterwaves leave       # 回主世界（全员）
/monsterwaves spawn minecraft:zombie 10 boss   # 刷 10 只 Boss 僵尸（测试）
/monsterwaves skill points # 查看技能点
```

## 已知边界

- 配置界面 POJO 子字段（如掉落条目 item/minCount）显示英文字段名（Cloth Config 11.1.136 限制）
- 体型缩放不做（1.20.1 无可靠 API）
- `minecraft:generic.block_break_speed` 需模组提供同名属性才生效

## 文档

- 《开发手册.md》——实现细节/配置表/调试要点/变更记录
- 《测试手册.md》——分模块测试用例
- 《指令手册.md》——全部指令说明

---

**变更记录**：v0.9.0 → v10.6 完整记录见《开发手册.md》第 10 节。
