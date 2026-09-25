package com.solidandshot.listener;

import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable event data passed from Bukkit adapters to listener rules. */
public final class EventContext {

    private final String event;
    private final Player player;
    private final Map<String, String> values;

    public EventContext(String event, Player player, Map<String, String> values) {
        this.event = Objects.requireNonNullElse(event, "unknown");
        this.player = player;
        Map<String, String> copy = new LinkedHashMap<>();
        if (values != null) {
            values.forEach((key, value) -> copy.put(key, Objects.requireNonNullElse(value, "")));
        }
        copy.putIfAbsent("event", this.event);
        if (player != null) {
            copy.putIfAbsent("player_name", player.getName());
            copy.putIfAbsent("uuid", player.getUniqueId().toString());
            if (player.getWorld() != null) {
                copy.putIfAbsent("world", player.getWorld().getName());
            }
            copy.putIfAbsent("x", format(player.getLocation().getX()));
            copy.putIfAbsent("y", format(player.getLocation().getY()));
            copy.putIfAbsent("z", format(player.getLocation().getZ()));
        }
        this.values = Map.copyOf(copy);
    }

    public static EventContext empty(String event) {
        return new EventContext(event, null, Map.of());
    }

    public String event() {
        return event;
    }

    public Player player() {
        return player;
    }

    public String get(String key) {
        return values.get(key);
    }

    public Map<String, String> values() {
        return values;
    }

    public EventContext with(String key, String value) {
        Map<String, String> next = new LinkedHashMap<>(values);
        next.put(key, Objects.requireNonNullElse(value, ""));
        return new EventContext(event, player, next);
    }

    private static String format(double value) {
        return Double.toString(value);
    }
}
