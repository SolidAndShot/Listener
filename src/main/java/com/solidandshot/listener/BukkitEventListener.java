package com.solidandshot.listener;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bukkit/Folia event adapter for the configurable listener rules. */
public final class BukkitEventListener implements Listener {

    private final ListenerManager manager;

    public BukkitEventListener(ListenerManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        manager.dispatch(context("player_join", player, Map.of("join_message", safe(event.getJoinMessage()))));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        manager.dispatch(context("player_quit", player, Map.of("quit_message", safe(event.getQuitMessage()))));
        manager.clientBridge().remove(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncPlayerChatEvent event) {
        manager.dispatch(context("player_chat", event.getPlayer(), Map.of(
                "message", safe(event.getMessage()),
                "format", safe(event.getFormat())
        )));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        manager.dispatch(context("player_command", event.getPlayer(), Map.of(
                "command", safe(event.getMessage()),
                "command_name", firstWord(event.getMessage())
        )));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        manager.dispatch(context("player_death", player, Map.of(
                "death_message", safe(event.getDeathMessage()),
                "keep_inventory", Boolean.toString(event.getKeepInventory())
        )));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        manager.dispatch(context("player_damage", player, Map.of(
                "damage", Double.toString(event.getFinalDamage()),
                "raw_damage", Double.toString(event.getDamage()),
                "damage_cause", event.getCause().name().toLowerCase()
        )));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()
                && event.getFrom().getWorld() == event.getTo().getWorld()) {
            return;
        }
        manager.dispatch(context("player_move", event.getPlayer(), Map.of(
                "old_x", Double.toString(event.getFrom().getX()),
                "old_y", Double.toString(event.getFrom().getY()),
                "old_z", Double.toString(event.getFrom().getZ()),
                "new_x", Double.toString(event.getTo().getX()),
                "new_y", Double.toString(event.getTo().getY()),
                "new_z", Double.toString(event.getTo().getZ()),
                "old_world", event.getFrom().getWorld().getName(),
                "new_world", event.getTo().getWorld().getName()
        )));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        manager.dispatch(context("block_break", event.getPlayer(), Map.of(
                "block", key(block),
                "block_x", Integer.toString(block.getX()),
                "block_y", Integer.toString(block.getY()),
                "block_z", Integer.toString(block.getZ()),
                "tool", itemKey(event.getPlayer().getInventory().getItemInMainHand())
        )));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        manager.dispatch(context("block_place", event.getPlayer(), Map.of(
                "block", key(block),
                "block_x", Integer.toString(block.getX()),
                "block_y", Integer.toString(block.getY()),
                "block_z", Integer.toString(block.getZ()),
                "item", itemKey(event.getItemInHand())
        )));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        manager.dispatch(context("player_interact", event.getPlayer(), Map.of(
                "action", event.getAction().name().toLowerCase(),
                "item", itemKey(event.getItem()),
                "block", event.getClickedBlock() == null ? "" : key(event.getClickedBlock()),
                "hand", event.getHand() == null ? "" : event.getHand().name().toLowerCase()
        )));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onConsume(PlayerItemConsumeEvent event) {
        manager.dispatch(context("item_consume", event.getPlayer(), Map.of(
                "item", itemKey(event.getItem())
        )));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack stack = event.getItem().getItemStack();
        manager.dispatch(context("item_pickup", player, Map.of(
                "item", itemKey(stack),
                "amount", Integer.toString(stack.getAmount())
        )));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        manager.dispatch(context("entity_spawn", null, Map.of(
                "entity", entityKey(entity),
                "entity_uuid", entity.getUniqueId().toString(),
                "world", entity.getWorld().getName(),
                "x", Double.toString(entity.getLocation().getX()),
                "y", Double.toString(entity.getLocation().getY()),
                "z", Double.toString(entity.getLocation().getZ()),
                "spawn_reason", event instanceof CreatureSpawnEvent creature ? creature.getSpawnReason().name().toLowerCase() : "unknown"
        )));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        String killer = entity instanceof LivingEntity living && living.getKiller() != null
                ? living.getKiller().getName() : "";
        manager.dispatch(context("entity_death", entity instanceof Player player ? player : null, Map.of(
                "entity", entityKey(entity),
                "entity_uuid", entity.getUniqueId().toString(),
                "world", entity.getWorld().getName(),
                "x", Double.toString(entity.getLocation().getX()),
                "y", Double.toString(entity.getLocation().getY()),
                "z", Double.toString(entity.getLocation().getZ()),
                "killer", killer
        )));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        manager.dispatch(context("world_change", event.getPlayer(), Map.of(
                "old_world", event.getFrom().getName(),
                "new_world", event.getPlayer().getWorld().getName()
        )));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWeather(WeatherChangeEvent event) {
        manager.dispatch(context("weather_change", null, Map.of(
                "world", event.getWorld().getName(),
                "weather_type", event.toWeatherState() ? "rain" : "clear"
        )));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onThunder(ThunderChangeEvent event) {
        manager.dispatch(context("weather_change", null, Map.of(
                "world", event.getWorld().getName(),
                "weather_type", event.toThunderState() ? "thunder" : "rain"
        )));
    }

    private static EventContext context(String event, Player player, Map<String, String> values) {
        return new EventContext(event, player, values);
    }

    private static String key(Block block) {
        return block.getType().getKey().toString();
    }

    private static String itemKey(ItemStack stack) {
        return stack == null || stack.getType().isAir() ? "" : stack.getType().getKey().toString();
    }

    private static String entityKey(Entity entity) {
        return entity.getType().getKey().toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String firstWord(String value) {
        String trimmed = safe(value).trim();
        int space = trimmed.indexOf(' ');
        return (space < 0 ? trimmed : trimmed.substring(0, space)).replaceFirst("^/", "");
    }
}
