package com.solidandshot.listener;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Loads listener definitions, filters events, and executes configured actions. */
public final class ListenerManager {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%([A-Za-z0-9_.:-]+)%");

    private final JavaPlugin plugin;
    private final ClientBridge clientBridge;
    private final Map<String, String> variables = new ConcurrentHashMap<>();
    private volatile Map<String, ListenerDefinition> definitions = Map.of();
    private final List<ScheduledTask> timerTasks = new ArrayList<>();

    public ListenerManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.clientBridge = new ClientBridge(plugin, this::dispatch);
    }

    public ClientBridge clientBridge() {
        return clientBridge;
    }

    public synchronized void reload() {
        stopTimers();
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection root = config.getConfigurationSection("listeners");
        Map<String, ListenerDefinition> loaded = new LinkedHashMap<>();
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(id);
                if (section == null) {
                    plugin.getLogger().warning("忽略无效监听器配置: " + id);
                    continue;
                }
                String event = normalize(section.getString("event", ""));
                if (event.isEmpty()) {
                    plugin.getLogger().warning("监听器 " + id + " 没有 event，已忽略。 ");
                    continue;
                }
                Map<String, String> filters = new LinkedHashMap<>();
                ConfigurationSection filterSection = section.getConfigurationSection("filters");
                if (filterSection != null) {
                    for (String key : filterSection.getKeys(false)) {
                        filters.put(key, Objects.requireNonNullElse(filterSection.getString(key), ""));
                    }
                }
                List<ActionSpec> actions = new ArrayList<>();
                for (Map<?, ?> raw : section.getMapList("actions")) {
                    String type = normalize(stringValue(raw.get("type")));
                    if (type.isEmpty()) continue;
                    String value = Objects.requireNonNullElse(stringValue(raw.get("value")), "");
                    long delay = Math.max(0L, numberValue(raw.get("delay_ticks"), 0L));
                    actions.add(new ActionSpec(type, value, delay));
                }
                long interval = Math.max(1L, section.getLong("interval_ticks", 20L));
                loaded.put(id, new ListenerDefinition(id, event, section.getBoolean("enabled", true), filters, actions, interval));
            }
        }
        definitions = Collections.unmodifiableMap(loaded);
        startTimers();
        plugin.getLogger().info("已加载 " + loaded.size() + " 个监听器规则。 ");
    }

    public synchronized void startTimers() {
        for (ListenerDefinition definition : definitions.values()) {
            if (!definition.enabled || !definition.event.equals("timer")) continue;
            ScheduledTask task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin,
                    ignored -> dispatchDefinition(definition, EventContext.empty("timer")),
                    1L, definition.intervalTicks);
            timerTasks.add(task);
        }
    }

    public synchronized void stopTimers() {
        for (ScheduledTask task : timerTasks) {
            task.cancel();
        }
        timerTasks.clear();
    }

    public synchronized void shutdown() {
        stopTimers();
        clientBridge.close();
    }

    public void dispatch(EventContext context) {
        if (context == null) return;
        for (ListenerDefinition definition : definitions.values()) {
            dispatchDefinition(definition, context);
        }
    }

    private void dispatchDefinition(ListenerDefinition definition, EventContext context) {
        if (!definition.enabled || !definition.event.equalsIgnoreCase(context.event())) return;
        if (!matches(definition.filters, context)) return;
        EventContext listenerContext = context.with("listener_id", definition.id);
        for (ActionSpec action : definition.actions) {
            scheduleAction(definition, action, listenerContext);
        }
    }

    public void fire(String id, Player player) {
        ListenerDefinition definition = definitions.get(id);
        if (definition == null) return;
        EventContext context = new EventContext(definition.event, player, Map.of("listener_id", id));
        for (ActionSpec action : definition.actions) {
            scheduleAction(definition, action, context);
        }
    }

    public Map<String, ListenerDefinition> definitions() {
        return definitions;
    }

    /**
     * Persists one rule edited by the in-game administrator GUI.
     * Keeping this operation here makes GUI and command/config reloads share the
     * same validation and timer lifecycle.
     */
    public synchronized boolean saveRule(String id, String event, boolean enabled,
                                         Map<String, String> filters, List<ActionSpec> actions,
                                         long intervalTicks) {
        if (id == null || id.isBlank() || event == null || event.isBlank() || actions == null || actions.isEmpty()) {
            return false;
        }
        String safeId = id.trim();
        String safeEvent = normalize(event);
        if (safeId.length() > 64 || safeEvent.length() > 128 || !safeId.matches("[A-Za-z0-9_-]+")) {
            return false;
        }
        List<Map<String, Object>> serializedActions = new ArrayList<>();
        if (actions.size() > 64) return false;
        for (ActionSpec action : actions) {
            if (action == null || action.type() == null || action.type().isBlank()) continue;
            String actionType = normalize(action.type());
            String actionValue = Objects.requireNonNullElse(action.value(), "");
            if (actionType.length() > 64 || actionValue.length() > 4096
                    || action.delayTicks() < 0 || action.delayTicks() > 2_000_000) return false;
            Map<String, Object> serialized = new LinkedHashMap<>();
            serialized.put("type", actionType);
            serialized.put("value", actionValue);
            if (action.delayTicks() > 0) serialized.put("delay_ticks", action.delayTicks());
            serializedActions.add(serialized);
        }
        if (serializedActions.isEmpty()) return false;
        Map<String, String> safeFilters = new LinkedHashMap<>();
        if (filters != null) {
            if (filters.size() > 64) return false;
            for (Map.Entry<String, String> entry : filters.entrySet()) {
                String key = Objects.requireNonNullElse(entry.getKey(), "");
                String value = Objects.requireNonNullElse(entry.getValue(), "");
                if (key.isBlank() || key.length() > 128 || value.length() > 4096) return false;
                safeFilters.put(key, value);
            }
        }
        String path = "listeners." + safeId;
        plugin.getConfig().set(path + ".event", safeEvent);
        plugin.getConfig().set(path + ".enabled", enabled);
        plugin.getConfig().set(path + ".interval_ticks", Math.max(1L, Math.min(2_000_000L, intervalTicks)));
        plugin.getConfig().set(path + ".filters", safeFilters);
        plugin.getConfig().set(path + ".actions", serializedActions);
        plugin.saveConfig();
        reload();
        return definitions.containsKey(safeId);
    }

    /** Toggle a rule and persist it without changing its other fields. */
    public synchronized boolean setEnabled(String id, boolean enabled) {
        ListenerDefinition definition = definitions.get(id);
        if (definition == null) return false;
        plugin.getConfig().set("listeners." + id + ".enabled", enabled);
        plugin.saveConfig();
        reload();
        return true;
    }

    private void scheduleAction(ListenerDefinition definition, ActionSpec action, EventContext context) {
        Runnable runnable = () -> executeAction(definition, action, context);
        Player player = context.player();
        if (player != null) {
            if (action.delayTicks > 0) {
                player.getScheduler().runDelayed(plugin, ignored -> runnable.run(), () -> { }, action.delayTicks);
            } else {
                player.getScheduler().run(plugin, ignored -> runnable.run(), () -> { });
            }
        } else if (action.delayTicks > 0) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, ignored -> runnable.run(), action.delayTicks);
        } else {
            Bukkit.getGlobalRegionScheduler().run(plugin, ignored -> runnable.run());
        }
    }

    private void executeAction(ListenerDefinition definition, ActionSpec action, EventContext context) {
        String value = expand(action.value, context);
        Player player = context.player();
        String colored = ChatColor.translateAlternateColorCodes('&', value);
        try {
            switch (action.type) {
                case "message" -> {
                    if (player != null) player.sendMessage(colored);
                }
                case "broadcast" -> Bukkit.broadcastMessage(colored);
                case "console_command" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripSlash(value));
                case "player_command" -> {
                    if (player != null) player.performCommand(stripSlash(value));
                }
                case "actionbar" -> {
                    if (player != null) player.sendActionBar(colored);
                }
                case "title" -> {
                    if (player != null) {
                        String[] parts = colored.split("\\|", 2);
                        player.sendTitle(parts[0], parts.length > 1 ? parts[1] : "", 10, 40, 10);
                    }
                }
                case "sound" -> playSound(player, value);
                case "set_variable" -> setVariable(value);
                case "log" -> plugin.getLogger().info(ChatColor.stripColor(colored));
                case "client_action" -> {
                    String[] parts = value.split("\\|", 2);
                    String clientAction = parts[0].trim();
                    String clientValue = parts.length > 1 ? parts[1] : "";
                    clientBridge.sendAction(player, clientAction, clientValue);
                }
                case "client_message", "client_screen", "client_overlay", "client_sound" ->
                        clientBridge.sendAction(player, action.type.substring("client_".length()), value);
                default -> plugin.getLogger().warning("监听器 " + definition.id + " 使用了未知动作: " + action.type);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("监听器 " + definition.id + " 的动作执行失败: " + ex.getMessage());
        }
    }

    private String expand(String input, EventContext context) {
        String value = Objects.requireNonNullElse(input, "");
        Map<String, String> values = new LinkedHashMap<>(variables);
        values.putAll(context.values());
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = values.get(matcher.group(1));
            if (replacement == null && context.player() != null) {
                replacement = placeholderApi(context.player(), matcher.group(0));
            }
            if (replacement == null) replacement = matcher.group(0);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        String expanded = result.toString();
        if (context.player() != null) {
            expanded = placeholderApi(context.player(), expanded);
        }
        return expanded;
    }

    private String placeholderApi(Player player, String value) {
        try {
            Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method method = api.getMethod("setPlaceholders", Player.class, String.class);
            Object result = method.invoke(null, player, value);
            return result instanceof String string ? string : value;
        } catch (ReflectiveOperationException ignored) {
            return value;
        }
    }

    private void playSound(Player player, String raw) {
        if (player == null) return;
        String[] parts = raw.split(",");
        try {
            String sound = parts[0].trim();
            if (sound.isEmpty()) return;
            float volume = parts.length > 1 ? Float.parseFloat(parts[1].trim()) : 1.0F;
            float pitch = parts.length > 2 ? Float.parseFloat(parts[2].trim()) : 1.0F;
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("未知音效或参数: " + raw);
        }
    }

    private void setVariable(String raw) {
        int split = raw.indexOf('=');
        if (split <= 0) return;
        variables.put(raw.substring(0, split).trim(), raw.substring(split + 1));
    }

    private boolean matches(Map<String, String> filters, EventContext context) {
        for (Map.Entry<String, String> entry : filters.entrySet()) {
            String expected = entry.getValue();
            if (expected.isBlank() || expected.equals("*")) continue;
            String filterKey = entry.getKey();
            boolean contains = filterKey.endsWith("_contains");
            String actualKey = contains
                    ? filterKey.substring(0, filterKey.length() - "_contains".length())
                    : filterKey;
            String actual = context.get(actualKey);
            if (contains) {
                if (actual == null || !actual.toLowerCase().contains(expected.toLowerCase())) return false;
            } else if (actual == null || !expected.equalsIgnoreCase(actual)) {
                return false;
            }
        }
        return true;
    }

    private static String stripSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }

    private static String normalize(String value) {
        return Objects.requireNonNullElse(value, "").trim().toLowerCase();
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static long numberValue(Object value, long fallback) {
        if (value instanceof Number number) return number.longValue();
        try {
            return value == null ? fallback : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public record ListenerDefinition(String id, String event, boolean enabled,
                                     Map<String, String> filters, List<ActionSpec> actions,
                                     long intervalTicks) {
        public ListenerDefinition {
            filters = Map.copyOf(filters);
            actions = List.copyOf(actions);
        }
    }

    public record ActionSpec(String type, String value, long delayTicks) {
    }
}
