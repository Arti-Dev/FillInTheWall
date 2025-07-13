package com.articreep.fillinthewall.menu;

import com.articreep.fillinthewall.FillInTheWall;
import com.articreep.fillinthewall.game.DisplayType;
import com.articreep.fillinthewall.game.PlayingField;
import com.articreep.fillinthewall.game.PlayingFieldManager;
import com.articreep.fillinthewall.game.Wall;
import com.articreep.fillinthewall.lobby.LobbyItems;
import com.articreep.fillinthewall.modifiers.ModifierEvent;
import com.articreep.fillinthewall.playerinfo.InventoryMenus;
import com.articreep.fillinthewall.utils.Utils;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
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
        BASE, WALL_GENERATION, DISPLAY_SLOTS, DISPLAY_SLOTS_PICK, GIMMICK
    }

    private record SandboxInfo(MenuType type, PlayingField field) {
    }

    private final static Map<Inventory, SandboxInfo> inventoryMappings = new HashMap<>();
    private final static Map<Inventory, Integer> displaySlotMappings = new HashMap<>();
    private final static Map<Player, PlayingField> pendingWallTimeInputs = new HashMap<>();
    private final static Map<Player, ModifierEvent> pendingGimmickTimeInputs = new HashMap<>();

    public final static int customizableSlots = 4;

    public static void sandboxInventory(Player player, PlayingField field) {
        Inventory inventory = Bukkit.createInventory(null, 27, Component.text("Sandbox Settings"));
        populateSandboxInventory(inventory, field);
        inventoryMappings.put(inventory, new SandboxInfo(MenuType.BASE, field));
        player.openInventory(inventory);
    }

    private static void populateSandboxInventory(Inventory inventory, PlayingField field) {
        inventory.clear();

        inventory.setItem(4, noHoleGarbageItem());
        inventory.setItem(10, wallSettingsItem());
        inventory.setItem(11, wallTimeItem(field.getQueue().getWallActiveTime()));
        inventory.setItem(12, displaySlotsItem());
        inventory.setItem(13, infoItem());
        inventory.setItem(14, highlightIncorrectBlocksItem(field.highlightIncorrectBlocksEnabled()));
        inventory.setItem(15, infiniteReachItem(field.infiniteReachEnabled()));
        inventory.setItem(16, gimmickItem());
        inventory.setItem(22, messyGarbageItem());
        fillEmptySpace(inventory, glassBorder());
    }

    @EventHandler
    public void onCloseInventory(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        inventoryMappings.remove(inventory);
        displaySlotMappings.remove(inventory);
    }

    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (pendingWallTimeInputs.containsKey(player) || pendingGimmickTimeInputs.containsKey(player)) {
            event.setCancelled(true);
            String message = ((TextComponent)event.message()).content();
            double seconds;
            try {
                seconds = Double.parseDouble(message);
                if (seconds < 0) {
                    player.sendMessage(minimessage.deserialize("<red>Time cannot be negative!"));
                    pendingWallTimeInputs.remove(player);
                    pendingGimmickTimeInputs.remove(player);
                    return;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(minimessage.deserialize("<red>Not a number"));
                pendingWallTimeInputs.remove(player);
                pendingGimmickTimeInputs.remove(player);
                return;
            }

            if (pendingWallTimeInputs.containsKey(player)) {
                PlayingField field = pendingWallTimeInputs.remove(player);
                if (!field.getPlayers().contains(player)) return; // Players who left may not use this
                field.getQueue().setWallActiveTime((int) (seconds * 20));
            } else if (pendingGimmickTimeInputs.containsKey(player)) {
                ModifierEvent gimmick = pendingGimmickTimeInputs.remove(player);
                if (gimmick == null) {
                    player.sendMessage(minimessage.deserialize("<red>No event to activate...?"));
                } else {
                    if (!gimmick.getPlayingField().getPlayers().contains(player)) return; // Players who left may not use this
                    gimmick.setTicksRemaining((int) (seconds * 20));
                    Bukkit.getScheduler().runTask(FillInTheWall.getInstance(), gimmick::activate);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // guard block
        // todo prevent players who aren't playing from using the sandbox
        Inventory inventory = event.getInventory();
        if (!inventoryMappings.containsKey(inventory)) return;
        event.setCancelled(true);
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;
        String itemString = clickedItem.getItemMeta().getPersistentDataContainer()
                .get(LobbyItems.itemTypeKey, PersistentDataType.STRING);
        if (itemString == null) return;

        MenuType type = inventoryMappings.get(inventory).type;
        PlayingField field = inventoryMappings.get(inventory).field;
        Player player = (Player) event.getWhoClicked();
        if (!field.getPlayers().contains(player)) return; // If the player somehow has the inventory open without being in game
        switch (type) {
            case BASE -> {
                switch (itemString) {
                    case "WALL_GENERATION" -> {
                        inventory.close();
                        wallGenerationInventory(player, field);
                    }
                    case "DISPLAY_SLOTS" -> {
                        inventory.close();
                        displaySlotsInventory(player, field);
                    }
                    case "GIMMICK" -> {
                        inventory.close();
                        gimmickInventory(player, field);
                    }
                    case "NO_HOLE_GARBAGE" -> {
                        Wall wall = new Wall(field.getLength(), field.getHeight());
                        field.getScorer().addGarbageToQueue(wall);
                        player.sendMessage(minimessage.deserialize("<gray>The words \"clean\" and \"garbage\" don't go together..."));
                    }
                    case "MESSY_GARBAGE" -> {
                        Wall wall = new Wall(field.getLength(), field.getHeight());
                        wall.generateHoles(10, 0, false);
                        field.getScorer().addGarbageToQueue(wall);
                        player.sendMessage(minimessage.deserialize("<yellow>Cheesy, greasy, you might say..."));
                    }
                    case "INFINITE_REACH" -> {
                        field.setInfiniteReach(!field.infiniteReachEnabled());
                        populateSandboxInventory(inventory, field);
                    }
                    case "HIGHLIGHT_INCORRECT_BLOCKS" -> {
                        field.setHighlightIncorrectBlocks(!field.highlightIncorrectBlocksEnabled());
                        populateSandboxInventory(inventory, field);
                    }
                    case "WALL_TIME" -> {
                        player.sendMessage(minimessage.deserialize("<gray>Enter new wall time in chat (seconds):"));
                        pendingWallTimeInputs.put(player, field);
                        inventory.close();
                    }
                }
            }

            case WALL_GENERATION -> {
                switch (itemString) {
                    case "RANDOM_HOLE_COUNT" -> {
                        if (event.isLeftClick()) {
                            field.getQueue().setRandomHoleCount(field.getQueue().getRandomHoleCount() + 1);
                        } else if (event.isRightClick()) {
                            field.getQueue().setRandomHoleCount(Math.max(0, field.getQueue().getRandomHoleCount() - 1));
                        }
                        populateWallGenerationInventory(inventory, field);
                    }
                    case "CONNECTED_HOLE_COUNT" -> {
                        if (event.isLeftClick()) {
                            field.getQueue().setConnectedHoleCount(field.getQueue().getConnectedHoleCount() + 1);
                        } else if (event.isRightClick()) {
                            field.getQueue().setConnectedHoleCount(Math.max(0, field.getQueue().getConnectedHoleCount() - 1));
                        }
                        populateWallGenerationInventory(inventory, field);
                    }
                    case "HOLE_SIZE" -> {
                        field.getQueue().setRandomizeFurther(!field.getQueue().isRandomizeFurther());
                        populateWallGenerationInventory(inventory, field);
                    }
                    case "BACK_ITEM" -> {
                        inventory.close();
                        sandboxInventory(player, field);
                    }
                }
            }

            case DISPLAY_SLOTS -> {
                String name = ((TextComponent) clickedItem.getItemMeta().displayName()).content();
                switch (itemString) {
                    case "DISPLAY_SLOT_SELECT" -> {
                        for (int i = 0; i < customizableSlots; i++) {
                            if (name.equals("Display Slot " + (i + 1))) {
                                displayTypeUserInput(player, field, i);
                                return;
                            }
                        }
                    }
                    case "BACK_ITEM" -> {
                        inventory.close();
                        sandboxInventory(player, field);
                    }
                }
            }

            case DISPLAY_SLOTS_PICK -> {
                String name = ((TextComponent) clickedItem.getItemMeta().displayName()).content();
                if (itemString.equals("DISPLAY_TYPE_SELECT")) {
                    DisplayType displayType;
                    try {
                        displayType = DisplayType.valueOf(name);
                    } catch (IllegalArgumentException e) {
                        player.sendMessage(minimessage.deserialize("<red>Invalid display type?"));
                        inventory.close();
                        return;
                    }
                    int slot = displaySlotMappings.get(inventory);
                    field.setDisplaySlot(slot, displayType);
                    inventory.close();
                    displaySlotsInventory(player, field);
                } else if (itemString.equals("BACK_ITEM")) {
                    inventory.close();
                    displaySlotsInventory(player, field);
                }
            }

            case GIMMICK -> {
                String name = ((TextComponent) clickedItem.getItemMeta().displayName()).content();
                if (itemString.equals("MODIFIER_EVENT")) {
                    ModifierEvent gimmick;
                    try {
                        gimmick = ModifierEvent.Type.valueOf(name).createEvent();
                        if (gimmick == null) {
                            field.endEvent();
                            return;
                        }
                    } catch (IllegalArgumentException e) {
                        player.sendMessage(minimessage.deserialize("<red>Unknown modifier"));
                        return;
                    }

                    gimmick.setTicksRemaining(30 * 20);
                    gimmick.setPlayingField(field);
                    gimmick.additionalInit(field.getLength(), field.getHeight());
                    player.sendMessage(minimessage.deserialize("<gray>Enter gimmick duration in chat (seconds):"));
                    pendingGimmickTimeInputs.put(player, gimmick);

                    inventory.close();
                } else if (itemString.equals("BACK_ITEM")) {
                    inventory.close();
                    sandboxInventory(player, field);
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
                minimessage.deserialize("<!italic><gray>How long it takes for walls"),
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

    private static ItemStack glassBorder() {
        ItemStack border = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = border.getItemMeta();
        meta.setHideTooltip(true);
        border.setItemMeta(meta);
        return border;
    }

    // Wall generation

    private static void wallGenerationInventory(Player player, PlayingField field) {
        Inventory inventory = Bukkit.createInventory(null, 27, Component.text("Wall Generation Settings"));
        inventoryMappings.put(inventory, new SandboxInfo(MenuType.WALL_GENERATION, field));
        populateWallGenerationInventory(inventory, field);
        player.openInventory(inventory);
    }

    private static void populateWallGenerationInventory(Inventory inventory, PlayingField field) {
        inventory.clear();

        inventory.setItem(18, InventoryMenus.backItem("Sandbox Settings"));
        inventory.setItem(11, randomHoleItem(field.getQueue().getRandomHoleCount()));
        inventory.setItem(13, connectedHoleItem(field.getQueue().getConnectedHoleCount()));
        inventory.setItem(15, randomTotalHolesItem(field.getQueue().isRandomizeFurther()));
        fillEmptySpace(inventory, glassBorder());
    }
    private static ItemStack randomHoleItem(int count) {
        ItemStack item = Utils.createGuiItem(Material.RED_MUSHROOM_BLOCK, minimessage.deserialize("<!italic><red>Random Hole Count"),
                minimessage.deserialize("<!italic><gray>Sets the number of completely random holes to generate."),
                Component.empty(), minimessage.deserialize("<!italic><aqua>Currently set to " + count),
                minimessage.deserialize("<!italic><yellow>Right click to decrease!"),
                minimessage.deserialize("<!italic><yellow>Left click to increase!"));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(LobbyItems.itemTypeKey, PersistentDataType.STRING, "RANDOM_HOLE_COUNT");
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack connectedHoleItem(int count) {
        ItemStack item = Utils.createGuiItem(Material.BROWN_MUSHROOM_BLOCK, minimessage.deserialize("<!italic><color:#fabd7f>Connected Hole Count"),
                minimessage.deserialize("<!italic><gray>Sets the number of connected holes to generate."),
                minimessage.deserialize("<!italic><gray>These generate after the random holes and only next to existing holes"),
                Component.empty(), minimessage.deserialize("<!italic><aqua>Currently set to " + count),
                minimessage.deserialize("<!italic><yellow>Right click to decrease!"),
                minimessage.deserialize("<!italic><yellow>Left click to increase!"));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(LobbyItems.itemTypeKey, PersistentDataType.STRING, "CONNECTED_HOLE_COUNT");
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack randomTotalHolesItem(boolean enabled) {
        ItemStack item = Utils.createGuiItem(Material.OAK_BUTTON, minimessage.deserialize("<!italic><yellow>Randomized Hole Count"),
                minimessage.deserialize("<!italic><gray>Whether to generate the same amount of holes each wall or not."),
                Component.empty(), Utils.statusComponent(enabled),
                minimessage.deserialize("<!italic><yellow>Click to toggle!"));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(LobbyItems.itemTypeKey, PersistentDataType.STRING, "HOLE_SIZE");
        item.setItemMeta(meta);
        return item;
    }

    // Display slots

    public static void displaySlotsInventory(Player player, PlayingField field) {
        Inventory inventory = Bukkit.createInventory(null, 27, minimessage.deserialize("<!italic>Display Slots"));
        inventoryMappings.put(inventory, new SandboxInfo(MenuType.DISPLAY_SLOTS, field));
        populateDisplaySlotsInventory(inventory, field);
        player.openInventory(inventory);
    }

    private static void populateDisplaySlotsInventory(Inventory inventory, PlayingField field) {
        inventory.clear();

        DisplayType[] displayTypes = field.getDisplaySlots();
        ItemStack[] itemStacks = new ItemStack[customizableSlots];
        for (int i = 0; i < customizableSlots; i++) {
            itemStacks[i] = Utils.createGuiItem(Material.PAINTING, minimessage.deserialize("<!italic>Display Slot " + (i + 1)),
                    minimessage.deserialize("<!italic>" + displayTypes[i].name()),
                    minimessage.deserialize("<!italic><yellow>Click to edit"));
            ItemMeta meta = itemStacks[i].getItemMeta();
            meta.getPersistentDataContainer().set(LobbyItems.itemTypeKey, PersistentDataType.STRING, "DISPLAY_SLOT_SELECT");
            itemStacks[i].setItemMeta(meta);

        }

        inventory.setItem(18, InventoryMenus.backItem("Sandbox Settings"));
        inventory.setItem(10, itemStacks[0]);
        inventory.setItem(12, itemStacks[1]);
        inventory.setItem(14, itemStacks[2]);
        inventory.setItem(16, itemStacks[3]);
        fillEmptySpace(inventory, glassBorder());
    }

    private void displayTypeUserInput(Player player, PlayingField field, int slot) {
        Inventory inventory = Bukkit.createInventory(null, 27, Component.text("select a type... i got lazy"));
        inventory.setItem(18, InventoryMenus.backItem("Display Slots"));
        for (DisplayType displayType : DisplayType.values()) {
            ItemStack item = new ItemStack(Material.PAINTING);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(minimessage.deserialize("<!italic>" + displayType.toString()));
            meta.getPersistentDataContainer().set(LobbyItems.itemTypeKey, PersistentDataType.STRING, "DISPLAY_TYPE_SELECT");
            item.setItemMeta(meta);
            inventory.addItem(item);
        }
        fillEmptySpace(inventory, glassBorder());
        inventoryMappings.put(inventory, new SandboxInfo(MenuType.DISPLAY_SLOTS_PICK, field));
        displaySlotMappings.put(inventory, slot);
        player.openInventory(inventory);
    }

    // Gimmick activation

    public static void gimmickInventory(Player player, PlayingField field) {
        Inventory inventory = Bukkit.createInventory(null, 27, Component.text("Gimmicks"));
        inventoryMappings.put(inventory, new SandboxInfo(MenuType.GIMMICK, field));
        inventory.setItem(18, InventoryMenus.backItem("Sandbox Settings"));

        for (ModifierEvent.Type type : ModifierEvent.Type.values()) {
            ItemStack item = new ItemStack(Material.BLAZE_POWDER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(minimessage.deserialize("<!italic>" + type.toString()));
            meta.getPersistentDataContainer().set(LobbyItems.itemTypeKey, PersistentDataType.STRING, "MODIFIER_EVENT");
            item.setItemMeta(meta);
            inventory.addItem(item);
        }

        fillEmptySpace(inventory, glassBorder());

        player.openInventory(inventory);
    }
}
