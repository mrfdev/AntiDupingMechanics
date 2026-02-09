package com.mrfloris.antiduping;

import org.bukkit.plugin.java.JavaPlugin;

public final class AntiDupingMechanics extends JavaPlugin {

    private AntiDupingListener listener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        listener = new AntiDupingListener(this);
        getServer().getPluginManager().registerEvents(listener, this);

        var cmd = getCommand("antiduping");
        if (cmd != null) {
            var command = new AntiDupingCommand(this, listener);
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }
    }

    public void reloadAll() {
        reloadConfig();
        listener.reloadFromConfig();
    }
}
