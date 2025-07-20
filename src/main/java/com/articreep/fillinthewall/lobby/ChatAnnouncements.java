package com.articreep.fillinthewall.lobby;

import com.articreep.fillinthewall.FillInTheWall;
import com.articreep.fillinthewall.playerinfo.PlayerSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ChatAnnouncements {
    private final static MiniMessage miniMessage = MiniMessage.miniMessage();
    private static final Component[] announcements = {
            miniMessage.deserialize("<blue>[⿻] <yellow>Pair up with a friend in Multiplayer with /fitw pair!"),
            miniMessage.deserialize("<blue>[⿻] <yellow>To leave a game, simply step off the playing field."),
            miniMessage.deserialize("<blue>[⿻] <yellow>Don't want to play Multiplayer? Run /fitw spectate."),
            miniMessage.deserialize("<blue>[⿻] <yellow>You can toggle <red>Gimmickless</red> mode with the Blaze Powder in your inventory (when not in a game)."),
            miniMessage.deserialize("<blue>[⿻] <yellow>You can disable these tips by clicking your head in your ninth slot (when not in a game).")
    };
    private static BukkitTask announcementTask;

    public static void startAnnouncements() {
        if (announcementTask != null) announcementTask.cancel();
        ArrayList<Component> messages = new ArrayList<>(List.of(announcements));


        announcementTask = new BukkitRunnable() {
            int i = 0;
            @Override
            public void run() {
                if (i >= messages.size()) {
                    i = 0;
                    Collections.shuffle(messages);
                }

                Component message = messages.get(i);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    if (PlayerSettings.getBooleanSettingOrDefault(uuid, PlayerSettings.BooleanSetting.TIPS)) {
                        syncSendMessage(player, message);
                    }
                }
            }
        }.runTaskTimerAsynchronously(FillInTheWall.getInstance(), 0, 20 * 60);
    }

    private static void syncSendMessage(Player player, Component message) {
        Bukkit.getScheduler().runTask(FillInTheWall.getInstance(), () -> {
            if (player.isOnline()) {
                player.sendMessage(message);
            }
        });
    }
}
