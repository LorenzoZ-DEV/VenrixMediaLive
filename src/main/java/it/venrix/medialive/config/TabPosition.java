package it.venrix.medialive.config;

/** Posizione del pallino nella tablist gestita da TAB. */
public enum TabPosition {
    PREFIX,
    SUFFIX;

    public static TabPosition parse(String raw, TabPosition fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
