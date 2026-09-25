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
        openModeSelect(player);
    }

    private void openModeSelect(Player player) {
        if (!authorised(player)) return;
        GuiHolder holder = new GuiHolder(GuiPage.MODE, 0, null);
        Inventory inventory = Bukkit.createInventory(holder, 54, MAIN_TITLE + ChatColor.GRAY + " · 选择模式");
        holder.inventory = inventory;
        fillEmpty(inventory, Material.CYAN_STAINED_GLASS_PANE);
        fillBackground(inventory);
        inventory.setItem(4, item(Material.BEACON, "§3Listener Studio",
                "§7选择适合你的编辑方式", "§8新手可从向导开始"));
        inventory.setItem(20, item(Material.NETHER_STAR, "§a新手向导",
                "§7用 5 个简单步骤创建规则", "§7事件和动作通过菜单选择", "§e只在输入 ID/自定义内容时使用聊天"));
        inventory.setItem(24, item(Material.ENCHANTED_BOOK, "§b高级编辑",
                "§7查看和修改已有监听器", "§7支持启停、测试和精细调整"));
        inventory.setItem(49, item(Material.BARRIER, "§c关闭", "§7关闭管理页面"));
        player.openInventory(inventory);
    }

    private void openAdvancedMain(Player player, int requestedPage) {
        if (!authorised(player)) return;
        List<ListenerManager.ListenerDefinition> definitions = new ArrayList<>(manager.definitions().values());
        int pageCount = Math.max(1, (definitions.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        GuiHolder holder = new GuiHolder(GuiPage.MAIN, page, null);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                MAIN_TITLE + ChatColor.GRAY + "  " + (page + 1) + "/" + pageCount);
        holder.inventory = inventory;
        fillEmpty(inventory, Material.BLACK_STAINED_GLASS_PANE);
        fillBackground(inventory);
        for (int index = 0; index < PAGE_SIZE; index++) {
            int absolute = page * PAGE_SIZE + index;
            if (absolute >= definitions.size()) break;
            ListenerManager.ListenerDefinition definition = definitions.get(absolute);
            inventory.setItem(index, ruleItem(definition));
        }
        inventory.setItem(45, item(Material.ARROW, "§e上一页", "§7查看上一页规则"));
        inventory.setItem(46, item(Material.CLOCK, "§b重新加载", "§7从 config.yml 重新读取规则"));
        inventory.setItem(47, item(Material.NETHER_STAR, "§a新手向导", "§7按菜单步骤创建规则"));
        inventory.setItem(48, item(Material.COMPASS, "§b选择模式", "§7返回新手向导/高级编辑选择"));
        inventory.setItem(49, item(Material.WRITABLE_BOOK, "§e快速新建", "§7使用聊天快速输入全部内容"));
        inventory.setItem(53, item(Material.ARROW, "§e下一页", "§7查看下一页规则"));
        inventory.setItem(52, item(Material.BARRIER, "§c关闭", "§7关闭管理页面"));
        player.openInventory(inventory);
    }

    private void openAdvancedMain(Player player) {
        openAdvancedMain(player, 0);
    }

    /** Opens the menu-driven beginner flow after the rule id has been entered. */
    private void openWizardEvents(Player player) {
        if (!authorised(player)) return;
        GuiHolder holder = new GuiHolder(GuiPage.WIZARD_EVENT, 0, null);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                MAIN_TITLE + ChatColor.GRAY + " · 新手向导 2/5");
        holder.inventory = inventory;
        fillEmpty(inventory, Material.BLUE_STAINED_GLASS_PANE);
        fillBackground(inventory);
        inventory.setItem(4, item(Material.COMPASS, "§b步骤 2/5 · 选择触发事件",
                "§7选择玩家加入、聊天、方块等事件", "§7也可以选择客户端 Mod 事件"));
        List<String> events = wizardEvents();
        List<Integer> slots = wizardEventSlots();
        for (int i = 0; i < events.size(); i++) {
            String event = events.get(i);
            inventory.setItem(slots.get(i), item(eventMaterial(event), "§b" + event,
                    "§7" + eventLabel(event), "§e点击选择"));
        }
        inventory.setItem(49, item(Material.ARROW, "§e上一步", "§7重新输入规则 ID"));
        inventory.setItem(52, item(Material.BARRIER, "§c取消", "§7返回模式选择"));
        player.openInventory(inventory);
    }

    private void openWizardActions(Player player) {
        EditSession session = sessions.get(player.getUniqueId());
        if (session == null || session.mode != EditMode.WIZARD) {
            openModeSelect(player);
            return;
        }
        GuiHolder holder = new GuiHolder(GuiPage.WIZARD_ACTION, 0, null);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                MAIN_TITLE + ChatColor.GRAY + " · 新手向导 3/5");
        holder.inventory = inventory;
        fillEmpty(inventory, Material.PURPLE_STAINED_GLASS_PANE);
        fillBackground(inventory);
        inventory.setItem(4, item(Material.COMMAND_BLOCK, "§d步骤 3/5 · 选择动作",
                "§7选择消息、命令、音效或客户端动作"));
        List<Integer> slots = wizardActionSlots();
        for (int i = 0; i < ACTIONS.size(); i++) {
            String action = ACTIONS.get(i);
            inventory.setItem(slots.get(i), item(actionMaterial(action), "§d" + action,
                    "§7" + actionLabel(action), "§e点击选择"));
        }
        inventory.setItem(49, item(Material.ARROW, "§e上一步", "§7返回事件选择"));
        inventory.setItem(52, item(Material.BARRIER, "§c取消", "§7返回模式选择"));
        player.openInventory(inventory);
    }

    private void openWizardContent(Player player) {
        EditSession session = sessions.get(player.getUniqueId());
        if (session == null || session.mode != EditMode.WIZARD) {
            openModeSelect(player);
            return;
        }
        GuiHolder holder = new GuiHolder(GuiPage.WIZARD_CONTENT, 0, null);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                MAIN_TITLE + ChatColor.GRAY + " · 新手向导 4/5");
        holder.inventory = inventory;
        fillEmpty(inventory, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        fillBackground(inventory);
        inventory.setItem(4, item(Material.WRITABLE_BOOK, "§e步骤 4/5 · 设置内容",
                "§7使用推荐模板，或输入自定义内容"));
        String preset = defaultContent(session.actionType);
        inventory.setItem(20, item(Material.PAPER, "§a使用推荐模板",
                "§7" + trimForLore(preset), "§e点击直接使用"));
        inventory.setItem(24, item(Material.WRITABLE_BOOK, "§b自定义内容",
                "§7输入你自己的消息/命令/音效", "§e需要在聊天框输入"));
        inventory.setItem(31, item(Material.BOOK, "§f当前动作: §d" + session.actionType,
                "§7" + actionLabel(session.actionType)));
        inventory.setItem(49, item(Material.ARROW, "§e上一步", "§7返回动作选择"));
        inventory.setItem(52, item(Material.BARRIER, "§c取消", "§7返回模式选择"));
        player.openInventory(inventory);
    }

    private void openWizardPreview(Player player) {
        EditSession session = sessions.get(player.getUniqueId());
        if (session == null || session.mode != EditMode.WIZARD) {
            openModeSelect(player);
            return;
        }
        GuiHolder holder = new GuiHolder(GuiPage.WIZARD_PREVIEW, 0, null);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                MAIN_TITLE + ChatColor.GRAY + " · 新手向导 5/5");
        holder.inventory = inventory;
        fillEmpty(inventory, Material.GRAY_STAINED_GLASS_PANE);
        fillBackground(inventory);
        inventory.setItem(4, item(Material.EMERALD, "§a步骤 5/5 · 预览并保存",
                "§7确认无误后保存并启用规则"));
        inventory.setItem(10, item(Material.NAME_TAG, "§f规则 ID: §b" + session.id));
        inventory.setItem(12, item(Material.COMPASS, "§f事件: §b" + session.event,
                "§7" + eventLabel(session.event)));
        inventory.setItem(14, item(Material.COMMAND_BLOCK, "§f动作: §d" + session.actionType,
                "§7" + actionLabel(session.actionType)));
        inventory.setItem(16, item(Material.PAPER, "§f内容预览",
                "§7" + trimForLore(session.actionValue)));
        inventory.setItem(31, item(Material.EMERALD_BLOCK, "§a保存并启用",
                "§7写入 config.yml 并立即重载"));
        inventory.setItem(45, item(Material.ARROW, "§e返回修改", "§7回到内容选择"));
        inventory.setItem(52, item(Material.BARRIER, "§c取消", "§7不保存并返回模式选择"));
        player.openInventory(inventory);
    }

    private void openDetail(Player player, String id) {
        if (!authorised(player)) return;
        ListenerManager.ListenerDefinition definition = manager.definitions().get(id);
        if (definition == null) {
            player.sendMessage(ChatColor.RED + "找不到监听器: " + id);
            openAdvancedMain(player);
            return;
        }
        GuiHolder holder = new GuiHolder(GuiPage.DETAIL, 0, id);
        Inventory inventory = Bukkit.createInventory(holder, 54, DETAIL_PREFIX + id);
        holder.inventory = inventory;
        fillEmpty(inventory, Material.BLACK_STAINED_GLASS_PANE);
        fillBackground(inventory);
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
        fillEmpty(inventory, Material.BLACK_STAINED_GLASS_PANE);
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
        else if (holder.page == GuiPage.DETAIL) handleDetailClick(player, holder, event.getRawSlot());
        else if (holder.page == GuiPage.MODE) handleModeClick(player, event.getRawSlot());
        else handleWizardClick(player, holder.page, event.getRawSlot());
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

    private void handleModeClick(Player player, int slot) {
        switch (slot) {
            case 20 -> startWizard(player);
            case 24 -> openAdvancedMain(player);
            case 49 -> player.closeInventory();
            default -> { }
        }
    }

    private void handleWizardClick(Player player, GuiPage page, int slot) {
        EditSession session = sessions.get(player.getUniqueId());
        if (session == null || session.mode != EditMode.WIZARD) {
            openModeSelect(player);
            return;
        }
        if (page == GuiPage.WIZARD_EVENT) {
            if (slot == 49) {
                player.closeInventory();
                session.wizardStage = WizardStage.ID;
                prompt(player, "重新输入规则 ID（输入 cancel 取消）");
            } else if (slot == 52) {
                cancelWizard(player);
            } else {
                String selected = wizardEventForSlot(slot);
                if (selected == null) return;
                if (selected.equals("client_custom")) {
                    player.closeInventory();
                    session.wizardStage = WizardStage.EVENT_CUSTOM;
                    prompt(player, "输入 client_ 开头的客户端事件名，例如 client_key_pressed");
                } else {
                    session.event = selected;
                    session.wizardStage = WizardStage.ACTION;
                    openWizardActions(player);
                }
            }
        } else if (page == GuiPage.WIZARD_ACTION) {
            if (slot == 49) {
                session.wizardStage = WizardStage.EVENT;
                openWizardEvents(player);
            } else if (slot == 52) {
                cancelWizard(player);
            } else {
                String selected = wizardActionForSlot(slot);
                if (selected == null) return;
                session.actionType = selected;
                session.wizardStage = WizardStage.CONTENT;
                openWizardContent(player);
            }
        } else if (page == GuiPage.WIZARD_CONTENT) {
            if (slot == 49) {
                session.wizardStage = WizardStage.ACTION;
                openWizardActions(player);
            } else if (slot == 52) {
                cancelWizard(player);
            } else if (slot == 20) {
                session.actionValue = defaultContent(session.actionType);
                session.wizardStage = WizardStage.PREVIEW;
                openWizardPreview(player);
            } else if (slot == 24) {
                player.closeInventory();
                session.wizardStage = WizardStage.CONTENT_CUSTOM;
                prompt(player, "输入动作内容（颜色可用 &a，占位符可用 %player_name%；输入 cancel 取消）");
            }
        } else if (page == GuiPage.WIZARD_PREVIEW) {
            if (slot == 45) {
                session.wizardStage = WizardStage.CONTENT;
                openWizardContent(player);
            } else if (slot == 52) {
                cancelWizard(player);
            } else if (slot == 31) {
                saveWizard(player, session);
            }
        }
    }

    private void saveWizard(Player player, EditSession session) {
        if (session.id == null || session.event == null || session.actionType == null || session.actionValue == null) {
            player.sendMessage(ChatColor.RED + "向导信息不完整，请返回修改。");
            return;
        }
        List<ListenerManager.ActionSpec> actions = List.of(
                new ListenerManager.ActionSpec(session.actionType, session.actionValue, 0));
        if (manager.saveRule(session.id, session.event, true, Map.of(), actions, 20)) {
            sessions.remove(player.getUniqueId(), session);
            player.sendMessage(ChatColor.GREEN + "已创建并启用监听器: " + session.id);
            openAdvancedMain(player);
        } else {
            player.sendMessage(ChatColor.RED + "保存失败，请返回检查内容长度或格式。");
        }
    }

    private void cancelWizard(Player player) {
        sessions.remove(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + "已取消新手向导。");
        openModeSelect(player);
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
                    openAdvancedMain(player, holder.index);
                }
            } else {
                openDetail(player, id);
            }
            return;
        }
        switch (slot) {
            case 45 -> openAdvancedMain(player, holder.index - 1);
            case 46 -> {
                plugin.reloadConfig();
                manager.reload();
                player.sendMessage(ChatColor.GREEN + "监听器配置已重新加载。");
                openAdvancedMain(player, holder.index);
            }
            case 47 -> startWizard(player);
            case 48 -> openModeSelect(player);
            case 49 -> startCreate(player);
            case 52 -> player.closeInventory();
            case 53 -> openAdvancedMain(player, holder.index + 1);
            default -> { }
        }
    }

    private void handleDetailClick(Player player, GuiHolder holder, int slot) {
        String id = holder.id;
        ListenerManager.ListenerDefinition definition = manager.definitions().get(id);
        if (definition == null) {
            openAdvancedMain(player);
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
            case 49 -> openAdvancedMain(player);
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

    private void startWizard(Player player) {
        if (!authorised(player)) return;
        player.closeInventory();
        EditSession session = EditSession.wizard();
        sessions.put(player.getUniqueId(), session);
        prompt(player, "新手向导 1/5：输入规则 ID（仅英文、数字、_、-；输入 cancel 取消）");
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
        if (session.mode == EditMode.WIZARD) {
            handleWizardInput(player, session, input);
        } else if (session.mode == EditMode.CREATE) {
            handleCreateInput(player, session, input);
        } else if (session.mode == EditMode.EDIT_EVENT) {
            finishEventEdit(player, session, input);
        } else if (session.mode == EditMode.EDIT_ACTION) {
            handleActionEdit(player, session, input);
        } else {
            finishIntervalEdit(player, session, input);
        }
    }

    private void handleWizardInput(Player player, EditSession session, String input) {
        if (session.wizardStage == WizardStage.ID) {
            if (!input.matches("[A-Za-z0-9_-]+") || input.length() > 64) {
                prompt(player, "ID 格式不正确，请使用英文、数字、_、-（最长 64 个字符）");
                return;
            }
            if (manager.definitions().containsKey(input)) {
                prompt(player, "这个 ID 已存在，请换一个名称");
                return;
            }
            session.id = input;
            session.wizardStage = WizardStage.EVENT;
            openWizardEvents(player);
        } else if (session.wizardStage == WizardStage.EVENT_CUSTOM) {
            if (!validEvent(input) || !input.toLowerCase(Locale.ROOT).startsWith("client_")) {
                prompt(player, "请输入 client_ 开头的客户端事件名，例如 client_key_pressed");
                return;
            }
            session.event = input.toLowerCase(Locale.ROOT);
            session.wizardStage = WizardStage.ACTION;
            openWizardActions(player);
        } else if (session.wizardStage == WizardStage.CONTENT_CUSTOM) {
            if (input.length() > 4096) {
                prompt(player, "内容最长 4096 个字符，请重新输入");
                return;
            }
            session.actionValue = input;
            session.wizardStage = WizardStage.PREVIEW;
            openWizardPreview(player);
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

    private static List<String> wizardEvents() {
        List<String> values = new ArrayList<>(EVENTS);
        values.add("client_connect");
        values.add("client_custom");
        return values;
    }

    private static List<Integer> wizardEventSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int row : List.of(1, 2, 3)) {
            for (int column = 1; column <= 7; column++) slots.add(row * 9 + column);
        }
        return slots;
    }

    private static List<Integer> wizardActionSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int row : List.of(1, 2)) {
            for (int column = 1; column <= 7; column++) slots.add(row * 9 + column);
        }
        return slots;
    }

    private static String wizardEventForSlot(int slot) {
        List<Integer> slots = wizardEventSlots();
        List<String> events = wizardEvents();
        int index = slots.indexOf(slot);
        return index >= 0 && index < events.size() ? events.get(index) : null;
    }

    private static String wizardActionForSlot(int slot) {
        List<Integer> slots = wizardActionSlots();
        int index = slots.indexOf(slot);
        return index >= 0 && index < ACTIONS.size() ? ACTIONS.get(index) : null;
    }

    private static Material eventMaterial(String event) {
        if (event.startsWith("client_")) return Material.ENDER_EYE;
        if (event.contains("block") || event.contains("item")) return Material.IRON_PICKAXE;
        if (event.contains("world") || event.contains("weather")) return Material.GRASS_BLOCK;
        if (event.contains("entity")) return Material.ZOMBIE_HEAD;
        if (event.equals("timer")) return Material.CLOCK;
        return Material.PLAYER_HEAD;
    }

    private static Material actionMaterial(String action) {
        if (action.startsWith("client_")) return Material.ENDER_PEARL;
        if (action.contains("command")) return Material.COMMAND_BLOCK;
        if (action.equals("sound")) return Material.NOTE_BLOCK;
        if (action.equals("log")) return Material.BOOK;
        return Material.PAPER;
    }

    private static String eventLabel(String event) {
        return switch (event) {
            case "player_join" -> "玩家加入服务器";
            case "player_quit" -> "玩家离开服务器";
            case "player_chat" -> "玩家发送聊天消息";
            case "player_command" -> "玩家执行命令";
            case "player_death" -> "玩家死亡";
            case "player_damage" -> "玩家受到伤害";
            case "player_move" -> "玩家跨方块移动";
            case "block_break" -> "玩家破坏方块";
            case "block_place" -> "玩家放置方块";
            case "player_interact" -> "玩家交互方块或物品";
            case "item_consume" -> "玩家食用物品";
            case "item_pickup" -> "玩家捡起物品";
            case "entity_spawn" -> "实体生成";
            case "entity_death" -> "实体死亡";
            case "world_change" -> "玩家切换世界";
            case "weather_change" -> "天气变化";
            case "server_start" -> "服务器启动";
            case "timer" -> "按时间间隔触发";
            case "client_connect" -> "客户端 Mod 连接";
            case "client_custom" -> "自定义客户端事件";
            default -> "客户端 Mod 事件";
        };
    }

    private static String actionLabel(String action) {
        return switch (action) {
            case "message" -> "给当前玩家发送聊天消息";
            case "broadcast" -> "向全服广播消息";
            case "console_command" -> "以控制台身份执行命令";
            case "player_command" -> "以玩家身份执行命令";
            case "actionbar" -> "发送 Action Bar";
            case "title" -> "发送标题和副标题";
            case "sound" -> "播放音效";
            case "set_variable" -> "设置运行时变量";
            case "log" -> "写入插件日志";
            case "client_action" -> "向客户端 Mod 发送动作";
            case "client_message" -> "向客户端显示消息";
            case "client_screen" -> "控制客户端界面";
            case "client_overlay" -> "显示客户端覆盖层";
            case "client_sound" -> "播放客户端音效";
            default -> "动作";
        };
    }

    private static String defaultContent(String action) {
        return switch (action) {
            case "message" -> "&a欢迎回来，%player_name%！";
            case "broadcast" -> "&e服务器公告：欢迎大家！";
            case "console_command" -> "say %player_name% 触发了监听器";
            case "player_command" -> "spawn";
            case "actionbar" -> "&b监听器已触发";
            case "title" -> "&6欢迎|&f%player_name%";
            case "sound", "client_sound" -> "minecraft:block.note_block.chime,1.0,1.0";
            case "set_variable" -> "server_label=生存服务器";
            case "log" -> "玩家 %player_name% 触发了监听器";
            case "client_action" -> "overlay|欢迎，%player_name%！";
            case "client_message" -> "欢迎，%player_name%！";
            case "client_screen" -> "inventory";
            case "client_overlay" -> "欢迎，%player_name%！";
            default -> "&f监听器已触发";
        };
    }

    private static String trimForLore(String value) {
        String normalized = value == null ? "" : value.replace('\n', ' ');
        return normalized.length() <= 42 ? normalized : normalized.substring(0, 39) + "...";
    }

    /**
     * Gives every screen a quiet visual frame so actionable items read as
     * cards instead of floating in an empty inventory. Empty filler slots are
     * still cancelled by the click handler and can never move items.
     */
    private static void fillBackground(Inventory inventory) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, "§r");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) inventory.setItem(slot, filler.clone());
        }
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

    private static void fillEmpty(Inventory inventory, Material material) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) inventory.setItem(slot, item(material, " "));
        }
    }

    private enum GuiPage { MAIN, DETAIL, MODE, WIZARD_EVENT, WIZARD_ACTION, WIZARD_CONTENT, WIZARD_PREVIEW }

    private enum EditMode { CREATE, EDIT_EVENT, EDIT_ACTION, EDIT_INTERVAL, WIZARD }

    private enum WizardStage { ID, EVENT, EVENT_CUSTOM, ACTION, CONTENT, CONTENT_CUSTOM, PREVIEW }

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
        private WizardStage wizardStage;
        private final Map<String, String> filters;

        private EditSession(EditMode mode) {
            this.mode = mode;
            this.filters = new LinkedHashMap<>();
        }

        private static EditSession create() {
            return new EditSession(EditMode.CREATE);
        }

        private static EditSession wizard() {
            EditSession session = new EditSession(EditMode.WIZARD);
            session.wizardStage = WizardStage.ID;
            return session;
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
