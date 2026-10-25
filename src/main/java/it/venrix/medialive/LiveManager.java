package it.venrix.medialive;

import it.venrix.medialive.cache.LiveData;
import it.venrix.medialive.config.MessageService;
import it.venrix.medialive.config.PluginConfig;
import it.venrix.medialive.database.LiveRepository;
import it.venrix.medialive.hook.LiveDisplayHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Punto centrale della logica: stato live, cache in memoria e cooldown del publish.
 * <p>
 * Placeholder e integrazione TAB leggono SOLO dalla cache, mai dal database.
 * Ogni variazione di stato aggiorna la cache, notifica l'hook della tablist e
 * persiste il dato in modo asincrono.
 */
public final class LiveManager {

    public static final String PERMISSION_USE = "venrixmedialive.use";
    public static final String PERMISSION_BYPASS_COOLDOWN = "venrixmedialive.bypass.cooldown";
    public static final String PERMISSION_RELOAD = "venrixmedialive.reload";

    private final PluginConfig config;
    private final MessageService messages;
    private final LiveRepository repository;

    /** Cache dello stato dei giocatori online. */
    private final Map<UUID, LiveData> cache = new ConcurrentHashMap<>();
    /** Ultimo broadcast per giocatore, in millisecondi epoch. */
    private final Map<UUID, Long> lastPublish = new ConcurrentHashMap<>();

    private final AtomicBoolean databaseReady = new AtomicBoolean(false);

    private volatile LiveDisplayHook displayHook;
    /** Operazioni extra da eseguire al reload (registrate dalla classe principale). */
    private volatile Runnable reloadHook;

    public LiveManager(PluginConfig config, MessageService messages, LiveRepository repository) {
        this.config = config;
        this.messages = messages;
        this.repository = repository;
    }

    // ------------------------------------------------------------------
    //  Ciclo di vita / dipendenze opzionali
    // ------------------------------------------------------------------

    /** Collega l'hook della tablist (puo' restare null se TAB non e' installato). */
    public void setDisplayHook(LiveDisplayHook displayHook) {
        this.displayHook = displayHook;
    }

    public LiveDisplayHook getDisplayHook() {
        return displayHook;
    }

    /**
     * Registra un'azione aggiuntiva da eseguire ad ogni {@code /live reload}
     * (es. ricostruzione della resource pack, refresh dei messaggi di ACF).
     */
    public void setReloadHook(Runnable reloadHook) {
        this.reloadHook = reloadHook;
    }

    public void setDatabaseReady(boolean ready) {
        databaseReady.set(ready);
    }

    public boolean isDatabaseReady() {
        return databaseReady.get();
    }

    // ------------------------------------------------------------------
    //  Cache
    // ------------------------------------------------------------------

    /** Inserisce in cache i dati letti dal database (chiamato al pre-login). */
    public void cache(UUID uuid, LiveData data) {
        cache.put(uuid, data);
    }

    /** Rimuove il giocatore dalla cache (chiamato al quit). */
    public void uncache(UUID uuid) {
        cache.remove(uuid);
        lastPublish.remove(uuid);
    }

    /** Dati in cache, oppure uno stato vuoto se il giocatore non e' caricato. */
    public LiveData get(UUID uuid) {
        return cache.getOrDefault(uuid, LiveData.empty());
    }

    public boolean isLive(UUID uuid) {
        LiveData data = cache.get(uuid);
        return data != null && data.isLive();
    }

    /** Link salvato, oppure stringa vuota se assente. */
    public String getUrl(UUID uuid) {
        LiveData data = cache.get(uuid);
        return data == null || data.getUrl() == null ? "" : data.getUrl();
    }

    // ------------------------------------------------------------------
    //  Azioni dei comandi
    // ------------------------------------------------------------------

    /** Gestisce {@code /live on}. */
    public void enable(Player player) {
        if (!databaseReady.get()) {
            messages.send(player, "general.not-ready");
            return;
        }
        UUID uuid = player.getUniqueId();
        LiveData data = cache.computeIfAbsent(uuid, key -> LiveData.empty());
        if (data.isLive()) {
            messages.send(player, "live.already-enabled");
            return;
        }
        applyState(uuid, data, true, data.getUrl());
        messages.send(player, "live.enabled");
    }

