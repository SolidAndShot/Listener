# 监听器（Listener）

一个面向 Minecraft 26.2、Luminol/Folia 26.2 build 727 的可配置服务器监听器插件。

它借鉴 FancyMenu 的“监听器 Provider → 实例 → 动作脚本”模型，但使用 Bukkit/Folia 服务端事件实现，不修改客户端，也不要求复刻 FancyMenu 的 Mixin。适合把玩家加入、聊天、命令、死亡、伤害、移动、方块、物品、实体、世界和天气等事件转换成自动动作。

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

## 命令

```text
/listener list
/listener info <id>
/listener reload
/listener test <id>
/listener fire <id>
```

需要 `listener.admin` 权限，默认只有 OP 拥有。

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

FancyMenu 中依赖客户端 Mixin 的键盘、鼠标、屏幕、客户端音乐和客户端渲染监听器无法仅通过 Bukkit 服务端插件直接获得。本项目优先实现服务端能够稳定观察的事件，后续可以通过 ProtocolLib、PlaceholderAPI、ItemsAdder 或客户端配套模组扩展。
