package com.articreep.fillinthewall.playerinfo;

public class PlayerSettings {
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
}
