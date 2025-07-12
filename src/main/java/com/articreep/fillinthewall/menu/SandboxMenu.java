package com.articreep.fillinthewall.menu;

import com.articreep.fillinthewall.game.PlayingField;
import com.articreep.fillinthewall.gamemode.GamemodeAttribute;
import com.articreep.fillinthewall.gamemode.GamemodeSettings;
import com.articreep.fillinthewall.lobby.LobbyItems;
import com.articreep.fillinthewall.utils.Utils;
import net.kyori.adventure.text.Component;
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
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;

public class SandboxMenu implements Listener {
    private final static MiniMessage minimessage = MiniMessage.miniMessage();

    public enum MenuType {
        BASE, WALL_GENERATION, DISPLAY_SLOTS, GIMMICK
    }

    private record SandboxInfo(MenuType type, PlayingField field) {
    }

    private final static Map<Inventory, SandboxInfo> inventoryMappings = new HashMap<>();

    public static void sandboxInventory(Player player, PlayingField field) {
        Inventory inventory = Bukkit.createInventory(null, 27, Component.text("Sandbox Settings"));
        populateSandboxInventory(inventory, field);
        inventoryMappings.put(inventory, new SandboxInfo(MenuType.BASE, field));
        player.openInventory(inventory);
    }

    private static void populateSandboxInventory(Inventory inventory, PlayingField field) {
        inventory.clear();
        ItemStack border = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = border.getItemMeta();
        meta.setHideTooltip(true);
        border.setItemMeta(meta);

        GamemodeSettings settings = field.getScorer().getSettings();
        inventory.setItem(4, noHoleGarbageItem());
        inventory.setItem(10, wallSettingsItem());
        inventory.setItem(11, wallTimeItem(field.getQueue().getWallActiveTime()));
        inventory.setItem(12, displaySlotsItem());
        inventory.setItem(13, infoItem());
        inventory.setItem(14, highlightIncorrectBlocksItem(settings.getBooleanAttribute(GamemodeAttribute.HIGHLIGHT_INCORRECT_BLOCKS)));
        inventory.setItem(15, infiniteReachItem(settings.getBooleanAttribute(GamemodeAttribute.INFINITE_BLOCK_REACH)));
        inventory.setItem(16, gimmickItem());
        inventory.setItem(22, messyGarbageItem());
        fillEmptySpace(inventory, border);
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
        MenuType type = inventoryMappings.get(inventory).type;
        PlayingField field = inventoryMappings.get(inventory).field;
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;
        String itemString = clickedItem.getItemMeta().getPersistentDataContainer()
                .get(LobbyItems.itemTypeKey, PersistentDataType.STRING);
        if (itemString == null) return;


    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory inventory = event.getInventory();
        if (inventoryMappings.containsKey(inventory)) {
            event.setCancelled(true);
        }
    }

    private static ItemStack wallSettingsItem() {
        ItemStack item = Utils.createGuiItem(Material.COBBLESTONE_WALL, minimessage.deserialize("<!italic><green>Wall Generation Settings"),
                minimessage.deserialize("<!italic><yellow>Click to view!"));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(LobbyItems.itemTypeKey, PersistentDataType.STRING, "WALL_GENERATION");
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack wallTimeItem(int ticks) {
        double seconds = ticks / 20d;
        ItemStack item = Utils.createGuiItem(Material.CLOCK, minimessage.deserialize("<!italic><green>Wall Time"),
                minimessage.deserialize("<!italic><gray>How long it takes for walls),"),
                minimessage.deserialize("<!italic><gray>to reach the playing field"),
                Component.empty(), minimessage.deserialize("<!italic><aqua>Currently set to " + seconds + "s"),
                minimessage.deserialize("<!italic><yellow>Click to change!"));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(LobbyItems.itemTypeKey, PersistentDataType.STRING, "WALL_TIME");
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack displaySlotsItem() {
        ItemStack item = Utils.createGuiItem(Material.ITEM_FRAME, minimessage.deserialize("<!italic><green>Display Slots"),
                minimessage.deserialize("<!italic><yellow>Click to view!"));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(LobbyItems.itemTypeKey, PersistentDataType.STRING, "DISPLAY_SLOTS");
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack noHoleGarbageItem() {
        ItemStack item = Utils.createGuiItem(Material.STONE, minimessage.deserialize("<!italic><green>No-hole Garbage"),
                minimessage.deserialize("<!italic><gray>Adds a no-hole garbage wall to the queue"),
                minimessage.deserialize("<!italic><yellow>Click to add!"));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(LobbyItems.itemTypeKey, PersistentDataType.STRING, "NO_HOLE_GARBAGE");
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack messyGarbageItem() {
        ItemStack item = Utils.createGuiItem(Material.DIRT, minimessage.deserialize("<!italic><green>Messy Garbage"),
                minimessage.deserialize("<!italic><gray>Adds a messy garbage wall to the queue"),
                minimessage.deserialize("<!italic><yellow>Click to add!"));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(LobbyItems.itemTypeKey, PersistentDataType.STRING, "MESSY_GARBAGE");
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack highlightIncorrectBlocksItem(boolean enabled) {
        ItemStack item = Utils.createGuiItem(Material.GLOW_BERRIES, minimessage.deserialize("<!italic><red>Highlight Incorrect Blocks"),
                minimessage.deserialize("<!italic><gray>Note: Highlights disappear after some time"),
                Component.empty(), Utils.statusComponent(enabled),
                minimessage.deserialize("<!italic><yellow>Click to toggle!"));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(LobbyItems.itemTypeKey, PersistentDataType.STRING, "HIGHLIGHT_INCORRECT_BLOCKS");
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack infiniteReachItem(boolean enabled) {
        ItemStack item = Utils.createGuiItem(Material.BEACON, minimessage.deserialize("<!italic><green>Infinite Reach"),
                Component.empty(), Utils.statusComponent(enabled),
                minimessage.deserialize("<!italic><yellow>Click to toggle!"));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(LobbyItems.itemTypeKey, PersistentDataType.STRING, "INFINITE_REACH");
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack gimmickItem() {
        ItemStack item = Utils.createGuiItem(Material.END_CRYSTAL, minimessage.deserialize("<!italic><red>Gimmicks"),
                minimessage.deserialize("<!italic><yellow>Click to view!"));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(LobbyItems.itemTypeKey, PersistentDataType.STRING, "GIMMICK");
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack infoItem() {
        return Utils.createGuiItem(Material.BOOK, minimessage.deserialize("<!italic><gradient:green:dark_green>Welcome to the Sandbox!"),
                minimessage.deserialize("<!italic><gray>Fiddle around with various features of the game here."),
                minimessage.deserialize("<!italic><blue>You can use /fitw custom <name> to import custom walls!"));
    }

    private static void fillEmptySpace(Inventory inventory, ItemStack border) {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, border);
            }
        }
    }
}
