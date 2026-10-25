package it.venrix.medialive.resourcepack;

import it.venrix.medialive.config.PluginConfig;
import it.venrix.medialive.config.Text;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.entity.Player;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Invio della resource pack opzionale.
 * <p>
 * La pack NON viene generata dal plugin: e' un file gia' pronto che mappa il carattere
 * U+E001 nel font {@code minecraft:default} sulla texture custom del pallino. Se la
 * abiliti, imposta {@code live.symbol} a {@code ""} in config.yml.
 * <p>
 * Con {@code resourcepack.enabled: false} questa classe non viene nemmeno istanziata.
 */
public final class ResourcePackService {

    /**
     * UUID fisso della pack: deve restare costante tra i riavvii, altrimenti il client
     * considera la pack come nuova e la riscarica ogni volta.
     */
    private static final UUID PACK_ID =
            UUID.nameUUIDFromBytes("VenrixMediaLive:live-badge-pack".getBytes(StandardCharsets.UTF_8));

    private final PluginConfig config;
    private final Logger logger;

    private ResourcePackRequest request;

    public ResourcePackService(PluginConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
        build();
    }

    /** Ricostruisce la richiesta dai valori di config (richiamata anche dal reload). */
    public void build() {
        this.request = null;

        String url = config.getPackUrl();
        String sha1 = config.getPackSha1();

        if (url == null || url.isBlank()) {
            logger.warning("resourcepack.enabled e' true ma resourcepack.url e' vuoto: invio disattivato.");
            return;
        }
        if (sha1 == null || !sha1.matches("(?i)[0-9a-f]{40}")) {
            logger.warning("resourcepack.sha1 non e' uno SHA-1 valido (40 caratteri esadecimali): invio disattivato.");
            return;
        }

        try {
            ResourcePackInfo info = ResourcePackInfo.resourcePackInfo()
                    .id(PACK_ID)
                    .uri(URI.create(url))
                    .hash(sha1.toLowerCase(java.util.Locale.ROOT))
                    .build();

            this.request = ResourcePackRequest.resourcePackRequest()
                    .packs(info)
                    .required(config.isPackRequired())
                    .prompt(Text.component(config.getPackPrompt()))
                    // replace = true: sostituisce eventuali pack precedenti dello stesso id
                    .replace(true)
                    .build();
        } catch (IllegalArgumentException ex) {
            logger.warning("resourcepack.url non e' un URL valido: " + ex.getMessage());
        }
    }

    /** {@code true} se la pack e' configurata correttamente e puo' essere inviata. */
    public boolean isUsable() {
        return request != null;
    }

    public void send(Player player) {
        if (request != null) {
            player.sendResourcePacks(request);
        }
    }
}
