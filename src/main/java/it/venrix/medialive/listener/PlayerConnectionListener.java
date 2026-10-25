package it.venrix.medialive.listener;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import it.venrix.medialive.LiveManager;
import it.venrix.medialive.cache.LiveData;
import it.venrix.medialive.database.LiveRepository;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Popola e svuota la cache in memoria.
 * <p>
 * Il caricamento avviene su {@link AsyncPlayerPreLoginEvent}, che gira gia' fuori
 * dal main thread: possiamo attendere il risultato della query senza lag, e i dati
 * sono pronti prima che il giocatore entri davvero.
 * <p>
 * L'applicazione del pallino in tablist NON avviene qui: la gestisce l'hook di TAB
 * tramite il suo PlayerLoadEvent (TAB carica i giocatori in modo asincrono).
 */
public final class PlayerConnectionListener implements Listener {

    /** Oltre questo tempo si entra comunque, con stato di default. */
    private static final long LOAD_TIMEOUT_SECONDS = 5L;

    private final LiveManager liveManager;
    private final LiveRepository repository;
    private final Logger logger;

    public PlayerConnectionListener(LiveManager liveManager, LiveRepository repository, Logger logger) {
        this.liveManager = liveManager;
        this.repository = repository;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID uuid = event.getUniqueId();
        if (!liveManager.isDatabaseReady()) {
            liveManager.cache(uuid, LiveData.empty());
            return;
        }
        try {
            // Siamo su un thread asincrono: attendere qui e' sicuro.
            LiveData data = repository.load(uuid).get(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            liveManager.cache(uuid, data);
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Caricamento dati live non riuscito per " + uuid
                    + ", uso lo stato di default.", ex);
            liveManager.cache(uuid, LiveData.empty());
        }
    }

    /**
     * Copre i casi in cui il giocatore non entra mai davvero (kick durante il login,
     * server pieno, whitelist, ...): in quelle situazioni PlayerQuitEvent non viene
     * chiamato e la voce in cache resterebbe orfana.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onConnectionClose(PlayerConnectionCloseEvent event) {
        liveManager.uncache(event.getPlayerUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        liveManager.uncache(event.getPlayer().getUniqueId());
    }
}
