package it.venrix.medialive;

import co.aikar.commands.MessageKeys;
import co.aikar.commands.PaperCommandManager;
import it.venrix.medialive.command.LiveCommand;
import it.venrix.medialive.config.MessageService;
import it.venrix.medialive.config.PluginConfig;
import it.venrix.medialive.database.Database;
import it.venrix.medialive.database.LiveRepository;
import it.venrix.medialive.hook.LiveDisplayHook;
import it.venrix.medialive.hook.TabHook;
import it.venrix.medialive.listener.PlayerConnectionListener;
import it.venrix.medialive.placeholder.VenrixLiveExpansion;
import it.venrix.medialive.resourcepack.ResourcePackListener;
import it.venrix.medialive.resourcepack.ResourcePackService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Classe principale di VenrixMediaLive.
 * <p>
 * Si limita a inizializzare e chiudere i componenti: tutta la logica vive in
 * {@link LiveManager}. Il pool HikariCP e l'executor asincrono vengono chiusi in
 * {@link #onDisable()}.
 */
public final class VenrixMediaLive extends JavaPlugin {

    private PluginConfig pluginConfig;
    private MessageService messageService;
    private ExecutorService databaseExecutor;
    private Database database;
    private LiveRepository repository;
    private LiveManager liveManager;
    private PaperCommandManager commandManager;
    private ResourcePackService packService;

    /**
     * Dichiarato con il tipo dell'interfaccia: in questo modo la classe {@code TabHook}
     * (e con essa l'API di TAB) viene caricata dalla JVM solo dentro
     * {@link #setupTabHook()}, dopo aver verificato che TAB sia installato.
     */
    private LiveDisplayHook tabHook;

    @Override
    public void onEnable() {
        // 1) configurazione e messaggi
        this.pluginConfig = new PluginConfig(this);
        this.messageService = new MessageService(this, pluginConfig);

        // 2) executor dedicato: nessuna query tocca il main thread
        this.databaseExecutor = Executors.newFixedThreadPool(pluginConfig.getPoolSize(), threadFactory());
        this.database = new Database();
        this.repository = new LiveRepository(database, databaseExecutor, getLogger());

        // 3) logica centrale
        this.liveManager = new LiveManager(pluginConfig, messageService, repository);

        // 4) integrazioni opzionali e comandi
        setupDatabaseAsync();
        setupTabHook();
        setupPlaceholders();
        setupResourcePack();
        registerListeners();
        setupCommands();

        // Azioni extra eseguite ad ogni /live reload
        liveManager.setReloadHook(this::onReload);

        getLogger().info("VenrixMediaLive abilitato.");
    }

    @Override
    public void onDisable() {
        // I comandi vanno sganciati per primi, per non ricevere input durante lo shutdown.
        if (commandManager != null) {
            commandManager.unregisterCommands();
            commandManager = null;
        }

        // Ripristina i prefix/suffix originali di TAB e sgancia i listener dall'event bus.
        if (tabHook != null) {
            tabHook.shutdown();
            tabHook = null;
        }

        // Attende i salvataggi ancora in coda, poi chiude il pool.
        if (databaseExecutor != null) {
            databaseExecutor.shutdown();
            try {
                if (!databaseExecutor.awaitTermination(10L, TimeUnit.SECONDS)) {
                    databaseExecutor.shutdownNow();
                }
            } catch (InterruptedException ex) {
                databaseExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            databaseExecutor = null;
        }

        if (database != null) {
            database.close();
            database = null;
        }

        getLogger().info("VenrixMediaLive disabilitato.");
    }

    // ------------------------------------------------------------------
    //  Inizializzazione dei componenti
    // ------------------------------------------------------------------

    /** Crea il pool e la tabella fuori dal main thread, poi sblocca i comandi. */
    private void setupDatabaseAsync() {
        databaseExecutor.execute(() -> {
            try {
                database.connect(pluginConfig);
                repository.createTable();
                liveManager.setDatabaseReady(true);
                getLogger().info("Connessione a MariaDB stabilita, tabella 'venrix_media_live' pronta.");
                loadOnlinePlayers();
            } catch (Exception ex) {
                getLogger().log(Level.SEVERE, "Impossibile inizializzare il database: "
                        + "i comandi /live resteranno disattivati.", ex);
            }
        });
    }

    /**
     * Carica in cache i giocatori gia' online: serve quando il plugin viene abilitato
     * a server avviato (reload o plugin manager).
     */
    private void loadOnlinePlayers() {
        getServer().getScheduler().runTask(this, () -> {
            List<UUID> online = new ArrayList<>();
            for (Player player : getServer().getOnlinePlayers()) {
                online.add(player.getUniqueId());
            }
            if (online.isEmpty()) {
                return;
            }
            for (UUID uuid : online) {
                repository.load(uuid).thenAccept(data -> {
                    liveManager.cache(uuid, data);
                    if (data.isLive() && tabHook != null) {
                        // Le chiamate a TAB restano sul main thread per sicurezza.
                        getServer().getScheduler().runTask(this, () -> tabHook.apply(uuid));
                    }
                });
            }
        });
    }

    /** Attiva l'hook di TAB solo se il plugin e' presente e abilitato. */
    private void setupTabHook() {
        if (!pluginConfig.isTabEnabled()) {
            getLogger().info("Integrazione TAB disattivata da config (tab.enabled: false).");
            return;
        }

        Plugin tab = getServer().getPluginManager().getPlugin("TAB");
        if (tab == null || !tab.isEnabled()) {
            getLogger().info("TAB non installato: il pallino in tablist non verra' mostrato "
                    + "(il resto del plugin funziona normalmente).");
            return;
        }

        try {
            // Prima riga in cui vengono toccate le classi dell'API di TAB.
            TabHook hook = new TabHook(liveManager, pluginConfig, getLogger());
            if (hook.isActive()) {
                this.tabHook = hook;
                liveManager.setDisplayHook(hook);
            }
        } catch (Throwable throwable) {
            // Throwable e non Exception: copre anche NoClassDefFoundError da versioni
            // incompatibili dell'API di TAB.
            getLogger().log(Level.WARNING, "Hook TAB non inizializzato: " + throwable.getMessage(), throwable);
            this.tabHook = null;
        }
    }

    /** Registra l'espansione PlaceholderAPI solo se il plugin e' presente. */
    private void setupPlaceholders() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().info("PlaceholderAPI non installato: i placeholder %venrixlive_*% non saranno disponibili.");
            return;
        }
        try {
            new VenrixLiveExpansion(this, liveManager, pluginConfig).register();
            getLogger().info("Espansione PlaceholderAPI 'venrixlive' registrata.");
        } catch (Throwable throwable) {
            getLogger().log(Level.WARNING, "Registrazione dell'espansione PlaceholderAPI non riuscita: "
                    + throwable.getMessage(), throwable);
        }
    }

    /** Prepara la resource pack solo se abilitata in config. */
    private void setupResourcePack() {
        if (!pluginConfig.isPackEnabled()) {
            return;
        }
        ResourcePackService service = new ResourcePackService(pluginConfig, getLogger());
        if (!service.isUsable()) {
            return;
        }
        this.packService = service;
        getServer().getPluginManager().registerEvents(
                new ResourcePackListener(this, pluginConfig, messageService, service), this);
        getLogger().info("Resource pack abilitata: verra' inviata al join.");
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
                new PlayerConnectionListener(liveManager, repository, getLogger()), this);
    }

    private void setupCommands() {
        this.commandManager = new PaperCommandManager(this);
        applyCommandMessages();
        commandManager.registerCommand(new LiveCommand(liveManager, messageService));
    }

    /** Allinea i messaggi interni di ACF a quelli di messages.yml. */
    private void applyCommandMessages() {
        if (commandManager == null) {
            return;
        }
        String noPermission = messageService.legacy("general.no-permission");
        commandManager.getLocales().setDefaultLocale(Locale.ENGLISH);
        commandManager.getLocales().addMessage(Locale.ENGLISH, MessageKeys.PERMISSION_DENIED, noPermission);
        commandManager.getLocales().addMessage(Locale.ENGLISH, MessageKeys.PERMISSION_DENIED_PARAMETER, noPermission);
    }

    /** Eseguito da {@code /live reload} dopo il ricaricamento di config e messaggi. */
    private void onReload() {
        applyCommandMessages();
        if (packService != null) {
            packService.build();
        }
    }

    private ThreadFactory threadFactory() {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, "VenrixMediaLive-DB-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    // ------------------------------------------------------------------
    //  Accessori
    // ------------------------------------------------------------------

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public MessageService getMessageService() {
        return messageService;
    }

    public LiveManager getLiveManager() {
        return liveManager;
    }
}
