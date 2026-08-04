这是一个基于mc1.20.1+forge47.4.10（https://docs.minecraftforge.net/en/1.20.1/）的mod。mod功能是源源不断地刷新怪物在玩家20格以外的地方，并且可以使用Cloth Config API（https://shedaniel.gitbook.io/cloth-config）进行局内配置，可以读取其他mod的生物进行生成，可以配置进度模式或者时间模式生成对应的怪物，可以自行配置生成的怪物，或者作为精英怪、boss等阶段性出现。同时按怪物的类型（普通、精英、boss）会掉落不同加成的属性球（攻击力、生命值上限、护甲值等）玩家拾取后会永久提升对应等级。

\# 怪物狂潮（Monster Waves）— 完整技术规格文档 v8.3

\## 文档说明

本文档为 \*\*怪物狂潮（Monster Waves）\*\* 模组的完整技术规格说明书，涵盖模组全部功能模块、配置参数、指令系统、事件API、数据结构和执行流程。本文档面向模组开发者、整合包作者及AI Agent，可作为开发实现的直接参考。

\## 目录

1\. \[模组概述](#1-模组概述)

2\. \[维度体系](#2-维度体系)

3\. \[阶段与难度系统](#3-阶段与难度系统)

4\. \[生成规则引擎](#4-生成规则引擎)

5\. \[怪物属性计算](#5-怪物属性计算)

6\. \[怪物自带BUFF系统](#6-怪物自带buff系统)

7\. \[属性球系统](#7-属性球系统)

8\. \[统一掉落系统](#8-统一掉落系统)

9\. \[休息维度系统](#9-休息维度系统)

10\. \[刷怪维度系统](#10-刷怪维度系统)

11\. \[传送符咒系统](#11-传送符咒系统)

12\. \[玩家大范围拾取系统](#12-玩家大范围拾取系统)

13\. \[苦痛传递附魔](#13-苦痛传递附魔)

14\. \[配置参数完整汇总](#14-配置参数完整汇总)

15\. \[指令系统](#15-指令系统)

16\. \[事件API](#16-事件api)

17\. \[玩家数据存储](#17-玩家数据存储)

18\. \[执行流程](#18-执行流程)

19\. \[Cloth Config GUI布局](#19-cloth-config-gui布局)

20\. \[兼容性与注意事项](#20-兼容性与注意事项)

21\. \[完整默认配置示例](#21-完整默认配置示例)



\---



\## 1. 模组概述



| 项目 | 内容 |

|------|------|

| \*\*模组名\*\* | 怪物狂潮（Monster Waves） |

| \*\*模组ID\*\* | `monsterwaves` |

| \*\*游戏版本\*\* | Minecraft 1.20.1 |

| \*\*Forge版本\*\* | 47.4.10 |

| \*\*前置依赖\*\* | Cloth Config API（Forge版，>=11.1.118） |

| \*\*运行环境\*\* | 服务端/客户端均可。核心逻辑必须在服务端执行；客户端仅负责发送指令、显示配置界面和接收同步数据。 |

| \*\*权限管理\*\* | 所有指令均通过Forge权限节点控制，整合包可配合FTB Quests等模组执行指令作为任务奖励或条件。 |



\### 1.1 核心功能



| 功能模块 | 说明 |

|----------|------|

| 动态怪物生成 | 在可配置的维度中持续生成怪物（普通/精英/Boss） |

| 阶段与难度系统 | 基于阶段和难度系数的怪物强度与掉落系统 |

| 永久属性成长 | 通过拾取属性球永久提升玩家属性 |

| 安全休息维度 | 空岛安全区，不刷怪，不饥饿，不死 |

| 超平坦刷怪维度 | 默认启用的战斗战场 |

| 传送符咒系统 | 返回符咒+战斗符咒，快速切换维度 |

| 大范围拾取 | 自动拾取周围掉落物和属性球 |

| 苦痛传递附魔 | 范围伤害附魔，兼容TaCZ枪械 |

| 怪物自带BUFF | 为生成的怪物配置任意药水效果（兼容原版与模组效果） |

| 全部GUI可调 | 所有配置通过Cloth Config API图形界面配置 |

| 完整指令系统 | `/monsterwaves` 根指令，支持维度参数 |



\---



\## 2. 维度体系



\### 2.1 维度列表



| 维度 | ID | 默认启用 | 用途 |

|------|----|---------|------|

| 休息维度 | `monsterwaves:safe` | ✅ 始终启用（不受维度覆盖影响） | 安全区，空岛半径20，无怪物，无饥饿，免疫死亡 |

| 刷怪维度 | `monsterwaves:arena` | ✅ 默认启用 | 超平坦战场，所有生成规则在此生效 |

| 主世界 | `minecraft:overworld` | ❌ 默认禁用 | 可手动启用（维度覆盖中设置为`true`） |

| 下界 | `minecraft:the\_nether` | ❌ 默认禁用 | 可手动启用 |

| 末地 | `minecraft:the\_end` | ❌ 默认禁用 | 可手动启用 |

| 其他模组维度 | 任意 | ❌ 默认禁用 | 需在维度覆盖中添加并启用 |



\### 2.2 启用/禁用行为



| 状态 | 行为 |

|------|------|

| \*\*维度启用\*\* | 本模组以`EventPriority.HIGHEST`优先级取消该维度中原版对应生物的自然生成（仅限本模组管理池内的生物），由模组规则驱动生成 |

| \*\*维度禁用\*\* | 本模组完全不干预该维度的生成逻辑，原版或其他模组的生成照常进行 |



> \*\*注意\*\*：休息维度是独立系统，不受维度覆盖系统中的`enabled`开关控制，它有自己的开关`safeDimension.enabled`。



\---



\## 3. 阶段与难度系统



\### 3.1 基本概念



\- 每个维度独立维护当前阶段。

\- 阶段列表`stage.list`是一个JSON数组，\*\*数量无限制，名称完全自定义\*\*。

\- 每个阶段有一个固定的\*\*难度系数`difficulty`\*\*（浮点数，≥0），\*\*模组不会自动修改它\*\*，切换阶段时仅采用新阶段的固定`difficulty`值。

\- 阶段可配置持续时间（`duration`，tick），到达后自动推进至下一阶段（若`stage.autoAdvance=true`）。

\- 也可通过指令手动切换阶段（`/monsterwaves stage set`）。



\### 3.2 阶段数据结构



```json

{

&#x20; "id": "萌芽期",              // 阶段名称，完全自定义，不限于数字

&#x20; "difficulty": 1.0,             // 固定难度系数（不自动变化）

&#x20; "duration": 6000,              // 持续tick，-1为无限

&#x20; "rules": \[                     // 该阶段的生成规则（同全局规则格式）

&#x20;   {

&#x20;     "id": "time\_wave",

&#x20;     "enabled": true,

&#x20;     "trigger": {"type": "TIME", "value": 400, "reset\_on\_trigger": true},

&#x20;     "scaling": {"count\_modifier": 1.0}

&#x20;   }

&#x20; ],

&#x20; "mobListOverride": \[           // 可选，覆盖怪物池

&#x20;   "minecraft:zombie",

&#x20;   "minecraft:skeleton"

&#x20; ],

&#x20; "eliteChanceOverride": 0.05,   // 可选，覆盖精英概率

&#x20; "bossChanceOverride": 0.01,    // 可选，覆盖Boss概率

&#x20; "attributeMultipliers": {      // 可选，各属性额外乘数

&#x20;   "healthMultiplier": 1.0,

&#x20;   "attackMultiplier": 1.0,

&#x20;   "armorMultiplier": 1.0

&#x20; },

&#x20; "mobEffects": \[                // 可选，该阶段怪物自带BUFF

&#x20;   {

&#x20;     "effect": "minecraft:strength",

&#x20;     "amplifier": 0,

&#x20;     "duration": -1,

&#x20;     "chance": 1.0,

&#x20;     "showParticles": true,

&#x20;     "showIcon": true

&#x20;   }

&#x20; ]

}

```



\### 3.3 阶段配置参数说明



| 参数 | 类型 | 必填 | 说明 |

|------|------|------|------|

| `id` | 字符串 | ✅ | 阶段名称（可包含中文、表情符号等任意字符） |

| `difficulty` | 浮点数 | ✅ | 该阶段的难度系数（≥0） |

| `duration` | 整数 | ✅ | 持续时间（tick），`-1`为无限 |

| `rules` | 规则数组 | ❌ | 该阶段的生成规则，若为空则使用全局`spawn.rules` |

| `mobListOverride` | 字符串列表 | ❌ | 覆盖怪物池，不填则使用全局`spawn.mobList` |

| `eliteChanceOverride` | 浮点数 | ❌ | 覆盖精英概率（0\~1），不填则使用全局`spawn.eliteChance` |

| `bossChanceOverride` | 浮点数 | ❌ | 覆盖Boss概率（0\~1），不填则使用全局`spawn.bossChance` |

| `attributeMultipliers` | 对象 | ❌ | 各属性额外乘数（`healthMultiplier`、`attackMultiplier`、`armorMultiplier`），不填则默认为1.0 |

| `mobEffects` | 效果数组 | ❌ | 该阶段怪物自带的药水效果列表，覆盖上级配置 |



\### 3.4 难度系数的影响范围



| 受影响项 | 说明 |

|----------|------|

| 怪物生命上限 | 原版生命 × (1 + (difficulty-1) × `healthBonusPerLevel`) × 精英/Boss倍率 × 阶段`attributeMultipliers.healthMultiplier` |

| 怪物攻击力 | 原版攻击 + (difficulty-1) × `attackBonusPerLevel` × 阶段`attributeMultipliers.attackMultiplier`，再乘以精英/Boss倍率 |

| 怪物护甲值 | 原版护甲 + (difficulty-1) × `armorBonusPerLevel` × 阶段`attributeMultipliers.armorMultiplier` |

| 属性球掉落概率 | `baseChance × difficulty × drop.difficulty.chanceMultiplier`（封顶1.0） |

| 物品掉落概率 | `条目chance × difficulty × drop.unified.globalChanceMultiplier × drop.difficulty.chanceMultiplier`（封顶1.0） |

| 物品掉落数量 | 基础数量 + `floor(drop.difficulty.extraCountPerLevel × (difficulty - 1))` |



\*\*速度\*\*：不受难度系数影响，仅由精英/Boss速度倍率决定。



\---



\## 4. 生成规则引擎



\### 4.1 规则数据结构



```json

{

&#x20; "id": "time\_wave",                    // 规则唯一标识

&#x20; "enabled": true,                      // 是否启用

&#x20; "trigger": {

&#x20;   "type": "TIME",                     // PROGRESS | TIME | PLAYER\_EVENT | ALWAYS

&#x20;   "value": 400,                       // 时间间隔(tick)或进度阈值

&#x20;   "reset\_on\_trigger": true            // 仅对TIME有效

&#x20; },

&#x20; "condition": {                        // 可选，额外条件

&#x20;   "min\_players": 1,

&#x20;   "max\_players": 10,

&#x20;   "required\_dimension": "monsterwaves:arena"

&#x20; },

&#x20; "scaling": {

&#x20;   "count\_modifier": 1.0,              // 生成数量乘数

&#x20;   "elite\_chance\_modifier": 0.0,       // 精英概率增加值（绝对值）

&#x20;   "boss\_chance\_modifier": 0.0,        // Boss概率增加值

&#x20;   "strength\_multiplier": 1.0          // 额外强度乘数（乘在难度系数之后）

&#x20; },

&#x20; "override\_mob\_list": \[                // 可选，覆盖全局怪物池

&#x20;   "minecraft:zombie"

&#x20; ],

&#x20; "mobEffects": \[                       // 可选，该规则怪物自带BUFF（覆盖上级配置）

&#x20;   {

&#x20;     "effect": "minecraft:haste",

&#x20;     "amplifier": 1,

&#x20;     "duration": 200,

&#x20;     "chance": 0.8,

&#x20;     "showParticles": true,

&#x20;     "showIcon": true

&#x20;   }

&#x20; ]

}

```



\### 4.2 触发器类型



| 类型 | `value`含义 | 说明 |

|------|-------------|------|

| `PROGRESS` | 整数阈值 | 玩家进度值 ≥ 阈值时持续生效 |

| `TIME` | 整数间隔（tick） | 自上次触发后每间隔`value` tick触发一次。若`reset\_on\_trigger=true`，则重置计时器；若`false`，则固定周期触发 |

| `PLAYER\_EVENT` | 事件名字符串 | 由外部指令或API触发（`/monsterwaves event <事件名>`） |

| `ALWAYS` | 0 | 每tick均尝试生成（受`spawn.interval`限制） |



\### 4.3 规则匹配逻辑



系统每`spawn.interval` tick检查一次所有在线玩家：



1\. 对于每个玩家，遍历当前阶段（或全局）中所有`enabled=true`的规则

2\. 检查`trigger`条件是否满足

3\. 检查`condition`中的附加条件是否满足

4\. \*\*所有满足条件的规则均触发\*\*，同一tick内可多次生成（若多个规则同时满足，则一次性生成多波怪物）

5\. 若没有任何规则满足，则该tick不生成任何怪物



\---



\## 5. 怪物属性计算



\### 5.1 属性公式



怪物生成时，基础属性取自原版生物（如`Zombie`的20生命、2攻击等），然后应用以下修正：



\*\*生命上限\*\* = 原版生命 × (1 + (difficulty-1) × `healthBonusPerLevel`) × 精英/Boss倍率 × 阶段`attributeMultipliers.healthMultiplier`



\*\*攻击力\*\*（近战） = 原版攻击 + (difficulty-1) × `attackBonusPerLevel` × 阶段`attributeMultipliers.attackMultiplier`，再乘以精英/Boss倍率



\*\*护甲值\*\* = 原版护甲 + (difficulty-1) × `armorBonusPerLevel` × 阶段`attributeMultipliers.armorMultiplier`



\*\*速度\*\* = 原版速度 × 精英/Boss速度倍率（\*\*不受难度系数影响\*\*）



\### 5.2 精英/Boss倍率



| 类型 | 生命倍率 | 速度倍率 | 额外效果 |

|------|----------|----------|----------|

| 精英 | `eliteHealthMultiplier`（默认2.0） | `eliteSpeedMultiplier`（默认1.3） | 随机药水效果（可配置列表） |

| Boss | `bossHealthMultiplier`（默认4.0） | `bossSpeedMultiplier`（默认1.5） | 多个药水效果，体型缩放（可选） |



\### 5.3 全局难度参数（`spawn.difficulty.\*`）



| 参数 | 类型 | 默认值 | 说明 |

|------|------|--------|------|

| `healthBonusPerLevel` | 浮点数 | `0.2` | 每点难度（difficulty-1）增加生命百分比（如0.2=20%） |

| `attackBonusPerLevel` | 浮点数 | `0.5` | 每点难度增加攻击力（固定值） |

| `armorBonusPerLevel` | 浮点数 | `0.5` | 每点难度增加护甲值（固定值） |

| `applyToEliteBoss` | 布尔 | `true` | 难度是否也应用于精英和Boss（否则它们只乘自身倍率） |



\---



\## 6. 怪物自带BUFF系统



\### 6.1 功能描述



模组允许为生成的怪物配置\*\*初始药水效果\*\*（即BUFF），使其在生成时自动获得指定的效果。这些效果可应用于所有怪物，也可针对特定阶段、特定规则或特定怪物类型单独配置。



\- \*\*兼容性\*\*：支持原版所有药水效果（如 `minecraft:speed`、`minecraft:strength`）以及其他模组注册的自定义效果（如 `irons\_spellbooks:haste`），通过效果注册名识别。

\- \*\*配置层级\*\*：可在\*\*全局\*\*、\*\*阶段\*\*、\*\*规则\*\*、\*\*怪物类型\*\*（普通/精英/Boss）和\*\*特定怪物\*\*（通过注册名）多个层级配置，优先级为：特定怪物 > 规则 > 阶段 > 怪物类型 > 全局。

\- \*\*效果合并\*\*：若多个层级配置了相同效果，以最高优先级为准（不叠加，仅覆盖）；若配置了不同效果，则全部生效（最多可叠加任意数量效果）。

\- \*\*概率机制\*\*：每个效果可独立配置出现概率，同一怪物可随机获得效果列表中的部分效果。



\### 6.2 配置方式



在 `spawn.mobEffects`（全局）或阶段/规则中的 `mobEffects` 字段，以JSON数组形式配置：



```json

"mobEffects": \[

&#x20; {

&#x20;   "effect": "minecraft:speed",      // 效果注册名（必填，支持模组效果）

&#x20;   "amplifier": 1,                   // 等级（整数，0为I级，1为II级……默认0）

&#x20;   "duration": 200,                  // 持续时间tick（整数，-1为无限，默认-1）

&#x20;   "chance": 0.5,                    // 获得该效果的概率（浮点数，0\~1，默认1.0）

&#x20;   "showParticles": true,            // 是否显示粒子效果（布尔，默认true）

&#x20;   "showIcon": true                  // 是否显示HUD图标（布尔，默认true）

&#x20; },

&#x20; {

&#x20;   "effect": "irons\_spellbooks:haste",

&#x20;   "amplifier": 0,

&#x20;   "duration": 300,

&#x20;   "chance": 0.8

&#x20; }

]

```



\### 6.3 配置层级与优先级



| 配置层级 | 配置路径 | 优先级 | 说明 |

|----------|----------|--------|------|

| 全局默认 | `spawn.mobEffects` | 最低（5级） | 所有怪物均继承 |

| 怪物类型覆盖 | `spawn.mobTypeEffects` | 4级 | 按NORMAL/ELITE/BOSS分别配置 |

| 阶段覆盖 | `stage.list\[].mobEffects` | 3级 | 覆盖全局和类型配置 |

| 规则覆盖 | `stage.list\[].rules\[].mobEffects` | 2级 | 覆盖阶段配置 |

| 特定怪物覆盖 | `spawn.specificMobEffects` | 最高（1级） | 按注册名精确匹配，完全覆盖 |



\*\*匹配逻辑\*\*：

1\. 若怪物注册名在 `specificMobEffects` 中有配置，则\*\*仅使用\*\*该列表（忽略其他所有层级）。

2\. 否则，若当前触发的规则中有 `mobEffects`，则使用该列表。

3\. 否则，若当前阶段有 `mobEffects`，则使用该列表。

4\. 否则，若怪物类型（NORMAL/ELITE/BOSS）在 `mobTypeEffects` 中有配置，则使用该列表。

5\. 否则，使用全局 `spawn.mobEffects`。



\### 6.4 新增配置参数



| 路径 | 类型 | 默认值 | 说明 |

|------|------|--------|------|

| `spawn.mobEffects` | 效果数组 | `\[]` | 全局默认效果列表 |

| `spawn.mobTypeEffects` | JSON对象 | `{}` | 按类型覆盖，键为 `"NORMAL"`/`"ELITE"`/`"BOSS"`，值为效果数组 |

| `spawn.specificMobEffects` | JSON对象 | `{}` | 按注册名覆盖，键为注册名（如 `"minecraft:zombie"`），值为效果数组 |



\*\*阶段/规则中的字段\*\*（在阶段对象或规则对象中）：

\- `mobEffects`：效果数组，覆盖上级配置。



\### 6.5 效果注册名获取



\- 原版效果：使用 `minecraft:effect\_name`（如 `minecraft:speed`）

\- 模组效果：使用模组的注册名（如 `irons\_spellbooks:haste`）

\- 可通过 `/monsterwaves effect list` 指令列出所有已注册的效果注册名。



\### 6.6 新增指令



| 指令 | 权限节点 | 说明 |

|------|----------|------|

| `/monsterwaves effect list` | `mw.command.effect` | 列出所有已注册的药水效果（含模组效果） |

| `/monsterwaves effect test <效果ID> \[等级] \[持续时间]` | `mw.command.effect` | 给玩家自身施加效果（用于测试） |



\---



\## 7. 属性球系统



\### 7.1 默认属性类型



| 属性类型 | 映射原版属性 | 颜色（默认） | 图标 | 默认权重 |

|----------|--------------|--------------|------|----------|

| `ATTACK` | `minecraft:generic.attack\_damage` | `#FF4444` | `sword` | 20 |

| `HEALTH` | `minecraft:generic.max\_health` | `#44FF44` | `heart` | 20 |

| `ARMOR` | `minecraft:generic.armor` | `#4444FF` | `shield` | 15 |

| `ARMOR\_TOUGHNESS` | `minecraft:generic.armor\_toughness` | `#FFAA00` | `chestplate` | 10 |

| `KNOCKBACK\_RESISTANCE` | `minecraft:generic.knockback\_resistance` | `#AA44FF` | `anvil` | 8 |

| `ATTACK\_SPEED` | `minecraft:generic.attack\_speed` | `#FF66AA` | `sword\_speed` | 10 |

| `MOVEMENT\_SPEED` | `minecraft:generic.movement\_speed` | `#66FFCC` | `boots` | 10 |



\### 7.2 配置参数



| 参数 | 类型 | 默认值 | 说明 |

|------|------|--------|------|

| `baseChanceNormal` | 浮点数 | `0.2` | 普通怪基础掉落率（0\~1） |

| `baseChanceElite` | 浮点数 | `0.5` | 精英基础掉落率（0\~1） |

| `baseChanceBoss` | 浮点数 | `1.0` | Boss基础掉落率（0\~1） |

| `valueNormal` | 整数 | `1` | 普通球加成点数 |

| `valueElite` | 整数 | `3` | 精英球加成点数 |

| `valueBoss` | 整数 | `5` | Boss球加成点数 |

| `attributeTypes` | 字符串列表 | 7种默认属性 | 可自由增删改属性类型 |

| `attributeColors` | JSON对象 | 见上表 | 各属性颜色（十六进制值） |

| `attributeIcons` | JSON对象 | 见上表 | 各属性图标标识 |

| `attributeMapping` | JSON对象 | 见上表 | 属性类型→Forge属性注册名 |

| `attributeWeights` | JSON对象 | 见上表 | 各属性掉落权重（整数≥0） |

| `weightAffectedByDifficulty` | 布尔 | `false` | 权重是否受难度影响 |

| `weightMultiplierPerDifficulty` | 浮点数 | `0.0` | 每点难度对权重的额外乘数 |

| `attributeMaxValues` | JSON对象 | `{}` | 各属性上限（-1=无限制） |

| `pickupMessage` | 布尔 | `true` | 拾取时聊天栏提示 |

| `pickupSound` | 字符串 | `"entity.experience\_orb.pickup"` | 拾取音效 |

| `pickupParticles` | 布尔 | `true` | 拾取粒子特效 |

| `autoAttractRange` | 整数 | `3` | 属性球自动飞向玩家的范围（格数），0=禁用 |

| `despawnTime` | 整数 | `6000` | 属性球存在时间（tick），超时消失 |



\### 7.3 权重计算逻辑



\*\*最终权重\*\* = 配置权重 × 难度影响系数



\*\*难度影响系数\*\* = 1 + (difficulty-1) × `weightMultiplierPerDifficulty`



\*\*实际概率\*\* = 该属性最终权重 / 所有属性最终权重之和



\*\*权重为0的处理\*\*：

\- 若某属性权重为`0`，则掉落时\*\*绝不会\*\*选择该属性类型

\- 若所有属性权重均为`0`，则属性球\*\*不会掉落\*\*（直接跳过）



\### 7.4 属性上限系统



\- 在`attributeMaxValues`中配置各属性上限（整数）

\- 拾取时若当前值 + 加成值 > 上限，则只加到上限，超出部分不生效

\- 若已达上限，属性球消耗但无增益，聊天栏提示`"你的 \[属性名] 已达到上限！"`

\- 上限检查在`AttributeBallPickupEvent`之后执行，事件中可修改上限值或取消



\### 7.5 属性球视觉与反馈



| 特性 | 说明 |

|------|------|

| 颜色区分 | 每种属性类型独立颜色（可配置），便于识别 |

| 图标/粒子 | 每种属性类型独立图标（可配置），拾取时播放对应粒子特效 |

| 拾取提示 | 拾取时聊天栏显示`"你获得了 +3 攻击力！"`（颜色对应属性颜色） |

| 拾取音效 | 可配置（默认经验球音效） |

| 自动吸引 | 进入`autoAttractRange`范围内自动飞向玩家 |



\### 7.6 自动清理系统（防堆积）



| 参数 | 类型 | 默认值 | 说明 |

|------|------|--------|------|

| `cleanup.enable` | 布尔 | `true` | 是否启用自动清理 |

| `cleanup.interval` | 整数 | `1200` | 清理检测间隔（tick，默认60秒） |

| `cleanup.maxCount` | 整数 | `500` | 单个维度中属性球最大数量，超过则清理最早生成的 |

| `cleanup.despawnTime` | 整数 | `12000` | 属性球存在时间（tick），超时自动消失 |

| `cleanup.ignoreChunkLoad` | 布尔 | `false` | 是否仅清理已加载区块中的属性球（提高性能） |

| `cleanup.autoAttractWhenCleaning` | 布尔 | `true` | 清理时是否尝试将属性球吸引至最近的玩家 |



\---



\## 8. 统一掉落系统



\### 8.1 核心概念



\*\*特定怪物掉落与类型掉落完全独立并行，互不影响\*\*：



```

怪物死亡

&#x20; │

&#x20; ├─ ① 特定怪物掉落 (specificLoot\[注册名]) ─→ 若有则执行（不阻断类型掉落）

&#x20; │                                       （若没有则跳过）

&#x20; │

&#x20; ├─ ② 类型掉落 (按怪物类型匹配) ─────────→ 执行对应的 normalLoot/eliteLoot/bossLoot

&#x20; │

&#x20; ├─ ③ 属性球掉落 (独立于上述) ──────────→ 按类型概率判定，按权重选择属性类型

&#x20; │

&#x20; └─ ④ 原版掉落 (若 overrideVanilla = false)

```



\### 8.2 配置参数



| 参数 | 类型 | 默认值 | 说明 |

|------|------|--------|------|

| `enable` | 布尔 | `true` | 是否启用统一掉落系统 |

| `overrideVanilla` | 布尔 | `false` | `true`=覆盖原版（仅使用统一掉落），`false`=追加原版 |

| `globalChanceMultiplier` | 浮点数 | `1.0` | 全局概率倍率（会乘以难度系数） |

| `normalLoot` | 条目列表 | `\[]` | 普通怪物掉落条目 |

| `eliteLoot` | 条目列表 | `\[]` | 精英怪物掉落条目 |

| `bossLoot` | 条目列表 | `\[]` | Boss掉落条目 |

| `specificLoot` | JSON对象 | `{}` | 特定怪物掉落（键=注册名，值=条目列表） |



\### 8.3 条目格式



```json

{

&#x20; "item": "minecraft:diamond",      // 物品注册名（支持模组物品）

&#x20; "nbt": "{CustomTag:1}",           // 可选，NBT字符串

&#x20; "minCount": 1,                    // 最小掉落数量

&#x20; "maxCount": 3,                    // 最大掉落数量

&#x20; "chance": 0.5,                    // 基础概率（0\~1）

&#x20; "isPlayerAffected": true          // 是否受抢夺附魔影响

}

```



\### 8.4 最终概率计算



\*\*最终概率\*\* = 条目`chance` × 当前维度`difficulty` × `drop.unified.globalChanceMultiplier` × `drop.difficulty.chanceMultiplier`



\*\*最终概率\*\* = `min(最终概率, 1.0)`（封顶为100%）



\### 8.5 物品数量计算



```

基础数量 = minCount + random(0, maxCount - minCount)

额外数量 = floor(drop.difficulty.extraCountPerLevel × (difficulty - 1))

最终数量 = 基础数量 + 额外数量

```



> `extraCountPerLevel`仅对物品掉落生效，属性球不受此参数影响。



\---



\## 9. 休息维度系统



\### 9.1 特性



| 特性 | 说明 |

|------|------|

| 不刷怪 | 本模组及原版均不生成任何怪物 |

| 不饥饿 | 饥饿值锁定为20，饱和度锁定为10 |

| 不死 | 免疫所有伤害，生命值最低为1 |

| 空岛 | 半径为20的圆形空岛 |

| 跳下传送 | 低于Y=-10时传送至指定维度的指定坐标 |



\### 9.2 配置参数



| 参数 | 类型 | 默认值 | 说明 |

|------|------|--------|------|

| `enabled` | 布尔 | `true` | 是否启用休息维度 |

| `dimensionId` | 字符串 | `"monsterwaves:safe"` | 维度ID |

| `islandRadius` | 整数 | `20` | 空岛半径（从中心向外延伸的方块数） |

| `islandBlock` | 字符串 | `"minecraft:grass\_block"` | 空岛方块类型 |

| `returnItem` | 字符串 | `"monsterwaves:return\_charm"` | 返回符咒物品ID |

| `giveOnJoin` | 布尔 | `true` | 首次加入时给予返回符咒 |

| `cooldown` | 整数 | `600` | 使用冷却（tick，默认30秒） |

| `fallTeleportY` | 整数 | `-10` | 触发传送的Y坐标 |

| `spawnPoint` | 位置对象 | `{x:0, y:65, z:0}` | 进入维度时的出生点 |

| `recipeEnabled` | 布尔 | `true` | 是否启用合成配方 |

| `fallDestinationDimension` | 字符串 | `"monsterwaves:arena"` | 坠落传送目标维度 |

| `fallDestinationX` | 整数 | `0` | 目标X坐标 |

| `fallDestinationY` | 整数 | `64` | 目标Y坐标 |

| `fallDestinationZ` | 整数 | `0` | 目标Z坐标 |

| `useCustomFallDestination` | 布尔 | `true` | 是否使用自定义坐标（`false`则使用目标维度的默认出生点） |



\---



\## 10. 刷怪维度系统



\### 10.1 特性



\- 超平坦地形（可配置层数）

\- 专用于战斗，所有生成规则在此生效

\- 默认玩家出生点在此维度



\### 10.2 配置参数



| 参数 | 类型 | 默认值 | 说明 |

|------|------|--------|------|

| `enabled` | 布尔 | `true` | 是否启用该维度 |

| `dimensionId` | 字符串 | `"monsterwaves:arena"` | 维度ID |

| `flatPreset` | 字符串 | `"minecraft:grass\_block;minecraft:dirt;minecraft:stone"` | 超平坦层配置（分号分隔，从上到下） |

| `spawnPoint` | 位置对象 | `{x:0, y:4, z:0}` | 玩家出生点 |



\---



\## 11. 传送符咒系统



\### 11.1 两种符咒对比



| 符咒 | 物品ID | 传送目标 | 获取方式 | 冷却（独立） |

|------|--------|----------|----------|-------------|

| 返回符咒 | `monsterwaves:return\_charm` | 休息维度 | 开局给予 / 合成 | `safeDimension.cooldown`（默认600 tick） |

| 战斗符咒 | `monsterwaves:battle\_charm` | 刷怪维度 | 开局给予 / 合成 | `battleCharm.cooldown`（默认600 tick） |



\### 11.2 返回符咒配置（`safeDimension.\*`）



| 参数 | 类型 | 默认值 | 说明 |

|------|------|--------|------|

| `returnItem` | 字符串 | `"monsterwaves:return\_charm"` | 物品ID |

| `giveOnJoin` | 布尔 | `true` | 开局给予 |

| `cooldown` | 整数 | `600` | 冷却时间（tick，30秒） |

| `recipeEnabled` | 布尔 | `true` | 是否启用合成配方 |



\*\*合成配方\*\*：4个末影珍珠 + 1个金苹果 → 1个返回符咒



\### 11.3 战斗符咒配置（`battleCharm.\*`）



| 参数 | 类型 | 默认值 | 说明 |

|------|------|--------|------|

| `enabled` | 布尔 | `true` | 是否启用战斗符咒 |

| `itemId` | 字符串 | `"monsterwaves:battle\_charm"` | 物品ID |

| `giveOnJoin` | 布尔 | `true` | 开局给予 |

| `cooldown` | 整数 | `600` | 冷却时间（tick，30秒） |

| `recipeEnabled` | 布尔 | `true` | 是否启用合成配方 |

| `recipeIngredients` | JSON对象 | `{"minecraft:ender\_pearl":4, "minecraft:iron\_sword":1}` | 配方材料（支持任意物品和数量） |

| `targetDimension` | 字符串 | `"monsterwaves:arena"` | 传送目标维度 |

| `spawnPoint` | 位置对象 | `{x:0, y:4, z:0}` | 传送到的坐标（若不填则使用目标维度的出生点） |



\*\*合成配方\*\*：4个末影珍珠 + 1把铁剑（任意耐久） → 1个战斗符咒



\---



\## 12. 玩家大范围拾取系统



\### 12.1 功能描述



\- 自动拾取玩家周围一定范围内的\*\*物品实体\*\*和\*\*属性球\*\*，无需手动靠近

\- 拾取动作模拟玩家主动拾取（触发原版拾取逻辑和属性球拾取事件）

\- 可分别控制是否拾取物品和属性球，以及拾取半径和检测频率



\### 12.2 配置参数



| 参数 | 类型 | 默认值 | 说明 |

|------|------|--------|------|

| `enable` | 布尔 | `true` | 是否启用大范围拾取 |

| `range` | 浮点数 | `6.0` | 拾取半径（格数） |

| `pickupItems` | 布尔 | `true` | 是否拾取普通物品实体 |

| `pickupAttributeBalls` | 布尔 | `true` | 是否拾取属性球 |

| `interval` | 整数 | `5` | 拾取检测间隔（tick） |

| `onlyOwnDrops` | 布尔 | `false` | 是否只拾取自己击杀产生的掉落物 |

| `blacklistItems` | 字符串列表 | `\[]` | 黑名单物品（注册名），不被拾取 |



\---



\## 13. 苦痛传递附魔



\### 13.1 附魔概述



| 项目 | 内容 |

|------|------|

| 附魔ID | `monsterwaves:pain\_transference` |

| 适用物品 | 剑、斧、Trident、\*\*TaCZ枪械\*\*（通过兼容层） |

| 最大等级 | V（5级） |

| 稀有度 | `RARITY.RARE` |

| 冲突附魔 | 无（独立效果） |

| 是否宝藏 | 否（可通过附魔台获得） |



\*\*效果\*\*：当玩家使用带有此附魔的武器命中一个怪物时，会对该怪物\*\*周围一定范围内\*\*的\*\*所有\*\*其他怪物造成伤害，伤害值为\*\*本次主伤害的百分比\*\*（百分比随附魔等级提升）。



> ⚠️ \*\*触发数量不限\*\*：只要在作用半径内的怪物均会受到伤害，由`baseRadius`和`radiusPerLevel`控制影响范围。



\### 13.2 配置参数



| 参数 | 类型 | 默认值 | 说明 |

|------|------|--------|------|

| `enabled` | 布尔 | `true` | 是否启用此附魔 |

| `maxLevel` | 整数 | `5` | 最大等级 |

| `baseRadius` | 浮点数 | `4.0` | 基础作用半径（格） |

| `radiusPerLevel` | 浮点数 | `1.0` | 每级增加的半径（格） |

| `baseDamagePercent` | 浮点数 | `0.15` | 基础传递伤害百分比（15%） |

| `damagePercentPerLevel` | 浮点数 | `0.05` | 每级增加的伤害百分比（+5%） |

| `affectSameTypeOnly` | 布尔 | `false` | 是否仅影响同类型怪物 |

| `affectEliteBoss` | 布尔 | `true` | 是否对精英/Boss怪物生效 |

| `excludeSource` | 布尔 | `true` | 是否排除被直接命中的目标 |

| `cooldown` | 整数 | `0` | 触发冷却（tick，0=无冷却） |

| `damageSourceMessage` | 字符串 | `"pain\_transference"` | 伤害源标识 |



\### 13.3 伤害计算



```

作用半径 = baseRadius + (附魔等级 - 1) × radiusPerLevel

传递伤害百分比 = baseDamagePercent + (附魔等级 - 1) × damagePercentPerLevel

传递伤害 = 主伤害 × 传递伤害百分比

```



\### 13.4 TaCZ 枪械兼容



由于TaCZ枪械的伤害通过`EntityKineticBullet`抛射物实现，不经过原版`LivingHurtEvent`的攻击者检测，需要特殊处理：



\- \*\*方案一：监听 ProjectileHitEvent（推荐）\*\*：TaCZ子弹命中时触发，从抛射物中获取发射者和枪械物品，检查附魔。

\- \*\*方案二：Mixin注入（备选）\*\*：若TaCZ未提供事件，通过Mixin在子弹命中处插入钩子。

\- \*\*兼容性保障\*\*：若未安装TaCZ，附魔仍可正常应用于原版武器。



\---



\## 14. 配置参数完整汇总



\### 14.1 全局启用



| 路径 | 类型 | 默认值 |

|------|------|--------|

| `enabled` | 布尔 | `true` |



\### 14.2 阶段系统（`stage.\*`）



| 路径 | 类型 | 默认值 |

|------|------|--------|

| `stage.enabled` | 布尔 | `true` |

| `stage.autoAdvance` | 布尔 | `true` |

| `stage.list` | JSON数组 | 预置示例（3个阶段） |

| `stage.currentIndex`（只读） | 整数 | `0` |



\### 14.3 生成设置（`spawn.\*`）



| 路径 | 类型 | 默认值 | 范围 |

|------|------|--------|------|

| `spawn.interval` | 整数 | `40` | 1\~200 |

| `spawn.minDistance` | 整数 | `20` | 5\~50 |

| `spawn.maxDistance` | 整数 | `32` | 25\~64 |

| `spawn.maxMobsPerPlayer` | 整数 | `30` | 5\~100 |

| `spawn.mobList` | 字符串列表 | `\["minecraft:zombie"]` | - |

| `spawn.mobWeights` | JSON对象 | `{"minecraft:zombie":5}` | - |

| `spawn.mobTypes` | JSON对象 | `{"minecraft:zombie":"NORMAL"}` | - |

| `spawn.eliteChance` | 浮点数 | `0.1` | 0\~1 |

| `spawn.bossChance` | 浮点数 | `0.02` | 0\~0.2 |

| `spawn.eliteHealthMultiplier` | 浮点数 | `2.0` | ≥1 |

| `spawn.eliteSpeedMultiplier` | 浮点数 | `1.3` | ≥0.5 |

| `spawn.bossHealthMultiplier` | 浮点数 | `4.0` | ≥1 |

| `spawn.bossSpeedMultiplier` | 浮点数 | `1.5` | ≥0.5 |

| `spawn.progressValue` | 整数 | `0` | ≥0 |



\### 14.4 难度参数（`spawn.difficulty.\*`）



| 路径 | 类型 | 默认值 |

|------|------|--------|

| `healthBonusPerLevel` | 浮点数 | `0.2` |

| `attackBonusPerLevel` | 浮点数 | `0.5` |

| `armorBonusPerLevel` | 浮点数 | `0.5` |

| `applyToEliteBoss` | 布尔 | `true` |



\### 14.5 怪物自带BUFF（`spawn.\*`）



| 路径 | 类型 | 默认值 |

|------|------|--------|

| `spawn.mobEffects` | 效果数组 | `\[]` |

| `spawn.mobTypeEffects` | JSON对象 | `{}` |

| `spawn.specificMobEffects` | JSON对象 | `{}` |



\*\*效果数组条目格式\*\*：

```json

{

&#x20; "effect": "minecraft:speed",      // 效果注册名（必填）

&#x20; "amplifier": 1,                   // 等级（整数，默认0）

&#x20; "duration": 200,                  // 持续时间tick（整数，默认-1无限）

&#x20; "chance": 0.5,                    // 概率（浮点数，0\~1，默认1.0）

&#x20; "showParticles": true,            // 是否显示粒子（布尔，默认true）

&#x20; "showIcon": true                  // 是否显示图标（布尔，默认true）

}

```



\### 14.6 属性球（`drop.ball.\*`）



| 路径 | 类型 | 默认值 |

|------|------|--------|

| `baseChanceNormal` | 浮点数 | `0.2` |

| `baseChanceElite` | 浮点数 | `0.5` |

| `baseChanceBoss` | 浮点数 | `1.0` |

| `valueNormal` | 整数 | `1` |

| `valueElite` | 整数 | `3` |

| `valueBoss` | 整数 | `5` |

| `attributeTypes` | 字符串列表 | 7种默认属性 |

| `attributeColors` | JSON对象 | 见7.1 |

| `attributeIcons` | JSON对象 | 见7.1 |

| `attributeMapping` | JSON对象 | 见7.1 |

| `attributeWeights` | JSON对象 | 见7.1 |

| `weightAffectedByDifficulty` | 布尔 | `false` |

| `weightMultiplierPerDifficulty` | 浮点数 | `0.0` |

| `attributeMaxValues` | JSON对象 | `{}` |

| `pickupMessage` | 布尔 | `true` |

| `pickupSound` | 字符串 | `"entity.experience\_orb.pickup"` |

| `pickupParticles` | 布尔 | `true` |

| `autoAttractRange` | 整数 | `3` |

| `despawnTime` | 整数 | `6000` |



\### 14.7 属性球清理（`drop.ball.cleanup.\*`）



| 路径 | 类型 | 默认值 |

|------|------|--------|

| `enable` | 布尔 | `true` |

| `interval` | 整数 | `1200` |

| `maxCount` | 整数 | `500` |

| `despawnTime` | 整数 | `12000` |

| `ignoreChunkLoad` | 布尔 | `false` |

| `autoAttractWhenCleaning` | 布尔 | `true` |



\### 14.8 统一掉落（`drop.unified.\*`）



| 路径 | 类型 | 默认值 |

|------|------|--------|

| `enable` | 布尔 | `true` |

| `overrideVanilla` | 布尔 | `false` |

| `globalChanceMultiplier` | 浮点数 | `1.0` |

| `normalLoot` | 条目列表 | `\[]` |

| `eliteLoot` | 条目列表 | `\[]` |

| `bossLoot` | 条目列表 | `\[]` |

| `specificLoot` | JSON对象 | `{}` |



\### 14.9 掉落难度修正（`drop.difficulty.\*`）



| 路径 | 类型 | 默认值 |

|------|------|--------|

| `chanceMultiplier` | 浮点数 | `1.0` |

| `extraCountPerLevel` | 浮点数 | `0` |



\### 14.10 属性转换（`attr.\*`）



| 路径 | 类型 | 默认值 |

|------|------|--------|

| `attackMultiplier` | 浮点数 | `0.5` |

| `healthMultiplier` | 浮点数 | `1.0` |

| `armorMultiplier` | 浮点数 | `0.5` |



\### 14.11 刷怪维度（`arenaDimension.\*`）



| 路径 | 类型 | 默认值 |

|------|------|--------|

| `enabled` | 布尔 | `true` |

| `dimensionId` | 字符串 | `"monsterwaves:arena"` |

| `flatPreset` | 字符串 | `"minecraft:grass\_block;minecraft:dirt;minecraft:stone"` |

| `spawnPoint` | 位置对象 | `{x:0, y:4, z:0}` |



\### 14.12 休息维度（`safeDimension.\*`）



| 路径 | 类型 | 默认值 |

|------|------|--------|

| `enabled` | 布尔 | `true` |

| `dimensionId` | 字符串 | `"monsterwaves:safe"` |

| `islandRadius` | 整数 | `20` |

| `islandBlock` | 字符串 | `"minecraft:grass\_block"` |

| `returnItem` | 字符串 | `"monsterwaves:return\_charm"` |

| `giveOnJoin` | 布尔 | `true` |

| `cooldown` | 整数 | `600` |

| `fallTeleportY` | 整数 | `-10` |

| `spawnPoint` | 位置对象 | `{x:0, y:65, z:0}` |

| `recipeEnabled` | 布尔 | `true` |

| `fallDestinationDimension` | 字符串 | `"monsterwaves:arena"` |

| `fallDestinationX` | 整数 | `0` |

| `fallDestinationY` | 整数 | `64` |

| `fallDestinationZ` | 整数 | `0` |

| `useCustomFallDestination` | 布尔 | `true` |



\### 14.13 战斗符咒（`battleCharm.\*`）



| 路径 | 类型 | 默认值 |

|------|------|--------|

| `enabled` | 布尔 | `true` |

| `itemId` | 字符串 | `"monsterwaves:battle\_charm"` |

| `giveOnJoin` | 布尔 | `true` |

| `cooldown` | 整数 | `600` |

| `recipeEnabled` | 布尔 | `true` |

| `recipeIngredients` | JSON对象 | `{"minecraft:ender\_pearl":4, "minecraft:iron\_sword":1}` |

| `targetDimension` | 字符串 | `"monsterwaves:arena"` |

| `spawnPoint` | 位置对象 | `{x:0, y:4, z:0}` |



\### 14.14 大范围拾取（`playerPickup.\*`）



| 路径 | 类型 | 默认值 |

|------|------|--------|

| `enable` | 布尔 | `true` |

| `range` | 浮点数 | `6.0` |

| `pickupItems` | 布尔 | `true` |

| `pickupAttributeBalls` | 布尔 | `true` |

| `interval` | 整数 | `5` |

| `onlyOwnDrops` | 布尔 | `false` |

| `blacklistItems` | 字符串列表 | `\[]` |



\### 14.15 苦痛传递附魔（`enchantment.painTransference.\*`）



| 路径 | 类型 | 默认值 |

|------|------|--------|

| `enabled` | 布尔 | `true` |

| `maxLevel` | 整数 | `5` |

| `baseRadius` | 浮点数 | `4.0` |

| `radiusPerLevel` | 浮点数 | `1.0` |

| `baseDamagePercent` | 浮点数 | `0.15` |

| `damagePercentPerLevel` | 浮点数 | `0.05` |

| `affectSameTypeOnly` | 布尔 | `false` |

| `affectEliteBoss` | 布尔 | `true` |

| `excludeSource` | 布尔 | `true` |

| `cooldown` | 整数 | `0` |

| `damageSourceMessage` | 字符串 | `"pain\_transference"` |



\### 14.16 维度覆盖（`dimensions.\*`）



```json

"dimensions": {

&#x20; "monsterwaves:arena": { "enabled": true },

&#x20; "minecraft:overworld": { "enabled": false },

&#x20; "minecraft:the\_nether": { "enabled": false },

&#x20; "minecraft:the\_end": { "enabled": false }

}

```



\---



\## 15. 指令系统



\### 15.1 根指令



所有指令以 `/monsterwaves` 为根（\*\*全称\*\*），支持Tab补全。维度参数可选，默认为玩家当前维度。维度参数格式为 `minecraft:overworld`、`monsterwaves:arena` 等。



> \*\*注意\*\*：原 `/mw` 已废弃，请使用 `/monsterwaves`。



\### 15.2 配置与状态类



| 指令 | 权限节点 | 说明 |

|------|----------|------|

| `/monsterwaves set <路径> <值> \[维度]` | `mw.config.set` | 修改任意配置参数 |

| `/monsterwaves reload \[维度]` | `mw.config.reload` | 重新加载配置文件 |

| `/monsterwaves stats \[玩家名] \[维度]` | `mw.command.stats` | 查看玩家属性累计加成值 |

| `/monsterwaves listmobs \[维度]` | `mw.command.debug` | 列出当前维度可用怪物池 |

| `/monsterwaves difficulty \[维度]` | `mw.command.stats` | 显示当前维度难度系数 |



\### 15.3 阶段管理



| 指令 | 权限节点 | 说明 |

|------|----------|------|

| `/monsterwaves stage info \[维度]` | `mw.stage.info` | 显示当前阶段信息 |

| `/monsterwaves stage set <阶段ID> \[维度]` | `mw.stage.set` | 切换至指定阶段 |

| `/monsterwaves stage next \[维度]` | `mw.stage.set` | 切换至下一阶段 |

| `/monsterwaves stage prev \[维度]` | `mw.stage.set` | 切换至上一阶段 |

| `/monsterwaves stage reset \[维度]` | `mw.stage.set` | 重置当前阶段计时器 |

| `/monsterwaves stage list \[维度]` | `mw.stage.list` | 列出所有阶段及其状态 |



\### 15.4 规则管理



| 指令 | 权限节点 | 说明 |

|------|----------|------|

| `/monsterwaves rule list \[维度]` | `mw.rule.list` | 列出所有规则及其状态 |

| `/monsterwaves rule toggle <规则ID> \[维度]` | `mw.rule.toggle` | 切换规则的启用/禁用状态 |

| `/monsterwaves rule trigger <规则ID> \[强度系数] \[维度]` | `mw.rule.trigger` | 立即强制执行指定规则 |

| `/monsterwaves rule add <JSON> \[维度]` | `mw.rule.add` | 添加一条新规则 |

| `/monsterwaves rule remove <规则ID> \[维度]` | `mw.rule.remove` | 删除指定规则 |



\### 15.5 掉落管理



| 指令 | 权限节点 | 说明 |

|------|----------|------|

| `/monsterwaves loot <类型> add <item> \[min] \[max] \[chance] \[nbt] \[维度]` | `mw.loot.add` | 向指定类型列表添加掉落条目 |

| `/monsterwaves loot <类型> remove <item> \[维度]` | `mw.loot.remove` | 移除匹配物品注册名的条目 |

| `/monsterwaves loot <类型> clear \[维度]` | `mw.loot.clear` | 清空指定类型列表 |

| `/monsterwaves loot <类型> list \[页数] \[维度]` | `mw.loot.list` | 分页显示指定类型掉落配置 |

| `/monsterwaves loot test <类型> \[次数] \[维度]` | `mw.loot.debug` | 模拟掉落测试 |



\### 15.6 调试生成



| 指令 | 权限节点 | 说明 |

|------|----------|------|

| `/monsterwaves spawn <生物注册名> \[数量] \[类型] \[难度覆盖] \[维度]` | `mw.command.spawn` | 生成指定生物（不触发`MobSpawnEvent`） |

| `/monsterwaves spawn random \[数量] \[维度]` | `mw.command.spawn` | 从怪物池按权重随机生成 |

| `/monsterwaves spawn <...> --drop \[维度]` | `mw.command.spawn` | 强制触发掉落流程 |



\### 15.7 玩家数据管理



| 指令 | 权限节点 | 说明 |

|------|----------|------|

| `/monsterwaves player add <玩家名> <属性类型> <数值>` | `mw.command.player` | 增加属性值 |

| `/monsterwaves player set <玩家名> <属性类型> <数值>` | `mw.command.player` | 强制设置属性值 |

| `/monsterwaves player progress <玩家名> <数值>` | `mw.command.player` | 修改进度值 |

| `/monsterwaves player reset <玩家名> \[--keep-progress]` | `mw.command.player` | 重置所有属性 |

| `/monsterwaves player list` | `mw.command.player` | 列出所有在线玩家 |

| `/monsterwaves player cap <玩家名> <属性类型>` | `mw.command.player` | 查看属性当前值和上限 |

| `/monsterwaves player cap set <玩家名> <属性类型> <上限值>` | `mw.command.player` | 设置临时属性上限 |



\### 15.8 属性球管理



| 指令 | 权限节点 | 说明 |

|------|----------|------|

| `/monsterwaves ball give <玩家名> <属性类型> <数量>` | `mw.command.ball` | 直接给予指定玩家属性球 |

| `/monsterwaves ball test <属性类型> \[数量]` | `mw.command.ball` | 在玩家面前生成测试属性球 |

| `/monsterwaves ball weights \[维度]` | `mw.command.ball` | 查看当前属性权重配置 |

| `/monsterwaves ball weight set <属性类型> <权重值>` | `mw.command.ball` | 动态设置属性权重 |

| `/monsterwaves ball cleanup \[维度]` | `mw.command.ball` | 立即执行一次属性球清理 |

| `/monsterwaves ball count \[维度]` | `mw.command.ball` | 统计当前维度中属性球数量 |



\### 15.9 属性映射管理



| 指令 | 权限节点 | 说明 |

|------|----------|------|

| `/monsterwaves attr list` | `mw.command.attr` | 列出所有可用属性注册名 |

| `/monsterwaves attr add <类型名> <注册名> \[颜色] \[图标]` | `mw.command.attr` | 动态添加属性类型 |

| `/monsterwaves attr remove <类型名>` | `mw.command.attr` | 移除属性类型 |



\### 15.10 效果管理（新增）



| 指令 | 权限节点 | 说明 |

|------|----------|------|

| `/monsterwaves effect list` | `mw.command.effect` | 列出所有已注册的药水效果（含模组效果） |

| `/monsterwaves effect test <效果ID> \[等级] \[持续时间]` | `mw.command.effect` | 给玩家自身施加效果（用于测试） |



\### 15.11 传送符咒



| 指令 | 权限节点 | 说明 |

|------|----------|------|

| `/monsterwaves safe` | `mw.command.safe` | 传送至休息维度（管理员，无冷却） |

| `/monsterwaves safe give \[玩家名] \[数量]` | `mw.command.safe` | 给予返回符咒 |

| `/monsterwaves battle` | `mw.command.battle` | 传送至刷怪维度（管理员，无冷却） |

| `/monsterwaves battle give \[玩家名] \[数量]` | `mw.command.battle` | 给予战斗符咒 |

| `/monsterwaves warp <维度ID>` | `mw.command.warp` | 传送至任意已注册维度 |



\### 15.12 大范围拾取



| 指令 | 权限节点 | 说明 |

|------|----------|------|

| `/monsterwaves pickup toggle` | `mw.command.pickup` | 切换当前玩家的临时拾取开关 |

| `/monsterwaves pickup range <数值>` | `mw.command.pickup` | 临时设置当前玩家的拾取半径 |



\### 15.13 附魔管理



| 指令 | 权限节点 | 说明 |

|------|----------|------|

| `/monsterwaves enchant give <玩家名> <附魔ID> \[等级]` | `mw.command.enchant` | 给玩家当前手持物品添加附魔 |

| `/monsterwaves enchant list` | `mw.command.enchant` | 列出所有可用附魔及其参数 |



\---



\## 16. 事件API



所有事件在\*\*服务端\*\*发布，通过Forge事件总线（`@SubscribeEvent`）监听。所有事件均提供`getDimension()`和`isDimensionEnabled()`。



\### 16.1 事件列表



| 事件 | 可取消 | 说明 |

|------|--------|------|

| `MobSpawnEvent` | ✅ | 生成前修改生成内容；`/monsterwaves spawn`不触发此事件 |

| `PostMobSpawnEvent` | ❌ | 怪物成功生成后；`/monsterwaves spawn`也触发 |

| `AttributeBallPickupEvent` | ✅ | 拾取属性球前，可修改加成值、上限 |

| `AttributeBallWeightCalculateEvent` | ❌ | 权重计算时，可动态修改权重映射 |

| `AttributeBallTypeSelectedEvent` | ✅ | 属性类型已选定后，可取消本次掉落 |

| `LootItemGenerateEvent` | ✅ | 掉落条目生成时，可替换物品 |

| `LootChanceCalculateEvent` | ❌ | 计算最终概率时，可修改概率值 |

| `StageChangeEvent` | ✅ | 阶段切换前，含新旧难度 |

| `PlayerProgressUpdateEvent` | ❌ | 进度值变化时 |

| `DimensionToggleEvent` | ❌ | 维度启用状态变化时 |

| `PlayerEnterSafeDimensionEvent` | ✅ | 即将进入休息维度 |

| `PlayerLeaveSafeDimensionEvent` | ✅ | 即将离开休息维度 |

| `PlayerPickupRangeEvent` | ✅ | 大范围拾取检测到可拾取实体时触发 |

| `BattleCharmUseEvent` | ✅ | 使用战斗符咒时 |

| `PainTransferenceEvent` | ✅ | 苦痛传递效果即将执行时 |

| `MobEffectApplyEvent` | ✅ | 怪物即将应用自带BUFF时（可修改效果列表） |



\### 16.2 关键事件方法详解



\#### `MobSpawnEvent`

| 方法 | 说明 |

|------|------|

| `Player getPlayer()` | 触发生成的玩家 |

| `BlockPos getSpawnPos()` | 拟生成位置 |

| `EntityType<?> getOriginalEntityType()` | 原本选中的生物类型 |

| `MobType getMobType()` | NORMAL/ELITE/BOSS |

| `int getCurrentProgress()` | 该玩家当前进度值 |

| `String getRuleId()` | 触发的规则ID |

| `String getCurrentStageId()` | 当前阶段ID |

| `float getCurrentDifficulty()` | 当前难度系数 |

| `List<MobEffectInstance> getEffects()` | 当前配置的效果列表（可修改） |

| `void setEntityType(EntityType<?>)` | 替换生物类型 |

| `void setMobType(MobType)` | 调整类型 |

| `void setSpawnPos(BlockPos)` | 修改生成位置 |



\#### `MobEffectApplyEvent`（新增）

| 方法 | 说明 |

|------|------|

| `LivingEntity getEntity()` | 即将被施加效果的怪物 |

| `MobType getMobType()` | 怪物类型 |

| `List<MobEffectInstance> getEffects()` | 将被施加的效果列表（\*\*可修改\*\*） |

| `String getSource()` | 效果来源（`"GLOBAL"`/`"TYPE"`/`"STAGE"`/`"RULE"`/`"SPECIFIC"`） |

| `void addEffect(MobEffectInstance)` | 添加效果 |

| `void removeEffect(MobEffect)` | 移除效果 |

| `void clearEffects()` | 清空所有效果 |



\#### `AttributeBallPickupEvent`

| 方法 | 说明 |

|------|------|

| `Player getPlayer()` | 拾取者 |

| `String getAttributeType()` | 属性类型名称 |

| `int getBaseBonus()` | 本次基础加成值 |

| `void setBonusValue(int)` | 修改加成值 |

| `int getCap()` | 该属性上限 |

| `void setCap(int)` | 修改上限 |

| `int getCurrentValue()` | 玩家当前该属性值 |



\#### `PainTransferenceEvent`

| 方法 | 说明 |

|------|------|

| `LivingEntity getAttacker()` | 攻击者（玩家） |

| `LivingEntity getSourceTarget()` | 被直接命中的怪物 |

| `List<LivingEntity> getAffectedTargets()` | 将被影响的目标列表（\*\*可修改\*\*） |

| `float getRadius()` | 当前作用半径 |

| `float getDamagePercent()` | 当前伤害百分比 |

| `float getMainDamage()` | 主伤害值 |

| `void setDamagePercent(float)` | 修改伤害百分比 |

| `void setRadius(float)` | 修改作用半径 |



\---



\## 17. 玩家数据存储



存储于玩家持久化NBT，键为`"monsterwaves\_data"`。



\### 17.1 数据结构



```json

{

&#x20; "attributes": {

&#x20;   "ATTACK": 10,

&#x20;   "HEALTH": 5,

&#x20;   "ARMOR": 3,

&#x20;   "ARMOR\_TOUGHNESS": 2,

&#x20;   "KNOCKBACK\_RESISTANCE": 1,

&#x20;   "ATTACK\_SPEED": 0,

&#x20;   "MOVEMENT\_SPEED": 2

&#x20; },

&#x20; "progress": 47,

&#x20; "safeDimensionCooldown": 342,

&#x20; "battleDimensionCooldown": 100,

&#x20; "hasReturnCharm": true,

&#x20; "hasBattleCharm": true,

&#x20; "pickupEnabled": true,

&#x20; "pickupRange": 6.0,

&#x20; "dimensions": {

&#x20;   "monsterwaves:arena": {

&#x20;     "currentStageIndex": 1,

&#x20;     "stageTimer": 3400

&#x20;   },

&#x20;   "minecraft:overworld": {

&#x20;     "currentStageIndex": 0,

&#x20;     "stageTimer": 1200

&#x20;   }

&#x20; }

}

```



\### 17.2 字段说明



| 字段 | 类型 | 说明 |

|------|------|------|

| `attributes` | JSON对象 | 各属性累计值（键=属性类型名） |

| `progress` | 整数 | 玩家当前进度值 |

| `safeDimensionCooldown` | 整数 | 返回符咒剩余冷却（tick） |

| `battleDimensionCooldown` | 整数 | 战斗符咒剩余冷却（tick） |

| `hasReturnCharm` | 布尔 | 是否拥有返回符咒 |

| `hasBattleCharm` | 布尔 | 是否拥有战斗符咒 |

| `pickupEnabled` | 布尔 | 大范围拾取临时开关 |

| `pickupRange` | 浮点数 | 大范围拾取临时半径 |

| `dimensions` | JSON对象 | 各维度的阶段状态（键=维度ID） |



\---



\## 18. 执行流程



\### 18.1 怪物生成流程



```

每 tick（受 spawn.interval 限制）

&#x20; │

&#x20; └─ 对于每个在线玩家：

&#x20;     ├─ 若当前维度被禁用 → 跳过

&#x20;     ├─ 若当前怪物数量 >= spawn.maxMobsPerPlayer → 跳过

&#x20;     ├─ 检查阶段自动推进（若 autoAdvance=true）：

&#x20;     │   └─ 当前阶段 duration 到期 → 触发 StageChangeEvent

&#x20;     │   └─ 若未取消 → 切换至下一阶段

&#x20;     ├─ 获取当前生效的规则集（来自阶段或全局）

&#x20;     ├─ 遍历所有 enabled=true 的规则：

&#x20;     │   ├─ 检查 trigger 条件是否满足

&#x20;     │   ├─ 检查 condition 附加条件是否满足

&#x20;     │   ├─ 若满足：

&#x20;     │   │   ├─ 选择生成位置（minDistance\~maxDistance间合法方块）

&#x20;     │   │   ├─ 从怪物池按权重选择生物注册名

&#x20;     │   │   ├─ 判定怪物类型（精英/Boss概率）

&#x20;     │   │   ├─ 发布 MobSpawnEvent（可取消/修改）

&#x20;     │   │   ├─ 若未取消：

&#x20;     │   │   │   ├─ 实例化生物

&#x20;     │   │   │   ├─ 应用难度系数计算属性（生命/攻击/护甲）

&#x20;     │   │   │   ├─ 应用精英/Boss强化

&#x20;     │   │   │   ├─ 获取该怪物应获得的效果列表（按优先级合并）

&#x20;     │   │   │   ├─ 发布 MobEffectApplyEvent（可修改效果列表）

&#x20;     │   │   │   ├─ 应用所有效果（每个效果按 chance 概率判定）

&#x20;     │   │   │   ├─ 生成到世界

&#x20;     │   │   │   └─ 发布 PostMobSpawnEvent

&#x20;     │   │   └─ 继续下一个规则

&#x20;     │   └─ 继续下一个规则

&#x20;     └─ 更新玩家进度（若规则为 PROGRESS 类型）

```



\### 18.2 效果列表合并流程



```

获取怪物效果列表：

&#x20; │

&#x20; ├─ 1. 若 specificMobEffects\[注册名] 存在 → 使用该列表（最高优先级）

&#x20; │

&#x20; ├─ 2. 否则，若当前规则有 mobEffects → 使用该列表

&#x20; │

&#x20; ├─ 3. 否则，若当前阶段有 mobEffects → 使用该列表

&#x20; │

&#x20; ├─ 4. 否则，若 mobTypeEffects\[类型] 存在 → 使用该列表

&#x20; │

&#x20; └─ 5. 否则，使用全局 spawn.mobEffects（最低优先级）

&#x20; │

&#x20; └─ 发布 MobEffectApplyEvent（可修改）

&#x20; └─ 对列表中每个效果，按 chance 概率判定是否实际应用

```



\### 18.3 属性球拾取流程



```

玩家接触属性球（或大范围拾取触发）

&#x20; │

&#x20; ├─ 判断属性类型是否存在于 attributeMapping 中

&#x20; │   └─ 不存在 → 记录警告，属性球消失

&#x20; │

&#x20; ├─ 触发 AttributeBallPickupEvent（可取消）

&#x20; │   └─ 可修改加成值、上限

&#x20; │

&#x20; ├─ 若事件取消 → 结束

&#x20; │

&#x20; ├─ 检查 attributeMaxValues 上限：

&#x20; │   ├─ 当前值 >= 上限 → 提示已达上限，属性球消耗

&#x20; │   ├─ 当前值 + 加成 > 上限 → 只加到上限

&#x20; │   └─ 正常 → 当前值 += 加成值

&#x20; │

&#x20; ├─ 应用属性值：

&#x20; │   ├─ 若映射到 Forge 原生属性 → 修改 AttributeInstance

&#x20; │   └─ 若映射到非原生属性 → 发布 CustomAttributeApplyEvent

&#x20; │

&#x20; ├─ 播放音效/粒子/提示

&#x20; │

&#x20; └─ 属性球实体消失

```



\### 18.4 苦痛传递触发流程



```

玩家造成伤害（任意来源）

&#x20; │

&#x20; ├─ 检查攻击者是否持有带苦痛传递附魔的武器

&#x20; │   ├─ 原版近战 → 直接从主手物品读取附魔

&#x20; │   └─ TaCZ枪械 → 从开枪时持有的物品读取附魔（NBT存储）

&#x20; │

&#x20; ├─ 若附魔等级 > 0：

&#x20; │   ├─ 获取被命中实体位置

&#x20; │   ├─ 计算作用半径和伤害百分比

&#x20; │   ├─ 获取半径内所有LivingEntity（排除源目标、自己、玩家）

&#x20; │   │   └─ 数量不限，仅受半径控制

&#x20; │   ├─ 应用过滤条件（affectSameTypeOnly、affectEliteBoss、excludeSource）

&#x20; │   ├─ 触发 PainTransferenceEvent（可修改目标列表和参数）

&#x20; │   └─ 对每个剩余目标应用伤害（魔法伤害，类型标识为 damageSourceMessage）

&#x20; └─ 若未检测到附魔 → 跳过

```



\---



\## 19. Cloth Config GUI布局



\### 19.1 主标签页



| 标签页 | 内容 |

|--------|------|

| \*\*全局配置\*\* | 全局启用开关、所有通用配置 |

| \*\*维度覆盖\*\* | 添加/删除维度覆盖，每个维度可独立配置所有参数 |



\### 19.2 全局配置子面板



| 面板 | 内容 |

|------|------|

| \*\*阶段系统\*\* | `stage.enabled`、`stage.autoAdvance`、阶段列表编辑器 |

| \*\*生成设置\*\* | `spawn.interval`、距离、最大怪物数、怪物池、精英/Boss概率与倍率 |

| \*\*难度影响\*\* | `spawn.difficulty.\*` 四个参数 |

| \*\*怪物BUFF\*\* | 全局效果列表、类型效果覆盖、特定怪物效果覆盖 |

| \*\*属性球配置\*\* | 所有 `drop.ball.\*` 参数 |

| \*\*属性球清理\*\* | `drop.ball.cleanup.\*` 五个参数 |

| \*\*统一掉落\*\* | `drop.unified.\*` 参数 |

| \*\*特定怪物掉落\*\* | `drop.unified.specificLoot` 键值对编辑器 |

| \*\*掉落难度修正\*\* | `drop.difficulty.\*` 两个参数 |

| \*\*属性转换\*\* | `attr.\*` 三个参数 |

| \*\*刷怪维度\*\* | `arenaDimension.\*` 四个参数 |

| \*\*休息维度\*\* | `safeDimension.\*` 全部参数 |

| \*\*战斗符咒\*\* | `battleCharm.\*` 全部参数 |

| \*\*玩家便利\*\* | `playerPickup.\*` 全部参数 |

| \*\*附魔配置\*\* | `enchantment.painTransference.\*` 全部参数 |



\---



\## 20. 兼容性与注意事项



\### 20.1 Forge版本适配



| 项目 | 说明 |

|------|------|

| 事件订阅 | 使用`@SubscribeEvent(priority = EventPriority.HIGHEST)`确保生成拦截优先级最高 |

| 配置加载 | 使用`@Config(modid = "monsterwaves", type = ConfigType.SERVER)` |

| 维度注册 | Forge 47.4.10中使用`LevelStem` + `DimensionType`注册自定义维度 |

| 网络同步 | 玩家属性数据通过`SimpleChannel`同步至客户端（仅用于显示） |



\### 20.2 跨模组兼容



| 项目 | 说明 |

|------|------|

| 生物注册名 | 通过`ForgeRegistries.ENTITY\_TYPES`解析，支持其他模组生物 |

| 物品注册名 | 通过`ForgeRegistries.ITEMS`解析，支持其他模组物品 |

| 属性注册名 | 通过`ForgeRegistries.ATTRIBUTES`解析，支持任意模组注册的属性 |

| 效果注册名 | 通过`ForgeRegistries.MOB\_EFFECTS`解析，支持原版与模组效果 |

| TaCZ枪械 | 通过`ProjectileHitEvent`监听或Mixin注入实现附魔兼容 |

| JEI/EMI | 可配置显示属性球、符咒的获取方式和用途 |



\### 20.3 已知限制与设计决策



| 限制 | 说明 |

|------|------|

| 速度不受难度影响 | 设计如此，仅由精英/Boss速度倍率决定 |

| 阶段不自动递增难度 | `difficulty`完全由配置决定，需手动配置不同阶段的不同值 |

| 特定怪物掉落独立 | 不覆盖类型掉落，两者并行生效 |



\### 20.4 性能建议



| 项目 | 建议 |

|------|------|

| 生成间隔 | 不宜过小（默认40 tick），避免服务器卡顿 |

| 最大怪物数 | 建议不超过50，视服务器性能调整 |

| 刷怪维度 | 建议使用刷怪维度而非主世界，避免污染原版世界 |

| 属性球清理 | 默认启用，防止属性球堆积影响性能 |



\---



\## 21. 完整默认配置示例



```json

{

&#x20; "enabled": true,

&#x20; "stage": {

&#x20;   "enabled": true,

&#x20;   "autoAdvance": true,

&#x20;   "list": \[

&#x20;     {

&#x20;       "id": "萌芽期",

&#x20;       "difficulty": 1.0,

&#x20;       "duration": 6000,

&#x20;       "rules": \[

&#x20;         {

&#x20;           "id": "time\_wave",

&#x20;           "enabled": true,

&#x20;           "trigger": {"type": "TIME", "value": 400, "reset\_on\_trigger": true},

&#x20;           "scaling": {"count\_modifier": 1.0}

&#x20;         }

&#x20;       ],

&#x20;       "mobListOverride": \["minecraft:zombie", "minecraft:skeleton"],

&#x20;       "eliteChanceOverride": 0.05,

&#x20;       "bossChanceOverride": 0.01,

&#x20;       "attributeMultipliers": {"healthMultiplier": 1.0, "attackMultiplier": 1.0, "armorMultiplier": 1.0},

&#x20;       "mobEffects": \[]

&#x20;     },

&#x20;     {

&#x20;       "id": "激战期",

&#x20;       "difficulty": 2.5,

&#x20;       "duration": 12000,

&#x20;       "rules": \[

&#x20;         {

&#x20;           "id": "time\_wave",

&#x20;           "enabled": true,

&#x20;           "trigger": {"type": "TIME", "value": 250, "reset\_on\_trigger": true},

&#x20;           "scaling": {"count\_modifier": 1.5, "elite\_chance\_modifier": 0.1}

&#x20;         }

&#x20;       ],

&#x20;       "mobListOverride": \["minecraft:zombie", "minecraft:skeleton", "minecraft:creeper"],

&#x20;       "eliteChanceOverride": 0.15,

&#x20;       "bossChanceOverride": 0.05,

&#x20;       "attributeMultipliers": {"healthMultiplier": 1.5, "attackMultiplier": 1.2, "armorMultiplier": 1.0},

&#x20;       "mobEffects": \[]

&#x20;     },

&#x20;     {

&#x20;       "id": "终局之战",

&#x20;       "difficulty": 5.0,

&#x20;       "duration": -1,

&#x20;       "rules": \[

&#x20;         {

&#x20;           "id": "time\_wave",

&#x20;           "enabled": true,

&#x20;           "trigger": {"type": "TIME", "value": 150, "reset\_on\_trigger": true},

&#x20;           "scaling": {"count\_modifier": 2.0, "elite\_chance\_modifier": 0.2, "boss\_chance\_modifier": 0.1}

&#x20;         }

&#x20;       ],

&#x20;       "mobListOverride": \["minecraft:zombie", "minecraft:skeleton", "minecraft:creeper", "minecraft:ender\_dragon"],

&#x20;       "eliteChanceOverride": 0.3,

&#x20;       "bossChanceOverride": 0.15,

&#x20;       "attributeMultipliers": {"healthMultiplier": 2.0, "attackMultiplier": 1.5, "armorMultiplier": 1.5},

&#x20;       "mobEffects": \[

&#x20;         {

&#x20;           "effect": "minecraft:strength",

&#x20;           "amplifier": 1,

&#x20;           "duration": -1,

&#x20;           "chance": 0.5,

&#x20;           "showParticles": true,

&#x20;           "showIcon": true

&#x20;         },

&#x20;         {

&#x20;           "effect": "minecraft:speed",

&#x20;           "amplifier": 0,

&#x20;           "duration": -1,

&#x20;           "chance": 0.3,

&#x20;           "showParticles": true,

&#x20;           "showIcon": true

&#x20;         }

&#x20;       ]

&#x20;     }

&#x20;   ]

&#x20; },

&#x20; "spawn": {

&#x20;   "interval": 40,

&#x20;   "minDistance": 20,

&#x20;   "maxDistance": 32,

&#x20;   "maxMobsPerPlayer": 30,

&#x20;   "mobList": \["minecraft:zombie"],

&#x20;   "mobWeights": {"minecraft:zombie": 5},

&#x20;   "mobTypes": {"minecraft:zombie": "NORMAL"},

&#x20;   "eliteChance": 0.1,

&#x20;   "bossChance": 0.02,

&#x20;   "eliteHealthMultiplier": 2.0,

&#x20;   "eliteSpeedMultiplier": 1.3,

&#x20;   "bossHealthMultiplier": 4.0,

&#x20;   "bossSpeedMultiplier": 1.5,

&#x20;   "difficulty": {

&#x20;     "healthBonusPerLevel": 0.2,

&#x20;     "attackBonusPerLevel": 0.5,

&#x20;     "armorBonusPerLevel": 0.5,

&#x20;     "applyToEliteBoss": true

&#x20;   },

&#x20;   "mobEffects": \[],

&#x20;   "mobTypeEffects": {},

&#x20;   "specificMobEffects": {}

&#x20; },

&#x20; "drop": {

&#x20;   "ball": {

&#x20;     "baseChanceNormal": 0.2,

&#x20;     "baseChanceElite": 0.5,

&#x20;     "baseChanceBoss": 1.0,

&#x20;     "valueNormal": 1,

&#x20;     "valueElite": 3,

&#x20;     "valueBoss": 5,

&#x20;     "attributeTypes": \["ATTACK", "HEALTH", "ARMOR", "ARMOR\_TOUGHNESS", "KNOCKBACK\_RESISTANCE", "ATTACK\_SPEED", "MOVEMENT\_SPEED"],

&#x20;     "attributeColors": {

&#x20;       "ATTACK": "#FF4444",

&#x20;       "HEALTH": "#44FF44",

&#x20;       "ARMOR": "#4444FF",

&#x20;       "ARMOR\_TOUGHNESS": "#FFAA00",

&#x20;       "KNOCKBACK\_RESISTANCE": "#AA44FF",

&#x20;       "ATTACK\_SPEED": "#FF66AA",

&#x20;       "MOVEMENT\_SPEED": "#66FFCC"

&#x20;     },

&#x20;     "attributeIcons": {

&#x20;       "ATTACK": "sword",

&#x20;       "HEALTH": "heart",

&#x20;       "ARMOR": "shield",

&#x20;       "ARMOR\_TOUGHNESS": "chestplate",

&#x20;       "KNOCKBACK\_RESISTANCE": "anvil",

&#x20;       "ATTACK\_SPEED": "sword\_speed",

&#x20;       "MOVEMENT\_SPEED": "boots"

&#x20;     },

&#x20;     "attributeMapping": {

&#x20;       "ATTACK": "minecraft:generic.attack\_damage",

&#x20;       "HEALTH": "minecraft:generic.max\_health",

&#x20;       "ARMOR": "minecraft:generic.armor",

&#x20;       "ARMOR\_TOUGHNESS": "minecraft:generic.armor\_toughness",

&#x20;       "KNOCKBACK\_RESISTANCE": "minecraft:generic.knockback\_resistance",

&#x20;       "ATTACK\_SPEED": "minecraft:generic.attack\_speed",

&#x20;       "MOVEMENT\_SPEED": "minecraft:generic.movement\_speed"

&#x20;     },

&#x20;     "attributeWeights": {

&#x20;       "ATTACK": 20,

&#x20;       "HEALTH": 20,

&#x20;       "ARMOR": 15,

&#x20;       "ARMOR\_TOUGHNESS": 10,

&#x20;       "KNOCKBACK\_RESISTANCE": 8,

&#x20;       "ATTACK\_SPEED": 10,

&#x20;       "MOVEMENT\_SPEED": 10

&#x20;     },

&#x20;     "weightAffectedByDifficulty": false,

&#x20;     "weightMultiplierPerDifficulty": 0.0,

&#x20;     "attributeMaxValues": {

&#x20;       "MOVEMENT\_SPEED": 5,

&#x20;       "ATTACK\_SPEED": 10

&#x20;     },

&#x20;     "pickupMessage": true,

&#x20;     "pickupSound": "entity.experience\_orb.pickup",

&#x20;     "pickupParticles": true,

&#x20;     "autoAttractRange": 3,

&#x20;     "despawnTime": 6000,

&#x20;     "cleanup": {

&#x20;       "enable": true,

&#x20;       "interval": 1200,

&#x20;       "maxCount": 500,

&#x20;       "despawnTime": 12000,

&#x20;       "ignoreChunkLoad": false,

&#x20;       "autoAttractWhenCleaning": true

&#x20;     }

&#x20;   },

&#x20;   "unified": {

&#x20;     "enable": true,

&#x20;     "overrideVanilla": false,

&#x20;     "globalChanceMultiplier": 1.0,

&#x20;     "normalLoot": \[],

&#x20;     "eliteLoot": \[],

&#x20;     "bossLoot": \[],

&#x20;     "specificLoot": {

&#x20;       "minecraft:creeper": \[

&#x20;         {"item": "minecraft:gunpowder", "minCount": 2, "maxCount": 4, "chance": 1.0}

&#x20;       ]

&#x20;     }

&#x20;   },

&#x20;   "difficulty": {

&#x20;     "chanceMultiplier": 1.0,

&#x20;     "extraCountPerLevel": 0

&#x20;   }

&#x20; },

&#x20; "attr": {

&#x20;   "attackMultiplier": 0.5,

&#x20;   "healthMultiplier": 1.0,

&#x20;   "armorMultiplier": 0.5

&#x20; },

&#x20; "arenaDimension": {

&#x20;   "enabled": true,

&#x20;   "dimensionId": "monsterwaves:arena",

&#x20;   "flatPreset": "minecraft:grass\_block;minecraft:dirt;minecraft:stone",

&#x20;   "spawnPoint": {"x": 0, "y": 4, "z": 0}

&#x20; },

&#x20; "safeDimension": {

&#x20;   "enabled": true,

&#x20;   "dimensionId": "monsterwaves:safe",

&#x20;   "islandRadius": 20,

&#x20;   "islandBlock": "minecraft:grass\_block",

&#x20;   "returnItem": "monsterwaves:return\_charm",

&#x20;   "giveOnJoin": true,

&#x20;   "cooldown": 600,

&#x20;   "fallTeleportY": -10,

&#x20;   "spawnPoint": {"x": 0, "y": 65, "z": 0},

&#x20;   "recipeEnabled": true,

&#x20;   "fallDestinationDimension": "monsterwaves:arena",

&#x20;   "fallDestinationX": 0,

&#x20;   "fallDestinationY": 64,

&#x20;   "fallDestinationZ": 0,

&#x20;   "useCustomFallDestination": true

&#x20; },

&#x20; "battleCharm": {

&#x20;   "enabled": true,

&#x20;   "itemId": "monsterwaves:battle\_charm",

&#x20;   "giveOnJoin": true,

&#x20;   "cooldown": 600,

&#x20;   "recipeEnabled": true,

&#x20;   "recipeIngredients": {

&#x20;     "minecraft:ender\_pearl": 4,

&#x20;     "minecraft:iron\_sword": 1

&#x20;   },

&#x20;   "targetDimension": "monsterwaves:arena",

&#x20;   "spawnPoint": {"x": 0, "y": 4, "z": 0}

&#x20; },

&#x20; "playerPickup": {

&#x20;   "enable": true,

&#x20;   "range": 6.0,

&#x20;   "pickupItems": true,

&#x20;   "pickupAttributeBalls": true,

&#x20;   "interval": 5,

&#x20;   "onlyOwnDrops": false,

&#x20;   "blacklistItems": \[]

&#x20; },

&#x20; "enchantment": {

&#x20;   "painTransference": {

&#x20;     "enabled": true,

&#x20;     "maxLevel": 5,

&#x20;     "baseRadius": 4.0,

&#x20;     "radiusPerLevel": 1.0,

&#x20;     "baseDamagePercent": 0.15,

&#x20;     "damagePercentPerLevel": 0.05,

&#x20;     "affectSameTypeOnly": false,

&#x20;     "affectEliteBoss": true,

&#x20;     "excludeSource": true,

&#x20;     "cooldown": 0,

&#x20;     "damageSourceMessage": "pain\_transference"

&#x20;   }

&#x20; },

&#x20; "dimensions": {

&#x20;   "monsterwaves:arena": {"enabled": true},

&#x20;   "minecraft:overworld": {"enabled": false},

&#x20;   "minecraft:the\_nether": {"enabled": false},

&#x20;   "minecraft:the\_end": {"enabled": false}

&#x20; }

}

```



\---



\## 附录：快速参考



\### 核心配置路径速查



| 模块 | 路径前缀 |

|------|----------|

| 全局启用 | `enabled` |

| 阶段系统 | `stage.` |

| 生成设置 | `spawn.` |

| 难度参数 | `spawn.difficulty.` |

| 怪物BUFF | `spawn.mobEffects`、`spawn.mobTypeEffects`、`spawn.specificMobEffects` |

| 属性球 | `drop.ball.` |

| 属性球清理 | `drop.ball.cleanup.` |

| 统一掉落 | `drop.unified.` |

| 掉落难度修正 | `drop.difficulty.` |

| 属性转换 | `attr.` |

| 刷怪维度 | `arenaDimension.` |

| 休息维度 | `safeDimension.` |

| 战斗符咒 | `battleCharm.` |

| 大范围拾取 | `playerPickup.` |

| 苦痛传递附魔 | `enchantment.painTransference.` |

| 维度覆盖 | `dimensions.<维度ID>.` |



\### 常用指令速查



| 目的 | 指令 |

|------|------|

| 查看阶段 | `/monsterwaves stage info` |

| 切换阶段 | `/monsterwaves stage set <ID>` |

| 查看属性 | `/monsterwaves stats` |

| 传送安全区 | `/monsterwaves safe` |

| 传送战场 | `/monsterwaves battle` |

| 生成测试怪物 | `/monsterwaves spawn <生物> \[数量] \[类型]` |

| 列出可用效果 | `/monsterwaves effect list` |

| 设置玩家属性 | `/monsterwaves player add <玩家> <属性> <数值>` |



