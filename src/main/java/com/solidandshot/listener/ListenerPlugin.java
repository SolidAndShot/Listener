package com.solidandshot.listener;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** Plugin entry point for the configurable server listener rules. */
public final class ListenerPlugin extends JavaPlugin {

    private ListenerManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        manager = new ListenerManager(this);
        getServer().getPluginManager().registerEvents(new BukkitEventListener(manager), this);
        ListenerGui gui = new ListenerGui(this, manager);
        getServer().getPluginManager().registerEvents(gui, this);

        PluginCommand command = getCommand("listener");
        if (command != null) {
            ListenerCommand executor = new ListenerCommand(this, manager);
            executor.setGui(gui);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().severe("plugin.yml 中未找到 listener 命令。");
        }

        manager.reload();
        manager.dispatch(EventContext.empty("server_start"));
        getLogger().info("监听器插件已启用。");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    public ListenerManager manager() {
        return manager;
    }
}
