package com.articreep.fillinthewall.modifiers;

import com.articreep.fillinthewall.FillInTheWall;
import com.articreep.fillinthewall.game.PlayingField;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public class Unsupported extends ModifierEvent implements Listener
{
    private final static MiniMessage miniMessage = MiniMessage.miniMessage();

    @Override
    public ModifierEvent copy() {
        return new Unsupported();
    }

    @Override
    public void playActivateSound() {
        field.playSoundToPlayers(Sound.BLOCK_VAULT_DEACTIVATE, 1, 1);
    }

    @Override
    public void playDeactivateSound() {
        field.playSoundToPlayers(Sound.BLOCK_VAULT_ACTIVATE, 1, 1);
    }

    @Override
    public void activate() {
        super.activate();
        Bukkit.getPluginManager().registerEvents(this, FillInTheWall.getInstance());
        field.sendTitleToPlayers(miniMessage.deserialize("<color:#9A5F4A>Unsupported"),
                Component.text("Support blocks are disabled.."), 0, 40, 10);
    }

    @Override
    public void end() {
        super.end();
        HandlerList.unregisterAll(this);
        field.sendTitleToPlayers(Component.empty(), Component.text("Support blocks are reenabled!"),
                0, 20, 10);
    }

    @EventHandler
    public void onSupportBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!field.getPlayers().contains(player)) return;
        if (event.getBlockPlaced().getType() == PlayingField.copperSupportItem().getType()) {
            event.setCancelled(true);
            player.sendMessage(miniMessage.deserialize(
                    "<color:#9A5F4A>Your support block is currently <bold>un</bold>supported!"));
        }
    }
}
