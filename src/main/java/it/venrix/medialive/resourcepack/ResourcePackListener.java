package it.venrix.medialive.resourcepack;

import it.venrix.medialive.config.MessageService;
import it.venrix.medialive.config.PluginConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.Plugin;

/**
 * Invio della pack al join e gestione dell'esito.
 * <p>
 * Registrato soltanto quando {@code resourcepack.enabled} e' true e la pack e'
 * configurata correttamente.
 */
public final class ResourcePackListener implements Listener {

    private final Plugin plugin;
    private final PluginConfig config;
    private final MessageService messages;
    private final ResourcePackService packService;

    public ResourcePackListener(Plugin plugin, PluginConfig config, MessageService messages,
                               ResourcePackService packService) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.packService = packService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        packService.send(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStatus(PlayerResourcePackStatusEvent event) {
        // Se la pack e' facoltativa non facciamo nulla: il giocatore puo' rifiutarla.
        if (!config.isPackRequired()) {
            return;
        }

        String messagePath = switch (event.getStatus()) {
            case DECLINED -> "resourcepack.kick-declined";
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> "resourcepack.kick-failed";
            default -> null;
        };
        if (messagePath == null) {
            return;
        }

        Component reason = messages.get(messagePath);
        Player player = event.getPlayer();
        // Il kick deve avvenire sul main thread.
        if (Bukkit.isPrimaryThread()) {
            player.kick(reason);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> player.kick(reason));
        }
    }
}
