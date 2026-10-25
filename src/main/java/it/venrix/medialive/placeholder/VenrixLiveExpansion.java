package it.venrix.medialive.placeholder;

import it.venrix.medialive.LiveManager;
import it.venrix.medialive.config.PluginConfig;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Espansione PlaceholderAPI con identificatore {@code venrixlive}.
 * <p>
 * Placeholder disponibili:
 * <ul>
 *   <li>{@code %venrixlive_status%} - simbolo colorato se in live, altrimenti stringa vuota.
 *       Se il simbolo configurato e' un tag {@code <sprite:...>} il tag viene restituito
 *       cosi' com'e': lo rendono TAB e i plugin che passano per MiniMessage, mentre
 *       quelli solo-legacy mostrerebbero il testo grezzo.</li>
 *   <li>{@code %venrixlive_url%} - link salvato, altrimenti stringa vuota</li>
 *   <li>{@code %venrixlive_active%} - "true" / "false"</li>
 * </ul>
 * Le letture avvengono solo dalla cache in memoria: nessuna query al database.
 * La tablist non usa questi placeholder, e' gestita in automatico dall'hook TAB.
 */
public final class VenrixLiveExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final LiveManager liveManager;
    private final PluginConfig config;

    public VenrixLiveExpansion(JavaPlugin plugin, LiveManager liveManager, PluginConfig config) {
        this.plugin = plugin;
        this.liveManager = liveManager;
        this.config = config;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "venrixlive";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    /**
     * L'espansione resta registrata anche se PlaceholderAPI viene ricaricato:
     * senza questo, un /papi reload la farebbe sparire.
     */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        boolean live = liveManager.isLive(player.getUniqueId());

        return switch (params.toLowerCase(Locale.ROOT)) {
            // Simbolo gia' convertito in codici legacy pronti per la chat
            case "status" -> live ? config.getSymbolLegacy() : "";
            case "url" -> liveManager.getUrl(player.getUniqueId());
            case "active" -> Boolean.toString(live);
            default -> null;
        };
    }
}
