# Speedrun Switch | 接力速通

<div align="center">

[![Minecraft](https://img.shields.io/badge/Minecraft-%3E%3D%2026.3-brightgreen.svg?logo=minecraft)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric%200.19.5+-blue.svg?logo=fabric)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25-orange.svg?logo=openjdk)](https://adoptium.net/)
[![Simple Voice Chat](https://img.shields.io/badge/Voice%20Chat-Optional%202.6.20+-purple.svg)](https://modrinth.com/plugin/simple-voice-chat)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)

**Speedrunning Minecraft, but Every 10 Minutes the Player Changes!**  
**速通 Minecraft，但每 10 分钟更换玩家！**

[English](#english) • [中文说明](#chinese)

</div>

---

<a name="english"></a>
## English

**Speedrun Switch** is a Fabric mod designed for cooperative multiplayer relay speedrunning in Minecraft.  
During the game, **only 1 player has control at a time**, while all other teammates remain in spectator mode and are camera-bound to the active player. Every 10 minutes (customizable), control automatically rotates to the next player, with the ultimate goal of defeating the Ender Dragon (or simply playing as a casual relay survival indefinitely).

---

### ✨ Key Features

- 🔄 **Relay Rotation & Instant Disconnect Safeguard**:
  - Automatically switches the active runner every 10 minutes, while other players remain in spectator mode watching.
  - The relay order is determined by factors such as players' spectating time and cumulative control time, with the order decided randomly based on weights.
  - When switching players, a runner is selected randomly from currently online players.
  - **Instant Disconnect Safeguard**: The moment the active runner disconnects, the system immediately transfers control to the next online player.
  - Spectators can press the <kbd>E</kbd> key to inspect equipment and items in real-time.
  - Based on the native spectator attachment mechanism, high-frequency camera lock packets are sent to strictly prohibit spectators from detaching their camera to scout for resources or call out locations.
  - Hides coordinates, biomes, and chunk information from spectator players.
  - Chat messages sent by players are only echoed to themselves to prevent illegal callouts and ghosting via in-game chat; system announcements and quest information are received normally by all players.
- 🎯 **Dynamic Quest Reward System**:
  - Randomly issues 0~1 exclusive quest with a delay of 0~300 seconds after each rotation. Built-in quests: slaying hostile mobs, killing Endermen, mining stone, dealing damage, picking up food, entering the Nether/End.
  - **Supports Data-Driven Custom Quests**: Without modifying any code, you can add quests for any entity (`kill_entity`), item (`collect_item`), or block (`mine_block`) directly in the configuration file.
  - Quest rewards (extended control time) and timeout penalties (inflicting slowness effect or deducting time).
- 🎙️ **Simple Voice Chat Spectator Channel Voice Isolation**:
  - Spectators automatically join the "Spectator Channel" to chat freely; the active runner receives no voice messages.
- 📊 **Detailed Match Statistics & Checkpoint Recovery**:
  - Records each player's active control time, death count, and overall total elapsed time in real time.
  - Automatically announces speedrun victory upon defeating the Ender Dragon, freezing the timer and broadcasting the completion battle report.
  - **Checkpoint Recovery (`/speedrun resume`)**: Interrupted speedrun runs can be resumed from the save at any time after an unexpected server restart or crash.
- 🖥️ **Client Immersive HUD**:
  - Top-left screen HUD displaying the current runner and countdown timer.

---

### ⌨️ Command List

| Command | Required Permission | Description |
| :--- | :---: | :--- |
| `/speedrun start [player]` | OP | Start the relay speedrun (with opening countdown and sound effects, optionally specify the starting runner) |
| `/speedrun resume` | OP | Resume the last interrupted speedrun run from the save |
| `/speedrun switch [player]` | OP | Manually transfer control immediately (optionally specify a player to transfer to) |
| `/speedrun time [set] <seconds>` | OP | Directly set the current runner's remaining time (e.g. `time 600` sets to 600 seconds, supports `time add` to adjust) |
| `/speedrun stats` | Available to all | View current run elapsed time, completion status, each player's control time and death count |
| `/speedrun unstuck [player]` | Available to all (Self) / OP 2 (Others) | Camera detachment/stuck recovery (resets attachment and re-follows the active runner) |
| `/speedrun end` | OP | End the current speedrun (preserves stats, restores everyone to survival with inventory kept) |
| `/speedrun reset` | OP | Clear statistical data and reset to idle unstarted state |
| `/speedrun help` | Available to all | View the interactive command help menu |

---

### ⚙️ Configuration File (`config/speedrun-switch.json`)

| Config Option | Default | Detailed Description |
| :--- | :---: | :--- |
| `switchIntervalMinutes` | `10` | Automatic rotation cycle (minutes) |
| `countdownSeconds` | `10` | Pre-rotation countdown duration (seconds) |
| `startCountdownSeconds` | `3` | Start countdown duration (seconds) |
| `autoSwitchEnabled` | `true` | Whether to enable automatic timed rotation (`false` allows switching only via commands) |
| `minimumPlayersForSwitch` | `2` | Minimum online players required for rotation (skips rotation with prompt if below) |
| `taskEnabled` | `true` | Whether to enable the per-round quest system |
| `taskIntervalSecondsMin` / `Max` | `0` / `300` | Random quest issuance delay interval after rotation (seconds) |
| `taskTimeLimitSeconds` | `120` | Quest timeout limit (seconds, automatically truncated upon rotation) |
| `taskRewardSeconds` | `30` | Bonus control time awarded upon quest completion (seconds) |
| `taskPenaltySeconds` | `30` | Control time deducted upon quest timeout (effective under `TIME` mode) |
| `taskPenaltyMode` | `SLOWNESS` | Penalty mode: `SLOWNESS` (inflict slowness effect) or `TIME` (deduct time) |
| `taskTemplates` | See above config | **Quest pool configuration**: supports custom quest types, target counts, weights, and generic target IDs |
| `voiceChatEnabled` | `true` | Whether to enable Simple Voice Chat spectator channel auto-isolation |
| `spectatorGroupName` | `观众频道` | Spectator group name created in the voice chat plugin |
| `blockDebugForSpectators` | `true` | Whether to block spectators' F3 debug information |

#### Custom Quest Configuration Template
Quest templates support generic types; fill in the corresponding namespaced ID to take effect:

```json
"taskTemplates": [
  { "id": "kill_entity", "target": "minecraft:blaze", "count": 3, "weight": 2 },
  { "id": "collect_item", "target": "minecraft:ender_pearl", "count": 4, "weight": 2 },
  { "id": "mine_block", "target": "minecraft:ancient_debris", "count": 1, "weight": 1 }
]
```

---

### 💡 Inspiration & Credits
Special thanks to the following creators for bringing inspiration:
- **夏天y**: [Bilibili Video (BV1vxhj6EEFc)](https://www.bilibili.com/video/BV1vxhj6EEFc/)
- **Andronicus** (YouTube): Video work *"Speedrunning Minecraft, but Every Minute the Player Changes"*

---

### 🤖 Generative AI Disclosure
In compliance with [Modrinth Content Policy (Section 6: Usage of Generative "AI")](https://support.modrinth.com/en/articles/8796599-content-rules):
- **Code & Workflows**: Generative AI was used to assist in writing, refactoring, and optimizing parts of the mod's code, Maven build configurations, and CI/CD publishing workflows.
- **Documentation & Page**: The project description, README documentation, and English/Chinese bilingual localization were formatted and polished with AI assistance.
- **Human Oversight**: Overall game design, core mechanics, logic validation, and release testing are directed, audited, and maintained by human creators.

---

<a name="chinese"></a>
## 🇨🇳 中文说明

**Speedrun Switch（接力速通）** 是一款专为 Minecraft 多人接力速通 Fabric 模组。  
在游戏过程中，**同一时间仅有 1 名玩家拥有操作权**，其余所有队友均为旁观视角并强制附身于活动玩家。每满 10 分钟（时间可自定义），操作权将自动轮换给下一位玩家，最终目标是击败末影龙（当然也可以作为普通接力生存一直玩下去）。

---

### ✨ 核心特性

- 🔄 **接力轮换与断线秒切**：
  - 每隔 10 分钟 自动切换操纵玩家，其余玩家保持旁观模式进行观战。
  - 接力顺序由玩家观战时间、累计操纵时间等因素决定，顺序由权重随机决定。
  - 切换玩家时会从当前在线玩家中随机选取。
  - **掉线保护机制**：活动玩家掉线瞬间，系统立即将操作权转移给下一名在线玩家。
  - 旁观者按 <kbd>E</kbd> 键即可实时查看装备与道具。
  - 基于原版旁观附身机制，高频发送视角锁定包，严禁旁观者私自脱离视角搜寻物资/跑图报点。
  - 对旁观者玩家隐藏坐标、生物群系与区块信息。
  - 玩家发送的聊天信息仅回显给自己，防止通过游戏内聊天违规报点交流；系统公告与任务信息全员正常接收。
- 🎯 **动态任务奖励系统**：
  - 每次轮换后随机延迟 0~300 秒发放 0~1 个专属任务。内置任务：击杀敌对生物、击杀末影人、挖掘石头、造成伤害、拾取食物、进入下界/末地。
  - **支持数据驱动自定义任务**：无需修改代码，在配置文件中即可添加任意生物（`kill_entity`）、物品（`collect_item`）、方块（`mine_block`）任务。
  - 任务奖励（延长操纵时间）与超时惩罚（施加缓慢效果或扣除时间）。
- 🎙️ **Simple Voice Chat 观众频道语音隔离**：
  - 旁观者自动加入“观众频道”畅所欲言，操纵者无法接受到语音信息。
- 📊 **详细战绩报表与断点恢复**：
  - 实时记录每位玩家的可操纵时长、死亡次数与全局总耗时。
  - 击杀末影龙后自动宣告速通胜利，冻结计时器并广播通关战报。
  - **断点恢复（`/speedrun resume`）**：服务器意外重启或崩溃后，可随时从存档恢复未完成的速通。
- 🖥️ **客户端沉浸式 HUD**：
  - 屏幕左上角HUD，显示当前操纵者、倒计时。

---

### ⌨️ 指令列表

| 指令 | 所需权限 | 说明 |
| :--- | :---: | :--- |
| `/speedrun start [玩家]` | OP | 开启接力速通（带开局倒计时与音效，可指定首发玩家） |
| `/speedrun resume` | OP | 从存档恢复上次中断的速通进程 |
| `/speedrun switch [玩家]` | OP | 立即手动交接（可指定移交给某位玩家） |
| `/speedrun time [set] <秒数>` | OP | 直接设置当前操纵者的剩余时间（如 `time 600` 设置为 600 秒，支持 `time add` 增减） |
| `/speedrun stats` | 全员可用 | 查看本局用时、完成状态、各玩家操纵时长与死亡数 |
| `/speedrun unstuck [玩家]` | 全员（自己）/ OP 2（他人） | 视角脱离/卡死恢复（重置附身并重新跟随操纵者） |
| `/speedrun end` | OP | 结束当前速通（保留统计，全员恢复生存并保留背包） |
| `/speedrun reset` | OP | 清空统计数据，重置为未开启状态 |
| `/speedrun help` | 全员可用 | 查看交互式指令帮助菜单 |

---

### ⚙️ 配置文件说明 (`config/speedrun-switch.json`)

| 配置项 | 默认值 | 详细解析 |
| :--- | :---: | :--- |
| `switchIntervalMinutes` | `10` | 自动轮换周期（分钟） |
| `countdownSeconds` | `10` | 轮换前倒计时时长（秒） |
| `startCountdownSeconds` | `3` | 开局起跑倒计时（秒） |
| `autoSwitchEnabled` | `true` | 是否启用自动定时轮换（`false` 时仅可通过指令切换） |
| `minimumPlayersForSwitch` | `2` | 允许轮换的最低在线人数（低于该人数跳过切换并提示） |
| `taskEnabled` | `true` | 是否启用每轮任务系统 |
| `taskIntervalSecondsMin` / `Max` | `0` / `300` | 轮换后任务随机发放延迟区间（秒） |
| `taskTimeLimitSeconds` | `120` | 任务超时时限（秒，轮换时自动截断） |
| `taskRewardSeconds` | `30` | 任务达成奖励的额外操纵时间（秒） |
| `taskPenaltySeconds` | `30` | 超时扣除的操纵时间（`TIME` 模式下生效） |
| `taskPenaltyMode` | `SLOWNESS` | 惩罚模式：`SLOWNESS`（施加缓慢效果）或 `TIME`（扣除时间） |
| `taskTemplates` | 见上方配置 | **任务池配置**：支持自定义任务类型、目标数量、权重与通用目标 ID |
| `voiceChatEnabled` | `true` | 是否启用 Simple Voice Chat 观众频道自动隔离 |
| `spectatorGroupName` | `观众频道` | 语音插件中创建的旁观者群组名称 |
| `blockDebugForSpectators` | `true` | 是否封锁旁观者的 F3 调试信息 |

#### 自定义任务配置模版
任务模板支持通用类型，填写对应的命名空间 ID 即可生效：

```json
"taskTemplates": [
  { "id": "kill_entity", "target": "minecraft:blaze", "count": 3, "weight": 2 },
  { "id": "collect_item", "target": "minecraft:ender_pearl", "count": 4, "weight": 2 },
  { "id": "mine_block", "target": "minecraft:ancient_debris", "count": 1, "weight": 1 }
]
```

---

### 💡 灵感来源与鸣谢 (Credits)
特别鸣谢以下创作者带来的灵感：
- **夏天y**：[Bilibili 视频 (BV1vxhj6EEFc)](https://www.bilibili.com/video/BV1vxhj6EEFc/)
- **Andronicus**（YouTube）：视频作品 *《Speedrunning Minecraft, but Every Minute the Player Changes》*

---

### 🤖 生成式 AI 声明 (Generative AI Disclosure)
根据 [Modrinth 内容政策（第 6 条：生成式 AI 的使用）](https://support.modrinth.com/en/articles/8796599-content-rules) 的透明度规范要求：
- **代码与工作流**：使用生成式 AI 辅助编写、重构与优化了部分模组代码、Maven 构建配置及 CI/CD 发布工作流。
- **文档与页面**：本项目的简介说明、README 文档以及中英双语本地化排版经由 AI 辅助润色与翻译。
- **人工主导与审核**：整体玩法设计、核心运行逻辑、安全校验及发布测试均由创作者人工主导、审计并维护。
