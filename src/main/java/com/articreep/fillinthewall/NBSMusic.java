package com.articreep.fillinthewall;

import com.xxmicloxx.NoteBlockAPI.model.Song;
import com.xxmicloxx.NoteBlockAPI.utils.NBSDecoder;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;

public class NBSMusic {
    private static Song lobbyMusic;
    private static Location lobbyMusicLocation;
    public static boolean enabled = true;

    public static void loadConfig(FileConfiguration config) {
        String filename = config.getString("lobby-music.filename");
        File musicFolder = new File(FillInTheWall.getInstance().getDataFolder(), "music");

        File file = new File(musicFolder, filename);
        if (file.exists()) {
            lobbyMusic = NBSDecoder.parse(file);
        } else {
            FillInTheWall.getInstance().getLogger().info("No lobby music provided/could not find file");
        }

        lobbyMusicLocation = config.getLocation("lobby-music.location");
    }

    public static Location getLobbyMusicLocation() {
        return lobbyMusicLocation;
    }

    public static Song getLobbyMusic() {
        return lobbyMusic;
    }
}
