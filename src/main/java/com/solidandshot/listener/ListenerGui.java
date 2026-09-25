package com.solidandshot.listener;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * Small, inventory-based editor for Listener rules. It intentionally edits the
 * same YAML model used by the command/config workflow, so it is safe to use as
 * a quick in-game tool rather than a second configuration system.
 */
public final class ListenerGui implements Listener {

    private static final String MAIN_TITLE = ChatColor.DARK_AQUA + "监听器管理";
    private static final String DETAIL_PREFIX = ChatColor.DARK_AQUA + "编辑监听器: ";
    private static final int PAGE_SIZE = 45;
    private static final List<String> EVENTS = List.of(
            "server_start", "timer", "player_join", "player_quit", "player_chat",
            "player_command", "player_death", "player_damage", "player_move",
            "block_break", "block_place", "player_interact", "item_consume",
            "item_pickup", "entity_spawn", "entity_death", "world_change", "weather_change");
    private static final List<String> ACTIONS = List.of(
            "message", "broadcast", "console_command", "player_command", "actionbar",
            "title", "sound", "set_variable", "log", "client_action", "client_message",
            "client_screen", "client_overlay", "client_sound");

    private final JavaPlugin plugin;
    private final ListenerManager manager;
    private final Map<UUID, EditSession> sessions = new ConcurrentHashMap<>();

