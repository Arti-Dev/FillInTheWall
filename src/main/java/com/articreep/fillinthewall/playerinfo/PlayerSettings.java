package com.articreep.fillinthewall.playerinfo;

import com.articreep.fillinthewall.Database;
import com.articreep.fillinthewall.FillInTheWall;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerSettings {

    private PlayerSettings() {
        // Prevent instantiation
    }

    private static final Map<UUID, Map<BooleanSetting, Boolean>> booleanSettingCache = new HashMap<>();

    public enum StringSetting {
        LEVEL_COLOR("DEFAULT");

        private final String value;
        StringSetting(String value) {
            this.value = value;
        }

        public String getDefault() {
            return value;
        }
    }

    public enum BooleanSetting {
        TIPS(true), MUSIC(true), ALT_SUPPORT_BLOCK(false), OTHERS_JOIN(true);

        private final boolean value;
        BooleanSetting(boolean value) {
            this.value = value;
        }
        public boolean getDefault() {
            return value;
        }
    }

    public static void loadSettings(UUID uuid) throws SQLException {
        Map<BooleanSetting, Boolean> settings = new HashMap<>();
        if (Database.isOfflineMode()) {
            // In offline mode, use default settings
            for (BooleanSetting setting : BooleanSetting.values()) {
                settings.put(setting, setting.getDefault());
            }
        } else {
            for (BooleanSetting setting : BooleanSetting.values()) {
                settings.put(setting, Database.getBooleanSetting(uuid, setting));
            }
        }
        booleanSettingCache.put(uuid, settings);
    }

    public static boolean getBooleanSetting(UUID uuid, BooleanSetting setting) throws SQLException {
        Map<BooleanSetting, Boolean> settings = booleanSettingCache.get(uuid);
        if (settings == null) {
            loadSettings(uuid);
            settings = booleanSettingCache.get(uuid);
        }
        return settings.get(setting);
    }

    public static void setBooleanSetting(UUID uuid, BooleanSetting setting, boolean value) {
        Map<BooleanSetting, Boolean> settings = booleanSettingCache.get(uuid);
        if (settings == null) {
            FillInTheWall.getInstance().getSLF4JLogger().error("Tried to set boolean setting for player {}, but settings were not loaded!", uuid);
            return;
        }
        settings.put(setting, value);
    }

    public static void writeToDatabase(UUID uuid) {
        if (Database.isOfflineMode()) return;
        Map<BooleanSetting, Boolean> settings = booleanSettingCache.get(uuid);
        if (settings == null) {
            return;
        }
        for (Map.Entry<BooleanSetting, Boolean> entry : settings.entrySet()) {
            Database.setBooleanSetting(uuid, entry.getKey(), entry.getValue());
        }
    }
}