    /** Gestisce {@code /live off}. */
    public void disable(Player player) {
        if (!databaseReady.get()) {
            messages.send(player, "general.not-ready");
            return;
        }
        UUID uuid = player.getUniqueId();
        LiveData data = cache.computeIfAbsent(uuid, key -> LiveData.empty());
        if (!data.isLive()) {
            messages.send(player, "live.already-disabled");
            return;
        }
        applyState(uuid, data, false, data.getUrl());
        messages.send(player, "live.disabled");
    }

    /** Gestisce {@code /live publish <link>}. */
    public void publish(Player player, String link) {
        if (!databaseReady.get()) {
            messages.send(player, "general.not-ready");
            return;
        }

        // 1) validazione del link
        LinkValidator.Result result = LinkValidator.validate(link, config.getAllowedDomains());
        if (result == LinkValidator.Result.INVALID) {
            messages.send(player, "publish.invalid-link");
            return;
        }
        if (result == LinkValidator.Result.DOMAIN_NOT_ALLOWED) {
            messages.send(player, "publish.domain-not-allowed",
                    "{domains}", String.join(", ", config.getAllowedDomains()));
            return;
        }

        // 2) cooldown anti-spam del broadcast
        UUID uuid = player.getUniqueId();
        if (!player.hasPermission(PERMISSION_BYPASS_COOLDOWN)) {
            long remaining = remainingCooldownMillis(uuid);
            if (remaining > 0L) {
                messages.send(player, "publish.cooldown", "{time}", formatDuration(remaining));
                return;
            }
        }

        // 3) salva il link; il publish attiva automaticamente lo stato live
        LiveData data = cache.computeIfAbsent(uuid, key -> LiveData.empty());
        applyState(uuid, data, true, link);
        lastPublish.put(uuid, System.currentTimeMillis());

        // 4) conferma privata + broadcast globale con link cliccabile
        messages.send(player, "publish.success", "{url}", link);
        broadcast(player.getName(), link);
    }

    /** Gestisce {@code /live reload}. */
    public void reload() {
        config.load();
        messages.load();
        Runnable extra = reloadHook;
        if (extra != null) {
            extra.run();
        }
        LiveDisplayHook hook = displayHook;
        if (hook != null && hook.isActive()) {
            // Il simbolo puo' essere cambiato: riapplica a tutti gli online in live.
            hook.refreshAll();
        }
    }

    // ------------------------------------------------------------------
    //  Interni
    // ------------------------------------------------------------------

    /**
     * Aggiorna cache, tablist e database in quest'ordine: la tablist reagisce
     * subito, il salvataggio avviene in background.
     */
    private void applyState(UUID uuid, LiveData data, boolean live, String url) {
        data.setLive(live);
        data.setUrl(url);
        cache.put(uuid, data);

        LiveDisplayHook hook = displayHook;
        if (hook != null && hook.isActive()) {
            if (live) {
                hook.apply(uuid);
            } else {
                hook.remove(uuid);
            }
        }

        repository.save(uuid, live, url);
    }

    /** Costruisce e invia il broadcast rendendo cliccabile il token url. */
    private void broadcast(String playerName, String url) {
        Component hover = messages.get("publish.hover");
        Component link = Component.text(url)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(hover));

        Component message = messages.get("publish.broadcast", "{player}", playerName)
                .replaceText(TextReplacementConfig.builder()
                        .matchLiteral("{url}")
                        .replacement(link)
                        .build());

        Bukkit.broadcast(message);
    }

    private long remainingCooldownMillis(UUID uuid) {
        long cooldown = config.getCooldownSeconds() * 1000L;
        if (cooldown <= 0L) {
            return 0L;
        }
        Long last = lastPublish.get(uuid);
        if (last == null) {
            return 0L;
        }
        long elapsed = System.currentTimeMillis() - last;
        return Math.max(0L, cooldown - elapsed);
    }

    /** Formatta una durata in millisecondi come "4m 12s" oppure "12s". */
    private String formatDuration(long millis) {
        long totalSeconds = Math.max(1L, (millis + 999L) / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return minutes > 0L ? minutes + "m " + seconds + "s" : seconds + "s";
    }
}
