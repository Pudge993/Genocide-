package syrnic.genocide.config;

import java.util.Locale;

public enum JoinMessageMode {
    OFF,
    FIRST_JOIN,
    EVERY_JOIN;

    public static JoinMessageMode fromString(String value) {
        if (value == null) {
            return FIRST_JOIN;
        }
        try {
            return JoinMessageMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return FIRST_JOIN;
        }
    }
}
