package it.venrix.medialive.config;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Gestione di messages.yml.
 * <p>
 * Ogni messaggio supporta i token {@code {prefix}} e {@code {live_symbol}}, piu'
 * eventuali coppie chiave/valore passate a runtime. La conversione in
 * {@link Component} avviene tramite {@link Text#LEGACY}.
 */
public final class MessageService {

    private static final String FILE_NAME = "messages.yml";

    private final JavaPlugin plugin;
    private final PluginConfig config;

    private FileConfiguration messages;
    private String prefix;

    public MessageService(JavaPlugin plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
        load();
    }

    /** Rilegge messages.yml dal disco (creandolo dal jar se assente). */
    public void load() {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            plugin.saveResource(FILE_NAME, false);
        }

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);

        // I default del jar fanno da fallback: se un admin cancella una chiave
        // il plugin continua a funzionare invece di stampare "messaggio mancante".
        try (InputStream stream = plugin.getResource(FILE_NAME)) {
            if (stream != null) {
                loaded.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)));
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Impossibile caricare i default di " + FILE_NAME + ": " + ex.getMessage());
        }

        this.messages = loaded;
        this.prefix = loaded.getString("prefix", "");
    }

    /**
     * Ritorna il messaggio grezzo con tutti i token risolti, ma senza conversione colori.
     *
     * @param path         percorso nel file, es. {@code live.enabled}
     * @param replacements coppie token/valore, es. {@code "{player}", "Notch"}
     */
    public String raw(String path, String... replacements) {
        String value = messages.getString(path);
        if (value == null) {
            return "<missing: " + path + ">";
        }
        value = value.replace("{prefix}", prefix)
                .replace("{live_symbol}", config.getSymbolRaw());
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            value = value.replace(replacements[i], replacements[i + 1]);
        }
        return value;
    }

    /** Come {@link #raw(String, String...)} ma gia' convertito in {@link Component}. */
    public Component get(String path, String... replacements) {
        return Text.component(raw(path, replacements));
    }

    /**
     * Versione legacy con carattere di sezione: serve per le API che accettano
     * solo stringhe (ad esempio i messaggi interni di ACF).
     */
    public String legacy(String path, String... replacements) {
        return Text.sectionize(raw(path, replacements));
    }

    /** Invia il messaggio, salvo che sia vuoto (un messaggio vuoto equivale a "disattivato"). */
    public void send(Audience audience, String path, String... replacements) {
        String value = raw(path, replacements);
        if (value.isBlank()) {
            return;
        }
        audience.sendMessage(Text.component(value));
    }
}
