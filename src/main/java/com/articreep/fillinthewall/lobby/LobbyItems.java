package com.articreep.fillinthewall.lobby;

import com.articreep.fillinthewall.FillInTheWall;
import com.articreep.fillinthewall.game.PlayingField;
import com.articreep.fillinthewall.playerinfo.InventoryMenus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class LobbyItems implements Listener {
    public static final NamespacedKey itemTypeKey = new NamespacedKey(FillInTheWall.getInstance(), "itemType");

    public static void giveProfileMenuItem(Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(Component.text("Profile + Settings", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Right-click to view!", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(PlayingField.gameKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(itemTypeKey, PersistentDataType.STRING, "PROFILE_LOBBY_ITEM");
        item.setItemMeta(meta);


        // Check if an item already exists at slot 8
        ItemStack existingItem = player.getInventory().getItem(8);
        if (existingItem != null && existingItem.getType() != Material.AIR) {
            // Just add it anywhere
            player.getInventory().addItem(item);
        } else {
            player.getInventory().setItem(8, item);
        }
    }

    // Allows players to exclude themselves from the multiplayer queue
    // Or if a game is running, allows them to spectate
    public static void giveSpectateGameItem() {
        // todo
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getItem() == null) return;
        if (!event.getItem().hasItemMeta()) return;
        PersistentDataContainer container = event.getItem().getItemMeta().getPersistentDataContainer();
        if (container.has(itemTypeKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
            String itemType = container.get(itemTypeKey, PersistentDataType.STRING);
            if (itemType == null) return;

            if (itemType.equals("PROFILE_LOBBY_ITEM")) {
                InventoryMenus.profileInventory(event.getPlayer());
            }
        }
    }

    public static boolean checkInventoryForItem(Player player, String itemType) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.hasItemMeta()) {
                PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
                if (container.has(itemTypeKey, PersistentDataType.STRING)) {
                    String type = container.get(itemTypeKey, PersistentDataType.STRING);
                    if (type != null && type.equals(itemType)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
