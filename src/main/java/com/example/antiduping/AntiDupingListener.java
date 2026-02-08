package com.example.antiduping;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class AntiDupingListener implements Listener {

    private final AntiDupingMechanics plugin;

    private boolean debugEnabled;
    private boolean debugToConsole;
    private String msgPrefix;

    private WorldSettings defaultSettings;
    private final Map<String, WorldSettings> perWorld = new HashMap<>();

    public AntiDupingListener(AntiDupingMechanics plugin) {
        this.plugin = plugin;
        reloadFromConfig();
    }

    public void reloadFromConfig() {
        var c = plugin.getConfig();

        debugEnabled = c.getBoolean("debug.enabled", false);
        debugToConsole = c.getBoolean("debug.log_to_console", true);
        msgPrefix = color(c.getString("messages.prefix", "&8[&6AntiDuping&8] &r"));

        perWorld.clear();

        ConfigurationSection worlds = c.getConfigurationSection("worlds");
        ConfigurationSection def = worlds != null ? worlds.getConfigurationSection("__default__") : null;
        defaultSettings = WorldSettings.fromConfig(def, new WorldSettings()); // baseline defaults if missing

        if (worlds != null) {
            for (String key : worlds.getKeys(false)) {
                if (key == null) continue;
                if (key.equalsIgnoreCase("__default__")) continue;

                ConfigurationSection worldSec = worlds.getConfigurationSection(key);
                if (worldSec == null) continue;

                // merge: start from defaultSettings, then override
                WorldSettings ws = defaultSettings.copy();
                ws.applyOverrides(worldSec);
                perWorld.put(key.toLowerCase(), ws);
            }
        }
    }

    public String statusDebug() {
        return "enabled=" + debugEnabled + ", log_to_console=" + debugToConsole;
    }

    public String statusWorldsLoaded() {
        return String.valueOf(perWorld.size() + 1) + " (including __default__)";
    }

    private WorldSettings settingsForWorld(String worldName) {
        if (worldName == null) return defaultSettings;
        return perWorld.getOrDefault(worldName.toLowerCase(), defaultSettings);
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    private void msg(Player p, String message) {
        if (message != null && !message.isBlank()) {
            p.sendMessage(msgPrefix + message);
        }
    }

    private void debug(String line) {
        if (!debugEnabled) return;
        if (debugToConsole) {
            Bukkit.getLogger().info("[AntiDupingMechanics] " + line);
        }
    }

    private boolean bypass(Player p) {
        return p != null && p.hasPermission("antiduping.bypass");
    }

    private boolean isBundle(ItemStack stack) {
        if (stack == null || stack.getType() != Material.BUNDLE) return false;
        ItemMeta meta = stack.getItemMeta();
        return meta instanceof BundleMeta;
    }

    // 1) Block opening ChestBoat inventory (but don't block riding).
    // 2) Block opening ChestedHorse inventory (donkeys/mules/llamas etc.).
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (bypass(player)) return;

        WorldSettings ws = settingsForWorld(player.getWorld().getName());

        Inventory inv = event.getInventory();
        InventoryHolder holder = inv.getHolder();

        if (ws.chestBoats.enabled && ws.chestBoats.blockOpenInventory) {
            if (holder instanceof ChestBoat) {
                event.setCancelled(true);
                msg(player, ws.chestBoats.message);
                debug("Blocked chest-boat inventory open: player=" + player.getName() + ", world=" + player.getWorld().getName());
                return;
            }
        }

        if (ws.donkeys.enabled && ws.donkeys.blockOpenInventory) {
            if (holder instanceof ChestedHorse) {
                event.setCancelled(true);
                msg(player, ws.donkeys.message);
                debug("Blocked chested-animal inventory open: player=" + player.getName() + ", world=" + player.getWorld().getName());
            }
        }
    }

    // Block attaching chests onto donkeys/mules/llamas/etc, and block bundle right-click item insert where applicable.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (bypass(player)) return;

        WorldSettings ws = settingsForWorld(player.getWorld().getName());
        Entity clicked = event.getRightClicked();

        if (ws.donkeys.enabled && ws.donkeys.blockAttachChest) {
            if (clicked instanceof ChestedHorse chestedHorse) {
                ItemStack inHand = player.getInventory().getItem(event.getHand());
                if (inHand != null && inHand.getType() == Material.CHEST) {
                    if (!chestedHorse.isCarryingChest()) {
                        event.setCancelled(true);
                        msg(player, ws.donkeys.message);
                        debug("Blocked chest attach to chested-animal: player=" + player.getName() + ", world=" + player.getWorld().getName()
                                + ", entity=" + clicked.getType());
                        return;
                    }
                }
            }
        }

        if (ws.bundles.enabled && ws.bundles.blockInsertItems) {
            if (clicked instanceof Item) {
                ItemStack inHand = player.getInventory().getItem(event.getHand());
                if (isBundle(inHand)) {
                    event.setCancelled(true);
                    msg(player, ws.bundles.message);
                    debug("Blocked bundle insert via entity-interact: player=" + player.getName() + ", world=" + player.getWorld().getName());
                }
            }
        }
    }

    // Block adding items into bundles via inventory clicks (allow emptying).
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (bypass(player)) return;

        WorldSettings ws = settingsForWorld(player.getWorld().getName());
        if (!ws.bundles.enabled || !ws.bundles.blockInsertItems) return;

        ItemStack current = event.getCurrentItem(); // clicked slot item
        ItemStack cursor = event.getCursor();        // cursor item

        // Putting a cursor item onto a bundle (insert)
        if (isBundle(current) && cursor != null && cursor.getType() != Material.AIR) {
            event.setCancelled(true);
            msg(player, ws.bundles.message);
            debug("Blocked bundle insert via click (cursor -> bundle): player=" + player.getName() + ", world=" + player.getWorld().getName()
                    + ", cursor=" + cursor.getType());
            return;
        }

        // Risky swap case: bundle on cursor + clicking a non-air item (could insert depending on client behavior)
        if (isBundle(cursor) && current != null && current.getType() != Material.AIR) {
            event.setCancelled(true);
            msg(player, ws.bundles.message);
            debug("Blocked bundle insert via click (item -> cursor-bundle): player=" + player.getName() + ", world=" + player.getWorld().getName()
                    + ", current=" + current.getType());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (bypass(player)) return;

        WorldSettings ws = settingsForWorld(player.getWorld().getName());
        if (!ws.bundles.enabled || !ws.bundles.blockInsertItems) return;

        ItemStack cursor = event.getOldCursor();
        if (cursor == null || cursor.getType() == Material.AIR) return;

        for (int rawSlot : event.getRawSlots()) {
            ItemStack inSlot = event.getView().getItem(rawSlot);
            if (isBundle(inSlot)) {
                event.setCancelled(true);
                msg(player, ws.bundles.message);
                debug("Blocked bundle insert via drag: player=" + player.getName() + ", world=" + player.getWorld().getName()
                        + ", cursor=" + cursor.getType());
                return;
            }
        }
    }

    // --- Settings types ---
    static final class WorldSettings {
        ChestBoatSettings chestBoats = new ChestBoatSettings();
        DonkeySettings donkeys = new DonkeySettings();
        BundleSettings bundles = new BundleSettings();

        static WorldSettings fromConfig(ConfigurationSection sec, WorldSettings fallback) {
            WorldSettings ws = (fallback == null ? new WorldSettings() : fallback.copy());
            if (sec == null) return ws;
            ws.applyOverrides(sec);
            return ws;
        }

        void applyOverrides(ConfigurationSection sec) {
            // chest_boats
            ConfigurationSection cb = sec.getConfigurationSection("chest_boats");
            if (cb != null) {
                if (cb.contains("enabled")) chestBoats.enabled = cb.getBoolean("enabled");
                if (cb.contains("block_open_inventory")) chestBoats.blockOpenInventory = cb.getBoolean("block_open_inventory");
                if (cb.contains("message")) chestBoats.message = colorLocal(cb.getString("message"));
            }

            // donkeys
            ConfigurationSection dk = sec.getConfigurationSection("donkeys");
            if (dk != null) {
                if (dk.contains("enabled")) donkeys.enabled = dk.getBoolean("enabled");
                if (dk.contains("block_open_inventory")) donkeys.blockOpenInventory = dk.getBoolean("block_open_inventory");
                if (dk.contains("block_attach_chest")) donkeys.blockAttachChest = dk.getBoolean("block_attach_chest");
                if (dk.contains("message")) donkeys.message = colorLocal(dk.getString("message"));
            }

            // bundles
            ConfigurationSection bd = sec.getConfigurationSection("bundles");
            if (bd != null) {
                if (bd.contains("enabled")) bundles.enabled = bd.getBoolean("enabled");
                if (bd.contains("block_insert_items")) bundles.blockInsertItems = bd.getBoolean("block_insert_items");
                if (bd.contains("message")) bundles.message = colorLocal(bd.getString("message"));
            }
        }

        WorldSettings copy() {
            WorldSettings ws = new WorldSettings();
            ws.chestBoats = chestBoats.copy();
            ws.donkeys = donkeys.copy();
            ws.bundles = bundles.copy();
            return ws;
        }

        private static String colorLocal(String s) {
            return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
        }
    }

    static final class ChestBoatSettings {
        boolean enabled = true;
        boolean blockOpenInventory = true;
        String message = colorStatic("&cYou can't use chest storage in chest boats on this server.");

        ChestBoatSettings copy() {
            ChestBoatSettings s = new ChestBoatSettings();
            s.enabled = enabled;
            s.blockOpenInventory = blockOpenInventory;
            s.message = message;
            return s;
        }
    }

    static final class DonkeySettings {
        boolean enabled = true;
        boolean blockOpenInventory = true;
        boolean blockAttachChest = true;
        String message = colorStatic("&cYou can't use chest storage on pack animals on this server.");

        DonkeySettings copy() {
            DonkeySettings s = new DonkeySettings();
            s.enabled = enabled;
            s.blockOpenInventory = blockOpenInventory;
            s.blockAttachChest = blockAttachChest;
            s.message = message;
            return s;
        }
    }

    static final class BundleSettings {
        boolean enabled = true;
        boolean blockInsertItems = true;
        String message = colorStatic("&cYou can't put items into bundles on this server.");

        BundleSettings copy() {
            BundleSettings s = new BundleSettings();
            s.enabled = enabled;
            s.blockInsertItems = blockInsertItems;
            s.message = message;
            return s;
        }
    }

    private static String colorStatic(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
