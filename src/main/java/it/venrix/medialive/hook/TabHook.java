package it.venrix.medialive.hook;

import it.venrix.medialive.LiveManager;
import it.venrix.medialive.config.PluginConfig;
import it.venrix.medialive.config.TabPosition;
import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.event.EventBus;
import me.neznamy.tab.api.event.EventHandler;
import me.neznamy.tab.api.event.player.PlayerLoadEvent;
import me.neznamy.tab.api.event.plugin.TabLoadEvent;
import me.neznamy.tab.api.tablist.TabListFormatManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.logging.Logger;

public final class TabHook implements LiveDisplayHook {

    private final LiveManager liveManager;
    private final PluginConfig config;
    private final Logger logger;

    private TabListFormatManager formatManager;
    private EventBus eventBus;
    private EventHandler<PlayerLoadEvent> playerLoadHandler;
    private EventHandler<TabLoadEvent> tabLoadHandler;
    private boolean active;

    public TabHook(LiveManager liveManager, PluginConfig config, Logger logger) {
        this.liveManager = liveManager;
        this.config = config;
        this.logger = logger;
        initialise();
    }

    private void initialise() {
        TabAPI api = TabAPI.getInstance();
        if (api == null) {
            logger.warning("TAB risulta installato ma la sua API non e' disponibile: hook disattivato.");
            return;
        }

        this.formatManager = api.getTabListFormatManager();
        if (formatManager == null) {
            logger.warning("Hook TAB disattivato: la funzione 'tablist-name-formatting' e' disabilitata in TAB. "
                    + "Attivala nella config di TAB per vedere il pallino in tablist.");
            return;
        }

        this.eventBus = api.getEventBus();
        if (eventBus == null) {
            logger.warning("Hook TAB disattivato: event bus di TAB non disponibile.");
            return;
        }

        // TAB carica i giocatori in modo asincrono: PlayerJoinEvent di Bukkit arriverebbe
        // troppo presto e getPlayer(uuid) restituirebbe null. Usiamo l'evento di TAB.
        this.playerLoadHandler = event -> {
            TabPlayer tabPlayer = event.getPlayer();
            if (tabPlayer != null && liveManager.isLive(tabPlayer.getUniqueId())) {
                applyTo(tabPlayer);
            }
        };
        eventBus.register(PlayerLoadEvent.class, playerLoadHandler);

        // Dopo un reload di TAB tutti i valori custom vengono azzerati: li rimettiamo.
        this.tabLoadHandler = event -> refreshAll();
        eventBus.register(TabLoadEvent.class, tabLoadHandler);

        this.active = true;
        logger.info("Hook TAB attivo (posizione: " + config.getTabPosition() + ").");
    }

    @Override
    public void apply(UUID uuid) {
        if (!active) {
            return;
        }
        TabPlayer tabPlayer = TabAPI.getInstance().getPlayer(uuid);
        // Controllo null obbligatorio: il giocatore potrebbe non essere ancora
        // stato caricato da TAB (caricamento asincrono).
        if (tabPlayer == null) {
            return;
        }
        applyTo(tabPlayer);
    }

    @Override
    public void remove(UUID uuid) {
        if (!active) {
            return;
        }
        TabPlayer tabPlayer = TabAPI.getInstance().getPlayer(uuid);
        if (tabPlayer == null) {
            return;
        }
        resetOf(tabPlayer);
    }

    @Override
    public void refreshAll() {
        if (!active) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (liveManager.isLive(player.getUniqueId())) {
                apply(player.getUniqueId());
            } else {
                remove(player.getUniqueId());
            }
        }
    }

    @Override
    public void shutdown() {
        if (!active) {
            return;
        }
        // Ripristina i valori originali per tutti prima di spegnersi.
        for (Player player : Bukkit.getOnlinePlayers()) {
            remove(player.getUniqueId());
        }
        if (eventBus != null) {
            if (playerLoadHandler != null) {
                eventBus.unregister(playerLoadHandler);
            }
            if (tabLoadHandler != null) {
                eventBus.unregister(tabLoadHandler);
            }
        }
        playerLoadHandler = null;
        tabLoadHandler = null;
        eventBus = null;
        formatManager = null;
        active = false;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    /** Aggiunge il simbolo al valore ORIGINALE, senza sovrascrivere la config di TAB. */
    private void applyTo(TabPlayer tabPlayer) {
        String symbol = config.getSymbolRaw();
        String separator = config.getTabSeparator();
        if (config.getTabPosition() == TabPosition.PREFIX) {
            String original = orEmpty(formatManager.getOriginalRawPrefix(tabPlayer));
            formatManager.setPrefix(tabPlayer, symbol + separator + original);
        } else {
            String original = orEmpty(formatManager.getOriginalRawSuffix(tabPlayer));
            formatManager.setSuffix(tabPlayer, original + separator + symbol);
        }
    }

    /** Passando null TAB torna al valore originale della sua configurazione. */
    private void resetOf(TabPlayer tabPlayer) {
        if (config.getTabPosition() == TabPosition.PREFIX) {
            formatManager.setPrefix(tabPlayer, null);
        } else {
            formatManager.setSuffix(tabPlayer, null);
        }
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
