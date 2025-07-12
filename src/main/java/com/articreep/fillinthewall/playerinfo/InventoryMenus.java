package com.articreep.fillinthewall.playerinfo;

import com.articreep.fillinthewall.lobby.LobbyItems;
import com.articreep.fillinthewall.utils.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.javatuples.Pair;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InventoryMenus implements Listener {
    public enum MenuType {
        PROFILE, STATS, SETTINGS
    }
    private final static Map<Inventory, MenuType> inventoryMappings = new HashMap<>();
    private final static MiniMessage miniMessage = MiniMessage.miniMessage();

    public static void profileInventory(UUID uuid, Player player) {

        String playername = Bukkit.getOfflinePlayer(uuid).getName();
        if (playername == null) playername = "null";
        Inventory inventory = Bukkit.createInventory(null, 27, Component.text(playername)
                .append(Component.text("'s profile")));

        ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) playerHead.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
        meta.displayName(Component.text(playername, NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));

        Component levelText;
        try {
            Pair<Integer, Integer> levelPair = PlayerLevels.getLevel(uuid);
            int bracketXP = PlayerLevels.getBracketXP(levelPair.getValue0());
            Component prefix = PlayerLevels.getPrefix(uuid);

            levelText = prefix
                    .append(Component.text(" " + levelPair.getValue1() + "/" + bracketXP, NamedTextColor.AQUA))
                    .decoration(TextDecoration.ITALIC, false);
        } catch (SQLException e) {
            levelText = Component.text("Error getting level", NamedTextColor.RED);
        }

        // todo playtime and perfect walls cleared
        meta.lore(Arrays.asList(levelText));
        playerHead.setItemMeta(meta);
        inventory.setItem(12, playerHead);

        ItemStack settings = Utils.createGuiItem(Material.TEST_INSTANCE_BLOCK, Component.text("Settings")
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Change various options", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
        ItemMeta settingsMeta = settings.getItemMeta();
        settingsMeta.getPersistentDataContainer().set(LobbyItems.itemTypeKey, PersistentDataType.STRING, "SETTINGS_ITEM");
        settings.setItemMeta(settingsMeta);
        inventory.setItem(14, settings);

        inventoryMappings.put(inventory, MenuType.PROFILE);
        player.openInventory(inventory);
    }

    public static void statsInventory(UUID uuid) {
        // todo
    }

    public static void settingsInventory(UUID uuid, Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, Component.text("Settings"));
        ItemStack border = new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
        ItemMeta meta = border.getItemMeta();
        meta.setHideTooltip(true);
        border.setItemMeta(meta);
        addBorder(inventory, border);

        // todo add settings items
        inventory.setItem(45, backItem("Profile"));
        inventoryMappings.put(inventory, MenuType.SETTINGS);
        player.openInventory(inventory);
    }

    @EventHandler
        public void onCloseInventory(InventoryCloseEvent event) {
            Inventory inventory = event.getInventory();
            inventoryMappings.remove(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // guard block
        Inventory inventory = event.getInventory();
        if (!inventoryMappings.containsKey(inventory)) return;
        event.setCancelled(true);
        MenuType type = inventoryMappings.get(inventory);
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;
        String itemString = clickedItem.getItemMeta().getPersistentDataContainer()
                .get(LobbyItems.itemTypeKey, PersistentDataType.STRING);
        if (itemString == null) return;

        switch (type) {
            case PROFILE -> {
                if (itemString.equals("SETTINGS_ITEM")) {
                    event.getWhoClicked().closeInventory();
                    settingsInventory(event.getWhoClicked().getUniqueId(), (Player) event.getWhoClicked());
                }
            }
            case STATS -> {
                // todo
            }
            case SETTINGS -> {
                if (itemString.equals("BACK_ITEM")) {
                    event.getWhoClicked().closeInventory();
                    profileInventory(event.getWhoClicked().getUniqueId(), (Player) event.getWhoClicked());
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory inventory = event.getInventory();
        if (inventoryMappings.containsKey(inventory)) {
            event.setCancelled(true);
        }
    }

    public static void addBorder(Inventory inventory, ItemStack border) {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (i < 9 || i % 9 == 0 || i % 9 == 8 || i > inventory.getSize() - 9) {
                inventory.setItem(i, border.clone());
            }
        }
    }

    public static ItemStack backItem(String where) {
        ItemStack back = Utils.createGuiItem(Material.ARROW, Component.text("Back", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text(where, NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
        ItemMeta backMeta = back.getItemMeta();
        backMeta.getPersistentDataContainer().set(LobbyItems.itemTypeKey, PersistentDataType.STRING, "BACK_ITEM");
        back.setItemMeta(backMeta);
        return back;
    }

    private static ItemStack tipToggleItem(boolean enabled) {
        ItemStack item = Utils.createGuiItem(Material.WRITABLE_BOOK,
                miniMessage.deserialize("<yellow>Show Tips"),
                miniMessage.deserialize("<gray>Toggles various tips in-game."),
                Component.empty(), Utils.statusComponent(enabled),
                miniMessage.deserialize("<yellow>Click to toggle"));
        ItemMeta itemMeta = item.getItemMeta();
        itemMeta.getPersistentDataContainer().set(LobbyItems.itemTypeKey, PersistentDataType.STRING, "SHOW_TIPS");
        item.setItemMeta(itemMeta);
        return item;
    }

    private static ItemStack musicToggleItem(boolean enabled) {
        ItemStack item = Utils.createGuiItem(Material.NOTE_BLOCK,
                miniMessage.deserialize("<yellow>Play Music"),
                miniMessage.deserialize("<gray>Toggles music in the lobby and in-game."),
                Component.empty(), Utils.statusComponent(enabled),
                miniMessage.deserialize("<yellow>Click to toggle"));
        ItemMeta itemMeta = item.getItemMeta();
        itemMeta.getPersistentDataContainer().set(LobbyItems.itemTypeKey, PersistentDataType.STRING, "PLAY_MUSIC");
        item.setItemMeta(itemMeta);
        return item;
    }
}
