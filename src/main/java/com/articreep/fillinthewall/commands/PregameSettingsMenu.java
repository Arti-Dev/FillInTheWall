package com.articreep.fillinthewall.commands;

import com.articreep.fillinthewall.FillInTheWall;
import com.articreep.fillinthewall.game.PlayingFieldManager;
import com.articreep.fillinthewall.game.DisplayType;
import com.articreep.fillinthewall.gamemode.GamemodeAttribute;
import com.articreep.fillinthewall.gamemode.GamemodeSettings;
import com.articreep.fillinthewall.modifiers.ModifierEvent;
import com.articreep.fillinthewall.multiplayer.Pregame;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.javatuples.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PregameSettingsMenu implements CommandExecutor, Listener {
    private final Map<Inventory, Pregame> inventories = new HashMap<>();
    private final Map<Inventory, Pregame> subMenus = new HashMap<>();
    private final NamespacedKey typeKey = new NamespacedKey(FillInTheWall.getInstance(), "attributeType");
    private final Map<Player, Pair<Pregame, GamemodeAttribute>> pendingIntegerInput = new HashMap<>();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (sender instanceof Player player) {
            if (!sender.isOp()) {
                sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>You don't have permission to do that."));
                return true;
            }
            Pregame pregame = getPregame(player);
            if (pregame != null) {
                openSettingsInventory(player, pregame);
            } else {
                sender.sendMessage("You must be in a world with a pregame timer to use this command");

            }
        } else {
            sender.sendMessage("Only players can use this command");
        }
        return true;
    }

    private Inventory createSettingsInventory(Pregame pregame) {
        GamemodeSettings settings = pregame.getSettings();
        Inventory inventory = Bukkit.createInventory(null, 54, Component.text("Pregame Settings"));
        for (GamemodeAttribute attribute : GamemodeAttribute.values()) {
            inventory.addItem(createSettingItem(settings, attribute));
        }
        if (inventory.getContents()[inventory.getSize() - 1] == null) {
            ItemStack reset = new ItemStack(Material.BARRIER);
            ItemMeta meta = reset.getItemMeta();
            meta.displayName(MiniMessage.miniMessage().deserialize("<red>Reset to defaults"));
            meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, "reset");
            reset.setItemMeta(meta);
            inventory.setItem(inventory.getSize() - 1, reset);
        }
        return inventory;
    }

    private Pregame getPregame(Player player) {
        World world = player.getWorld();
        if (PlayingFieldManager.pregame != null && world.equals(PlayingFieldManager.pregame.getWorld())) {
            return PlayingFieldManager.pregame;
        } else if (PlayingFieldManager.vsPregame != null && world.equals(PlayingFieldManager.vsPregame.getWorld())) {
            return PlayingFieldManager.vsPregame;
        } else {
            return null;
        }
    }

    private void openSettingsInventory(Player player, Pregame pregame) {
        Inventory inventory = createSettingsInventory(pregame);
        inventories.put(inventory, pregame);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onItemDrag(InventoryDragEvent event) {
        Inventory inventory = event.getInventory();
        if (inventories.containsKey(inventory)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemClick(InventoryClickEvent event) {
        Inventory inventory = event.getInventory();
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        if (inventories.containsKey(inventory)) {
            event.setCancelled(true);
            PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
            String type = container.getOrDefault(typeKey, PersistentDataType.STRING, "");
            switch (type) {
                case "boolean" -> {
                    GamemodeSettings settings = inventories.get(inventory).getSettings();
                    GamemodeAttribute attribute = GamemodeAttribute.valueOf(item.getItemMeta().getDisplayName());
                    settings.setAttribute(attribute, !settings.getBooleanAttribute(attribute));
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
                    inventory.setItem(event.getSlot(), createSettingItem(settings, attribute));
                }
                case "integer" -> {
                    GamemodeAttribute attribute = GamemodeAttribute.valueOf(item.getItemMeta().getDisplayName());
                    integerUserInput(player, inventories.get(inventory), attribute);
                }
                case "displayType" -> {
                    GamemodeAttribute attribute = GamemodeAttribute.valueOf(item.getItemMeta().getDisplayName());
                    displayTypeUserInput(player, inventories.get(inventory), attribute);
                }
                case "modifierEvent" -> {
                    GamemodeAttribute attribute = GamemodeAttribute.valueOf(item.getItemMeta().getDisplayName());
                    modifierTypeUserInput(player, inventories.get(inventory), attribute);
                }
                case "reset" -> {
                    Pregame pregame = inventories.get(inventory);
                    GamemodeSettings defaultSettings = pregame.getGamemode().getDefaultSettings();
                    for (GamemodeAttribute attribute : GamemodeAttribute.values()) {
                        pregame.getSettings().setAttribute(attribute, defaultSettings.getAttribute(attribute));
                    }
                    player.closeInventory();
                    player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1, 1);
                    player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Settings reset!"));
                }
            }
        } else if (subMenus.containsKey(inventory)) {
            event.setCancelled(true);
            Pregame pregame = subMenus.get(inventory);
            GamemodeSettings settings = pregame.getSettings();
            GamemodeAttribute attribute = GamemodeAttribute.valueOf(event.getView().getTitle());
            PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
            String type = container.getOrDefault(typeKey, PersistentDataType.STRING, "");
            switch (type) {
                case "displayType" -> {
                    DisplayType displayType = DisplayType.valueOf(item.getItemMeta().getDisplayName());
                    settings.setAttribute(attribute, displayType);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
                    player.closeInventory();
                    openSettingsInventory(player, pregame);
                }
                case "modifierEvent" -> {
                    ModifierEvent.Type modifierType = ModifierEvent.Type.valueOf(item.getItemMeta().getDisplayName());
                    settings.setAttribute(attribute, modifierType);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
                    player.closeInventory();
                    openSettingsInventory(player, pregame);
                }
            }

        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        inventories.remove(event.getInventory());
        subMenus.remove(event.getInventory());
    }

    @EventHandler
    public void onPlayerDC(PlayerQuitEvent event) {
        pendingIntegerInput.remove(event.getPlayer());
    }

    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        if (!pendingIntegerInput.containsKey(event.getPlayer())) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        Pair<Pregame, GamemodeAttribute> pair = pendingIntegerInput.get(player);
        String message = ((TextComponent)event.message()).content();
        try {
            int value = Integer.parseInt(message);
            pair.getValue0().getSettings().setAttribute(pair.getValue1(), value);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            player.sendMessage("Value set to " + value);
        } catch (NumberFormatException e) {
            player.sendMessage("Invalid number");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
        } finally {
            Bukkit.getScheduler().runTask(FillInTheWall.getInstance(), () -> {
                pendingIntegerInput.remove(player);
                openSettingsInventory(player, pair.getValue0());
            });
        }
    }

    private ItemStack createSettingItem(GamemodeSettings settings, GamemodeAttribute attribute) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(attribute.toString()));
        meta.lore(List.of(MiniMessage.miniMessage().deserialize(
                "Value: " + settings.getAttribute(attribute))));
        PersistentDataContainer container = meta.getPersistentDataContainer();

        if (attribute.getType() == Boolean.class) {
            container.set(typeKey, PersistentDataType.STRING, "boolean");
            if (settings.getBooleanAttribute(attribute)) {
                item = new ItemStack(Material.LIME_DYE);
            } else {
                item = new ItemStack(Material.RED_DYE);
            }
        } else if (attribute.getType() == Integer.class) {
            container.set(typeKey, PersistentDataType.STRING, "integer");
            item = new ItemStack(Material.CLOCK);
        } else if (attribute.getType() == DisplayType.class) {
            container.set(typeKey, PersistentDataType.STRING, "displayType");
            item = new ItemStack(Material.PAINTING);
        } else if (attribute.getType() == ModifierEvent.Type.class) {
            container.set(typeKey, PersistentDataType.STRING, "modifierEvent");
            item = new ItemStack(Material.BLAZE_POWDER);
        }


        item.setItemMeta(meta);
        return item;
    }

    private void integerUserInput(Player player, Pregame pregame, GamemodeAttribute attribute) {
        player.closeInventory();
        player.sendMessage("Enter a new value for " + attribute);
        pendingIntegerInput.put(player, Pair.with(pregame, attribute));
    }

    private void displayTypeUserInput(Player player, Pregame pregame, GamemodeAttribute attribute) {
        player.closeInventory();
        Inventory inventory = Bukkit.createInventory(null, 27, Component.text(attribute.toString()));
        for (DisplayType displayType : DisplayType.values()) {
            ItemStack item = new ItemStack(Material.PAINTING);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(displayType.toString()));
            meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, "displayType");
            item.setItemMeta(meta);
            inventory.addItem(item);
        }
        subMenus.put(inventory, pregame);
        player.openInventory(inventory);
    }

    private void modifierTypeUserInput(Player player, Pregame pregame, GamemodeAttribute attribute) {
        player.closeInventory();
        Inventory inventory = Bukkit.createInventory(null, 27, Component.text(attribute.toString()));
        for (ModifierEvent.Type type : ModifierEvent.Type.values()) {
            ItemStack item = new ItemStack(Material.BLAZE_POWDER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(type.toString()));
            meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, "modifierEvent");
            item.setItemMeta(meta);
            inventory.addItem(item);
        }
        subMenus.put(inventory, pregame);
        player.openInventory(inventory);
    }
}
