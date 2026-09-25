package com.solidandshot.listener;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Administrative commands for inspecting and manually firing listener rules. */
public final class ListenerCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final ListenerManager manager;

    public ListenerCommand(JavaPlugin plugin, ListenerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("listener.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有权限执行此命令。");
            return true;
        }
        String subcommand = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "list" -> list(sender);
            case "info" -> info(sender, args);
            case "reload" -> reload(sender);
            case "test", "fire" -> fire(sender, args);
            default -> usage(sender, label);
        }
        return true;
    }

    private void list(CommandSender sender) {
        if (manager.definitions().isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "当前没有监听器规则。");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "监听器规则 (" + manager.definitions().size() + "):");
        manager.definitions().values().forEach(definition -> sender.sendMessage(
                (definition.enabled() ? ChatColor.GREEN : ChatColor.GRAY) + "- " + definition.id()
                        + ChatColor.DARK_GRAY + " [" + definition.event() + "] actions=" + definition.actions().size()));
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /listener info <id>");
            return;
        }
        ListenerManager.ListenerDefinition definition = manager.definitions().get(args[1]);
        if (definition == null) {
            sender.sendMessage(ChatColor.RED + "找不到监听器: " + args[1]);
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "监听器 " + definition.id());
        sender.sendMessage(ChatColor.GRAY + "事件: " + definition.event());
        sender.sendMessage(ChatColor.GRAY + "启用: " + definition.enabled());
        sender.sendMessage(ChatColor.GRAY + "过滤器: " + definition.filters());
        sender.sendMessage(ChatColor.GRAY + "动作:");
        for (ListenerManager.ActionSpec action : definition.actions()) {
            sender.sendMessage(ChatColor.GRAY + "  - " + action.type() + " (delay=" + action.delayTicks() + ") " + action.value());
        }
        if (definition.event().equals("timer")) {
            sender.sendMessage(ChatColor.GRAY + "间隔: " + definition.intervalTicks() + " ticks");
        }
    }

    private void reload(CommandSender sender) {
        plugin.reloadConfig();
        manager.reload();
        sender.sendMessage(ChatColor.GREEN + "监听器配置已重载，共 " + manager.definitions().size() + " 条规则。");
    }

    private void fire(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /listener fire <id>");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "该命令需要由玩家执行。");
            return;
        }
        if (!manager.definitions().containsKey(args[1])) {
            sender.sendMessage(ChatColor.RED + "找不到监听器: " + args[1]);
            return;
        }
        manager.fire(args[1], player);
        sender.sendMessage(ChatColor.GREEN + "已触发监听器: " + args[1]);
    }

    private void usage(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.YELLOW + "用法: /" + label + " <list|info|reload|test|fire>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> values = List.of("list", "info", "reload", "test", "fire");
            return partial(values, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("info")
                || args[0].equalsIgnoreCase("test") || args[0].equalsIgnoreCase("fire"))) {
            return partial(new ArrayList<>(manager.definitions().keySet()), args[1]);
        }
        return Collections.emptyList();
    }

    private static List<String> partial(List<String> values, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
