package it.venrix.medialive.hook;

import java.util.UUID;

/**
 * Astrazione sull'integrazione che mostra il pallino in tablist.
 * <p>
 * Questa interfaccia NON referenzia alcuna classe di TAB: serve proprio a permettere
 * a {@code LiveManager} di parlare con {@code TabHook} senza che la JVM debba
 * caricare le classi dell'API di TAB quando il plugin non e' installato.
 */
public interface LiveDisplayHook {

    /** Applica il pallino al giocatore indicato (no-op se TAB non lo conosce ancora). */
    void apply(UUID uuid);

    /** Rimuove il pallino, ripristinando il valore originale configurato in TAB. */
    void remove(UUID uuid);

    /** Riapplica il pallino a tutti i giocatori online in live (usato dopo un reload). */
    void refreshAll();

    /** Rimuove il pallino da tutti e sgancia i listener. */
    void shutdown();

    /** {@code true} se l'hook e' realmente operativo. */
    boolean isActive();
}
