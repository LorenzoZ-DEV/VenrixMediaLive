package it.venrix.medialive.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

/**
 * Snapshot tipizzato di config.yml.
 * <p>
 * Tutti i valori vengono letti una sola volta al caricamento e ricalcolati
 * soltanto su {@code /live reload}: nessuna lettura del file a runtime.
 */
public final class PluginConfig {

    private final JavaPlugin plugin;

    // --- database ---
    private String dbHost;
    private int dbPort;
    private String dbName;
    private String dbUser;
    private String dbPassword;
    private int poolSize;
    private long connectionTimeout;

    // --- live ---
    private String symbolRaw;
    private String symbolLegacy;
    private List<String> allowedDomains;
    private long cooldownSeconds;

    // --- tab ---
    private boolean tabEnabled;
    private TabPosition tabPosition;
    private String tabSeparator;

    // --- resource pack ---
    private boolean packEnabled;
    private String packUrl;
    private String packSha1;
    private boolean packRequired;
    private String packPrompt;

    public PluginConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        load();
    }

    /** Rilegge config.yml dal disco e ricalcola tutti i valori derivati. */
    public void load() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        ConfigurationSection db = section(config, "database");
        this.dbHost = db.getString("host", "127.0.0.1");
        this.dbPort = db.getInt("port", 3306);
        this.dbName = db.getString("name", "venrixmc");
        this.dbUser = db.getString("user", "root");
        this.dbPassword = db.getString("password", "");
        this.poolSize = Math.max(1, db.getInt("pool-size", 10));
        this.connectionTimeout = Math.max(1000L, db.getLong("connection-timeout", 10_000L));

        ConfigurationSection live = section(config, "live");
        // Il simbolo viene messo in cache in due forme: con & (per TAB e per i messaggi)
        // e con il carattere di sezione (per i placeholder, che restituiscono stringhe legacy).
        this.symbolRaw = live.getString("symbol",
                "&f<sprite:\"minecraft:gui\":\"minecraft:icon/trial_available\">");
        this.symbolLegacy = Text.sectionize(this.symbolRaw);
        this.cooldownSeconds = Math.max(0L, live.getLong("cooldown-seconds", 600L));
        this.allowedDomains = live.getStringList("allowed-domains").stream()
                .filter(domain -> domain != null && !domain.isBlank())
                .map(domain -> domain.trim().toLowerCase(Locale.ROOT))
                .toList();

        ConfigurationSection tab = section(config, "tab");
        this.tabEnabled = tab.getBoolean("enabled", true);
        this.tabPosition = TabPosition.parse(tab.getString("position", "SUFFIX"), TabPosition.SUFFIX);
        this.tabSeparator = tab.getString("separator", " ");

        ConfigurationSection pack = section(config, "resourcepack");
        this.packEnabled = pack.getBoolean("enabled", false);
        this.packUrl = pack.getString("url", "");
        this.packSha1 = pack.getString("sha1", "");
        this.packRequired = pack.getBoolean("required", false);
        this.packPrompt = pack.getString("prompt", "");
    }

    /** Ritorna la sezione richiesta, creandola vuota se mancante per evitare NPE. */
    private ConfigurationSection section(FileConfiguration config, String path) {
        ConfigurationSection found = config.getConfigurationSection(path);
        return found != null ? found : config.createSection(path);
    }

    public String getJdbcUrl() {
        return "jdbc:mariadb://" + dbHost + ":" + dbPort + "/" + dbName
                + "?useUnicode=true&characterEncoding=utf8";
    }

    public String getDbUser() {
        return dbUser;
    }

    public String getDbPassword() {
        return dbPassword;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public long getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * Simbolo con i codici {@code &} e gli eventuali tag {@code <sprite:...>} originali:
     * da passare a TAB (che li interpreta da solo) e ai messaggi del plugin.
     */
    public String getSymbolRaw() {
        return symbolRaw;
    }

    /**
     * Simbolo gia' colorato con il carattere di sezione: da restituire nei placeholder.
     * Un eventuale tag {@code <sprite:...>} resta testuale, vedi {@link Text#sectionize(String)}.
     */
    public String getSymbolLegacy() {
        return symbolLegacy;
    }

    public List<String> getAllowedDomains() {
        return allowedDomains;
    }

    public long getCooldownSeconds() {
        return cooldownSeconds;
    }

    public boolean isTabEnabled() {
        return tabEnabled;
    }

    public TabPosition getTabPosition() {
        return tabPosition;
    }

    public String getTabSeparator() {
        return tabSeparator;
    }

    public boolean isPackEnabled() {
        return packEnabled;
    }

    public String getPackUrl() {
        return packUrl;
    }

    public String getPackSha1() {
        return packSha1;
    }

    public boolean isPackRequired() {
        return packRequired;
    }

    public String getPackPrompt() {
        return packPrompt;
    }
}
