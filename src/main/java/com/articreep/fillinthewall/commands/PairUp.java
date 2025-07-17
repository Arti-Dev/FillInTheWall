package com.articreep.fillinthewall.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

public class PairUp implements Listener {
    private static final MiniMessage miniMessage = MiniMessage.miniMessage();

    // from -> to
    private static final Map<Player, Player> outgoingRequests = new HashMap<>();

    public record PlayerPair(Player player1, Player player2) {
        public Player getOtherPlayer(Player player) {
            // thanks for the ternary operator copilot
            return player.equals(player1) ? player2 : player1;
        }
        public boolean contains(Player player) {
            return player.equals(player1) || player.equals(player2);
        }
    }

    private static final Set<PlayerPair> pairs = new HashSet<>();

    public static boolean isPaired(Player player) {
        return pairs.stream().anyMatch(pair -> pair.contains(player));
    }

    private static void removePair(Player player) {
        pairs.removeIf(pair -> pair.contains(player));
    }

    public static PlayerPair getPair(Player player) {
        Optional<PlayerPair> pair = pairs.stream().filter(p -> p.contains(player)).findFirst();
        return pair.orElse(null);
    }

    public static Player getOtherPlayer(Player player) {
        Optional<PlayerPair> pair = pairs.stream().filter(p -> p.contains(player)).findFirst();
        return pair.map(playerPair -> playerPair.getOtherPlayer(player)).orElse(null);
    }

    private static void addPair(Player player1, Player player2) {
        pairs.add(new PlayerPair(player1, player2));
        outgoingRequests.remove(player1);
        outgoingRequests.remove(player2);
    }

    public static void leave(Player player) {
        if (isPaired(player)) {
            Player other = getOtherPlayer(player);
            removePair(player);
            player.sendMessage(miniMessage.deserialize("<red>You are no longer paired with " + other.getName()));
            other.sendMessage(miniMessage.deserialize("<red>You are no longer paired with " + player.getName()));
        } else {
            player.sendMessage("You are not paired with anyone");
        }
    }

    public static void request(Player player, Player other) {
        if (isPaired(player)) {
            Player existing = getOtherPlayer(player);
            player.sendMessage("You are already paired with " + existing.getName() + ". To leave, use /fitw pair leave");
            return;
        }
        if (isPaired(other)) {
            player.sendMessage(miniMessage.deserialize("<red>That player's already paired with someone else!"));
            return;
        }

        if (outgoingRequests.containsKey(other)) {
            // accept
            addPair(other, player);
            player.sendMessage(miniMessage.deserialize("<green>You are now paired with " + other.getName() + " for multiplayer games"));
            player.sendMessage(miniMessage.deserialize("<green>To leave, use /fitw pair leave"));
            other.sendMessage(miniMessage.deserialize("<green>You are now paired with " + player.getName() + " for multiplayer games"));
            other.sendMessage(miniMessage.deserialize("<green>To leave, use /fitw pair leave"));
        } else {
            // request
            outgoingRequests.put(player, other);
            player.sendMessage(miniMessage.deserialize("<green>Request sent to " + other.getName()));
            other.sendMessage(miniMessage.deserialize("<green>" + player.getName() + " has invited you to team up for multiplayer games!"));
            other.sendMessage(miniMessage.deserialize("<yellow>Click " +
                    "<bold><click:run_command:/fitw pair " + player.getName() + "><hover:show_text:'<yellow>Click to accept!'>here</hover></click></bold>" +
                    " or run /fitw pair " + player.getName() + " to accept!"));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (isPaired(player)) {
            Player other = getOtherPlayer(player);
            removePair(player);
            other.sendMessage(miniMessage.deserialize("<red>You are no longer paired with anyone"));
        }
        outgoingRequests.remove(player);
    }


}
