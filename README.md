# 监听器（Listener）

一个面向 Minecraft 26.2、Luminol/Folia 26.2 build 727 的可配置服务器监听器插件。

它借鉴 FancyMenu 的“监听器 Provider → 实例 → 动作脚本”模型，核心使用 Bukkit/Folia 服务端事件实现；不安装客户端 Mod 也能独立运行。适合把玩家加入、聊天、命令、死亡、伤害、移动、方块、物品、实体、世界和天气等事件转换成自动动作。

## 环境

- Minecraft 26.2
- Luminol/Folia 26.2 build 727
- Java 25
- `folia-supported: true`
- PlaceholderAPI 可选

## 构建

```powershell
gradle clean build
```

产物：

```text
build/libs/Listener-1.0.0.jar
```

将 JAR 放入服务器 `plugins` 目录后完整重启。首次启动会生成 `plugins/Listener/config.yml`。

## 使用教程

完整的中文教程（安装、配置结构、事件过滤器、动作、变量、常见示例和故障排查）见：[docs/使用教程.md](docs/使用教程.md)。

最小使用流程如下：

1. 把 `Listener-1.0.0.jar` 放入服务器的 `plugins` 目录并重启服务器。
2. 编辑 `plugins/Listener/config.yml`，在 `listeners` 下添加或修改规则。
3. 执行 `/listener reload` 重新加载配置。
4. 用 `/listener list`、`/listener info <id>` 检查规则，或用 `/listener fire <id>` 由玩家手动测试动作。

## 命令

```text
/listener list
/listener info <id>
/listener gui
/listener reload
/listener test <id>
/listener fire <id>
```

需要 `listener.admin` 权限，默认只有 OP 拥有。

`/listener gui` 会打开一个简单的两级游戏内编辑器：在规则列表中左键进入详情、右键快速启停；详情页可修改事件、首个动作、定时器间隔、立即测试或新建规则。保存会自动写回 `plugins/Listener/config.yml` 并重载规则。编辑器和菜单点击会再次检查 `listener.admin`，普通玩家无法使用。

## 配置示例

```yaml
listeners:
  welcome:
    event: player_join
    enabled: true
    actions:
      - type: message
        value: "&a欢迎 %player_name%"

  stone_break:
    event: block_break
    enabled: true
    filters:
      block: minecraft:stone
    actions:
      - type: console_command
        value: "say %player_name% 挖到了石头"
```

## 事件

当前实现的事件包括：

```text
server_start
timer
player_join
player_quit
player_chat
player_command
player_death
player_damage
player_move
block_break
block_place
player_interact
item_consume
item_pickup
entity_spawn
entity_death
world_change
weather_change
client_connect
client_*
```

## 动作

```text
message
broadcast
console_command
player_command
actionbar
title
sound
set_variable
log
client_action
client_message
client_screen
client_overlay
client_sound
```

每个动作支持 `delay_ticks`。动作会在 Folia 全局调度器或玩家调度器上执行，避免异步聊天事件直接操作世界对象。

`sound` 动作的格式为 `minecraft:block.note_block.chime,1.0,1.0`，依次是音效资源名、音量和音调。

## 变量

常用变量：

```text
%player_name% %uuid% %world% %x% %y% %z%
%message% %command% %block% %item% %entity% %damage%
%event% %listener_id% %old_world% %new_world%
```

未安装 PlaceholderAPI 时，插件仍会处理上述内置变量；安装后会自动尝试继续解析 PlaceholderAPI 变量。

## 设计边界

FancyMenu 中依赖客户端 Mixin 的键盘、鼠标、屏幕、客户端音乐和客户端渲染监听器无法仅通过 Bukkit 服务端插件直接获得。本项目提供可选的 [ListenerClient Fabric Mod](https://github.com/SolidAndShot/ListenerClient) 桥接（Plugin Messaging）：客户端 Mod 可把这些事件上报为 `client_*` 事件，也可接收 `client_*` 动作。没有安装客户端 Mod 时，原有服务端功能不受影响。

## 客户端 Mod 桥接协议

服务端和客户端通过原生 Plugin Messaging 通道 `listener:main` 通信。每个消息都是 Java `DataOutputStream` 写出的二进制帧：

```text
byte protocol_version (当前为 1)
byte opcode
```

客户端发送：

- `opcode=1 HELLO`：`byte client_version`、`UTF mod_version`、`byte feature_count`、若干 `UTF feature`；
- `opcode=4 EVENT`：`UTF event`、`byte field_count`、若干 `UTF key` + `UTF value`。

事件名不带 `client_` 前缀时，服务端会自动补上；例如客户端发送 `keyboard_key_pressed`，规则中监听 `client_keyboard_key_pressed`。字段会作为普通监听器变量和过滤器使用，并自动包含 `source=client`。

服务端发送：

- `opcode=2 HELLO_ACK`：`boolean accepted`、`UTF reason`、`UTF capabilities`；
- `opcode=3 ACTION`：`UTF action`、`UTF value`。

客户端 Mod 可根据 `action` 实现打开屏幕、显示覆盖层、播放客户端音乐等行为；未知动作应安全忽略。所有字符串最多 4096 字符，单帧最多 32 KiB。协议不要求安装 ProtocolLib。

### 客户端动作示例

```yaml
listeners:
  open_profile:
    event: client_keyboard_key_pressed
    filters:
      key_keycode: "80"
    actions:
      - type: client_screen
        value: "inventory"

  show_overlay:
    event: client_connect
    actions:
      - type: client_overlay
        value: "欢迎，%player_name%！"
```

可用动作包括 `client_action`（值格式为 `动作名|参数`）以及便捷别名 `client_message`、`client_screen`、`client_overlay`、`client_sound`。这些动作只有在玩家安装兼容客户端 Mod 并完成握手后才会发送；首次进入时请用 `client_connect` 触发欢迎动作。

安全提示：客户端事件由玩家客户端自行上报，不能作为管理员签名或反作弊凭据。不要仅凭 `client_*` 事件执行踢人、封禁、权限变更等高权限控制台命令。