    public ListenerGui(JavaPlugin plugin, ListenerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void openMain(Player player) {
        if (!authorised(player)) return;
        openMain(player, 0);
    }

    private void openMain(Player player, int requestedPage) {
        if (!authorised(player)) return;
        List<ListenerManager.ListenerDefinition> definitions = new ArrayList<>(manager.definitions().values());
        int pageCount = Math.max(1, (definitions.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        GuiHolder holder = new GuiHolder(GuiPage.MAIN, page, null);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                MAIN_TITLE + ChatColor.GRAY + "  " + (page + 1) + "/" + pageCount);
        holder.inventory = inventory;
        for (int index = 0; index < PAGE_SIZE; index++) {
            int absolute = page * PAGE_SIZE + index;
            if (absolute >= definitions.size()) break;
            ListenerManager.ListenerDefinition definition = definitions.get(absolute);
            inventory.setItem(index, ruleItem(definition));
        }
        inventory.setItem(45, item(Material.ARROW, "§e上一页", "§7查看上一页规则"));
        inventory.setItem(46, item(Material.CLOCK, "§b重新加载", "§7从 config.yml 重新读取规则"));
        inventory.setItem(49, item(Material.WRITABLE_BOOK, "§a新建监听器", "§7按提示输入规则名称、事件和动作"));
        inventory.setItem(53, item(Material.ARROW, "§e下一页", "§7查看下一页规则"));
        inventory.setItem(52, item(Material.BARRIER, "§c关闭", "§7关闭管理页面"));
        player.openInventory(inventory);
    }

    private void openDetail(Player player, String id) {
        if (!authorised(player)) return;
        ListenerManager.ListenerDefinition definition = manager.definitions().get(id);
        if (definition == null) {
            player.sendMessage(ChatColor.RED + "找不到监听器: " + id);
            openMain(player);
            return;
        }
        GuiHolder holder = new GuiHolder(GuiPage.DETAIL, 0, id);
        Inventory inventory = Bukkit.createInventory(holder, 54, DETAIL_PREFIX + id);
        holder.inventory = inventory;
        inventory.setItem(10, item(Material.NAME_TAG, "§b事件: §f" + definition.event(),
                "§7点击后在聊天框输入新的事件名", "§8例如 player_join、timer、client_connect"));
        inventory.setItem(12, item(definition.enabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                definition.enabled() ? "§a已启用" : "§7已停用", "§e点击切换启用状态"));
        String firstAction = definition.actions().isEmpty() ? "(无动作)" : definition.actions().get(0).type();
        inventory.setItem(14, item(Material.COMMAND_BLOCK, "§d首个动作: §f" + firstAction,
                "§7点击编辑首个动作类型和值", "§8其余动作会保留"));
        inventory.setItem(16, item(Material.LEVER, "§6立即测试", "§7以当前玩家身份触发此规则"));
        inventory.setItem(28, item(Material.BOOK, "§f查看详情", "§7事件: " + definition.event(),
                "§7动作数量: " + definition.actions().size(), "§7间隔: " + definition.intervalTicks() + " ticks"));
        inventory.setItem(31, item(Material.CLOCK, "§b定时器间隔", "§7仅 timer 事件使用",
                "§7当前: " + definition.intervalTicks() + " ticks", "§e点击输入新的 tick 数"));
        inventory.setItem(49, item(Material.ARROW, "§e返回规则列表", "§7返回上一页"));
        inventory.setItem(52, item(Material.BARRIER, "§c关闭", "§7关闭管理页面"));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder holder)) return;
        event.setCancelled(true);
        if (!authorised(player)) {
            player.closeInventory();
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        if (holder.page == GuiPage.MAIN) handleMainClick(player, holder, event.getRawSlot(), event.isRightClick());
        else handleDetailClick(player, holder, event.getRawSlot());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder)) return;
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player && !player.hasPermission("listener.admin")) {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Do not retain stale edit sessions after a player leaves the server.
        sessions.remove(event.getPlayer().getUniqueId());
    }

    private void handleMainClick(Player player, GuiHolder holder, int slot, boolean rightClick) {
        if (slot < PAGE_SIZE) {
            int absolute = holder.index * PAGE_SIZE + slot;
            List<String> ids = new ArrayList<>(manager.definitions().keySet());
            if (absolute >= ids.size()) return;
            String id = ids.get(absolute);
            if (rightClick) {
                ListenerManager.ListenerDefinition definition = manager.definitions().get(id);
                if (definition != null && manager.setEnabled(id, !definition.enabled())) {
                    player.sendMessage(ChatColor.GREEN + id + (definition.enabled() ? " 已停用" : " 已启用"));
                    openMain(player, holder.index);
                }
            } else {
                openDetail(player, id);
            }
            return;
        }
        switch (slot) {
            case 45 -> openMain(player, holder.index - 1);
            case 46 -> {
                plugin.reloadConfig();
                manager.reload();
                player.sendMessage(ChatColor.GREEN + "监听器配置已重新加载。");
                openMain(player, holder.index);
            }
            case 49 -> startCreate(player);
            case 52 -> player.closeInventory();
            case 53 -> openMain(player, holder.index + 1);
            default -> { }
        }
    }

    private void handleDetailClick(Player player, GuiHolder holder, int slot) {
        String id = holder.id;
        ListenerManager.ListenerDefinition definition = manager.definitions().get(id);
        if (definition == null) {
            openMain(player);
            return;
        }
        switch (slot) {
            case 10 -> startEditEvent(player, definition);
            case 12 -> {
                manager.setEnabled(id, !definition.enabled());
                openDetail(player, id);
            }
            case 14 -> startEditAction(player, definition);
            case 16 -> {
                manager.fire(id, player);
                player.sendMessage(ChatColor.GREEN + "已测试监听器: " + id);
            }
            case 31 -> startEditInterval(player, definition);
            case 49 -> openMain(player);
            case 52 -> player.closeInventory();
            default -> { }
        }
    }

    private boolean authorised(Player player) {
        if (player.hasPermission("listener.admin")) return true;
        player.sendMessage(ChatColor.RED + "你没有权限打开监听器编辑器。");
        return false;
    }

    private void startCreate(Player player) {
        player.closeInventory();
        sessions.put(player.getUniqueId(), EditSession.create());
        prompt(player, "第一步/共四步：输入新监听器 ID（仅英文、数字、_、-；输入 cancel 取消）");
    }

    private void startEditEvent(Player player, ListenerManager.ListenerDefinition definition) {
        player.closeInventory();
        sessions.put(player.getUniqueId(), EditSession.editEvent(definition));
        prompt(player, "输入事件名（例如 player_join、timer、client_connect；输入 cancel 取消）");
    }

    private void startEditAction(Player player, ListenerManager.ListenerDefinition definition) {
        player.closeInventory();
        sessions.put(player.getUniqueId(), EditSession.editAction(definition));
        prompt(player, "输入动作类型（例如 message、broadcast、console_command；输入 cancel 取消）");
    }

    private void startEditInterval(Player player, ListenerManager.ListenerDefinition definition) {
        player.closeInventory();
        sessions.put(player.getUniqueId(), EditSession.editInterval(definition));
        prompt(player, "输入 timer 间隔 tick（20 tick 约 1 秒；输入 cancel 取消）");
    }

    private void prompt(Player player, String text) {
        player.sendMessage(ChatColor.GOLD + "[监听器编辑器] " + ChatColor.WHITE + text);
        player.sendMessage(ChatColor.GRAY + "当前聊天输入会被编辑器读取，完成后会自动保存并重新打开页面。");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        EditSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        event.setCancelled(true);
        // Permission can be revoked while the chat editor is open. Never let a
        // stale session continue to persist configuration after that happens.
        if (!player.hasPermission("listener.admin")) {
            sessions.remove(player.getUniqueId(), session);
            player.getScheduler().run(plugin, ignored ->
                    player.sendMessage(ChatColor.RED + "你的监听器编辑权限已被撤销，编辑已取消。"), () -> { });
            return;
        }
        String input = event.getMessage().trim();
        // Chat is asynchronous; continue on the player's Folia scheduler so
        // inventory and player messaging APIs are invoked on the right context.
        player.getScheduler().run(plugin, ignored -> handleInput(player, session, input), () -> { });
    }

    private void handleInput(Player player, EditSession session, String input) {
        if (!player.hasPermission("listener.admin")) {
            sessions.remove(player.getUniqueId(), session);
            player.sendMessage(ChatColor.RED + "你的监听器编辑权限已被撤销，编辑已取消。");
            return;
        }
        if (sessions.get(player.getUniqueId()) != session) return;
        if (input.equalsIgnoreCase("cancel")) {
            sessions.remove(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "已取消编辑。");
            openMain(player);
            return;
        }
        if (session.mode == EditMode.CREATE) {
            handleCreateInput(player, session, input);
        } else if (session.mode == EditMode.EDIT_EVENT) {
            finishEventEdit(player, session, input);
        } else if (session.mode == EditMode.EDIT_ACTION) {
            handleActionEdit(player, session, input);
        } else {
            finishIntervalEdit(player, session, input);
        }
    }

    private void handleCreateInput(Player player, EditSession session, String input) {
        if (session.step == 0) {
            if (!input.matches("[A-Za-z0-9_-]+")) {
                prompt(player, "ID 格式不正确，请重新输入（仅英文、数字、_、-）");
                return;
            }
            if (manager.definitions().containsKey(input)) {
                prompt(player, "这个 ID 已存在，请换一个名称");
                return;
            }
            session.id = input;
            session.step = 1;
            prompt(player, "第二步/共四步：输入事件名，例如 player_join、timer 或 client_connect");
        } else if (session.step == 1) {
            if (!validEvent(input)) {
                prompt(player, "事件名不在支持列表中；也可以使用 client_ 开头的客户端事件，请重试");
                return;
            }
            session.event = input.toLowerCase(Locale.ROOT);
            session.step = 2;
            prompt(player, "第三步/共四步：输入动作类型，例如 message、broadcast、console_command");
        } else if (session.step == 2) {
            if (!ACTIONS.contains(input.toLowerCase(Locale.ROOT))) {
                prompt(player, "动作类型不正确，请从提示中的动作类型选择");
                return;
            }
            session.actionType = input.toLowerCase(Locale.ROOT);
            session.step = 3;
            prompt(player, "第四步/共四步：输入动作内容（颜色可用 &a，变量可用 %player_name%）");
        } else {
            session.actionValue = input;
            List<ListenerManager.ActionSpec> actions = List.of(new ListenerManager.ActionSpec(
                    session.actionType, session.actionValue, 0));
            if (manager.saveRule(session.id, session.event, true, Map.of(), actions, 20)) {
                sessions.remove(player.getUniqueId());
                player.sendMessage(ChatColor.GREEN + "已创建监听器: " + session.id);
                openDetail(player, session.id);
            } else {
                player.sendMessage(ChatColor.RED + "保存失败，请检查输入或服务器日志。");
                sessions.remove(player.getUniqueId());
                openMain(player);
            }
        }
    }

    private void finishEventEdit(Player player, EditSession session, String input) {
        if (!validEvent(input)) {
            prompt(player, "事件名不在支持列表中；也可以使用 client_ 开头的客户端事件，请重试");
            return;
        }
        if (saveExisting(player, session, input.toLowerCase(Locale.ROOT), session.actionType,
                session.actionValue, session.intervalTicks)) {
            player.sendMessage(ChatColor.GREEN + "事件已保存。");
        }
    }

    private void handleActionEdit(Player player, EditSession session, String input) {
        if (session.step == 0) {
            if (!ACTIONS.contains(input.toLowerCase(Locale.ROOT))) {
                prompt(player, "动作类型不正确，请从提示中的动作类型选择");
                return;
            }
            session.actionType = input.toLowerCase(Locale.ROOT);
            session.step = 1;
            prompt(player, "输入动作内容（颜色可用 &a，变量可用 %player_name%）");
            return;
        }
        session.actionValue = input;
        if (saveExisting(player, session, session.event, session.actionType, session.actionValue,
                session.intervalTicks)) {
            player.sendMessage(ChatColor.GREEN + "首个动作已保存。");
        }
    }

    private void finishIntervalEdit(Player player, EditSession session, String input) {
        long interval;
        try {
            interval = Long.parseLong(input);
        } catch (NumberFormatException ex) {
            prompt(player, "请输入正整数 tick，例如 20");
            return;
        }
        if (interval < 1 || interval > 2_000_000) {
            prompt(player, "请输入 1 到 2000000 之间的 tick 数");
            return;
        }
        if (saveExisting(player, session, session.event, session.actionType, session.actionValue, interval)) {
            player.sendMessage(ChatColor.GREEN + "定时器间隔已保存。");
        }
    }

    private boolean saveExisting(Player player, EditSession session, String event, String actionType,
                                 String actionValue, long interval) {
        ListenerManager.ListenerDefinition current = manager.definitions().get(session.id);
        if (current == null) {
            sessions.remove(player.getUniqueId());
            player.sendMessage(ChatColor.RED + "规则已经不存在，无法保存。");
            openMain(player);
            return false;
        }
        List<ListenerManager.ActionSpec> actions = new ArrayList<>(current.actions());
        if (actions.isEmpty()) actions.add(new ListenerManager.ActionSpec(actionType, actionValue, 0));
        else actions.set(0, new ListenerManager.ActionSpec(actionType, actionValue, actions.get(0).delayTicks()));
        boolean saved = manager.saveRule(session.id, event, current.enabled(), current.filters(), actions, interval);
        sessions.remove(player.getUniqueId());
        if (saved) openDetail(player, session.id);
        else {
            player.sendMessage(ChatColor.RED + "保存失败，请检查服务器日志。");
            openMain(player);
        }
        return saved;
    }

    private static boolean validEvent(String value) {
        String event = value.toLowerCase(Locale.ROOT);
        return EVENTS.contains(event) || (event.startsWith("client_") && event.matches("client_[a-z0-9_.:-]+"));
    }

    private static ItemStack ruleItem(ListenerManager.ListenerDefinition definition) {
        Material material = definition.enabled() ? Material.LIME_DYE : Material.GRAY_DYE;
        return item(material, (definition.enabled() ? "§a" : "§7") + definition.id(),
                "§f事件: §b" + definition.event(),
                "§f动作: §d" + definition.actions().size() + " 个",
                "§e左键查看详情  §7右键切换启用");
    }

    private static ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private enum GuiPage { MAIN, DETAIL }

    private enum EditMode { CREATE, EDIT_EVENT, EDIT_ACTION, EDIT_INTERVAL }

    private static final class GuiHolder implements InventoryHolder {
        private final GuiPage page;
        private final int index;
        private final String id;
        private Inventory inventory;

        private GuiHolder(GuiPage page, int index, String id) {
            this.page = page;
            this.index = index;
            this.id = id;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class EditSession {
        private final EditMode mode;
        private int step;
        private String id;
        private String event;
        private boolean enabled;
        private String actionType;
        private String actionValue;
        private long intervalTicks;
        private final Map<String, String> filters;

        private EditSession(EditMode mode) {
            this.mode = mode;
            this.filters = new LinkedHashMap<>();
        }

        private static EditSession create() {
            return new EditSession(EditMode.CREATE);
        }

        private static EditSession editEvent(ListenerManager.ListenerDefinition definition) {
            EditSession session = from(EditMode.EDIT_EVENT, definition);
            session.step = 0;
            return session;
        }

        private static EditSession editAction(ListenerManager.ListenerDefinition definition) {
            EditSession session = from(EditMode.EDIT_ACTION, definition);
            session.step = 0;
            return session;
        }

        private static EditSession editInterval(ListenerManager.ListenerDefinition definition) {
            EditSession session = from(EditMode.EDIT_INTERVAL, definition);
            session.step = 0;
            return session;
        }

        private static EditSession from(EditMode mode, ListenerManager.ListenerDefinition definition) {
            EditSession session = new EditSession(mode);
            session.id = definition.id();
            session.event = definition.event();
            session.enabled = definition.enabled();
            session.filters.putAll(definition.filters());
            session.intervalTicks = definition.intervalTicks();
            if (!definition.actions().isEmpty()) {
                session.actionType = definition.actions().get(0).type();
                session.actionValue = definition.actions().get(0).value();
            } else {
                session.actionType = "message";
                session.actionValue = "";
            }
            return session;
        }
    }
}
