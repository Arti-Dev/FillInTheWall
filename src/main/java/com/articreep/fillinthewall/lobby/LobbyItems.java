package com.articreep.fillinthewall.lobby;

import com.articreep.fillinthewall.Database;
import com.articreep.fillinthewall.FillInTheWall;
import com.articreep.fillinthewall.game.PlayingField;
import com.articreep.fillinthewall.multiplayer.Pregame;
import com.articreep.fillinthewall.playerinfo.InventoryMenus;
import com.articreep.fillinthewall.playerinfo.PlayerLevels;
import com.articreep.fillinthewall.utils.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.sql.SQLException;
import java.util.List;

public class LobbyItems implements Listener {
    public static final NamespacedKey itemTypeKey = new NamespacedKey(FillInTheWall.getInstance(), "itemType");
    private static final MiniMessage miniMessage = MiniMessage.miniMessage();

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


//        // Check if an item already exists at slot 8
//        ItemStack existingItem = player.getInventory().getItem(8);
//        if (existingItem != null && existingItem.getType() != Material.AIR) {
//            // Just add it anywhere
//            player.getInventory().addItem(item);
//        } else {
//            player.getInventory().setItem(8, item);
//        }
        player.getInventory().setItem(8, item);
    }

    public static void giveGimmicklessMenuItem(Player player) {
        boolean enabled = Pregame.isGimmicklessPlayer(player);
        String color = enabled ? "<green>" : "<red>";
        ItemStack item = Utils.createGuiItem(Material.BLAZE_POWDER, miniMessage.deserialize(color + "Gimmickless Mode"),
                miniMessage.deserialize("<!italic><gray>Tired of chaotic gimmicks?"),
                miniMessage.deserialize("<!italic><gray>Gimmickless mode allows you to play multiplayer"),
                miniMessage.deserialize("<!italic><gray>without them, at a slight cost to your score."),
                miniMessage.deserialize("<red>Requires player level 5"),
                Component.empty(), Utils.statusComponent(enabled), miniMessage.deserialize("<!italic><yellow>Right-click to toggle!"));

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(PlayingField.gameKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(itemTypeKey, PersistentDataType.STRING, "GIMMICKLESS_LOBBY_ITEM");
        if (enabled) meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        player.getInventory().setItem(7, item);
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

            Player player = event.getPlayer();
            if (itemType.equals("PROFILE_LOBBY_ITEM")) {
                InventoryMenus.profileInventory(player);
            } else if (itemType.equals("GIMMICKLESS_LOBBY_ITEM") || event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                try {
                    if (!Database.isOfflineMode() && PlayerLevels.getLevel(player.getUniqueId()).getValue0() < 5) {
                        player.sendMessage(miniMessage.deserialize("<red>You must be at least level 5 to toggle Gimmickless Mode!"));
                        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1, 1);
                        return;
                    }
                } catch (SQLException e) {
                    FillInTheWall.getInstance().getSLF4JLogger().error("Something went wrong while checking player level");
                    e.printStackTrace();
                }

                boolean enabled = Pregame.isGimmicklessPlayer(player);
                if (enabled) {
                    Pregame.removeGimmicklessPlayer(player);
                } else {
                    Pregame.addGimmicklessPlayer(player);
                }
                giveGimmicklessMenuItem(player);
                player.playSound(player, Sound.UI_BUTTON_CLICK, 1, 1);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getCurrentItem() == null) return;
        if (!event.getCurrentItem().hasItemMeta()) return;
        PersistentDataContainer container = event.getCurrentItem().getItemMeta().getPersistentDataContainer();
        if (container.has(itemTypeKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

//    public static boolean checkInventoryForItem(Player player, String itemType) {
//        for (ItemStack item : player.getInventory().getContents()) {
//            if (item != null && item.hasItemMeta()) {
//                PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
//                if (container.has(itemTypeKey, PersistentDataType.STRING)) {
//                    String type = container.get(itemTypeKey, PersistentDataType.STRING);
//                    if (type != null && type.equals(itemType)) {
//                        return true;
//                    }
//                }
//            }
//        }
//        return false;
//    }
}
