package it.venrix.medialive.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CatchUnknown;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Single;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import it.venrix.medialive.LiveManager;
import it.venrix.medialive.config.MessageService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Comando {@code /live}.
 * <p>
 * Qui non c'e' logica applicativa: solo parsing degli argomenti e delega a
 * {@link LiveManager}. ACF genera in automatico il tab completion dei sottocomandi
 * e nasconde quelli per cui il sender non ha il permesso (es. {@code reload}).
 */
@CommandAlias("live")
@Description("Gestione della modalita live di VENRIXMC")
public final class LiveCommand extends BaseCommand {

    private final LiveManager liveManager;
    private final MessageService messages;

    public LiveCommand(LiveManager liveManager, MessageService messages) {
        this.liveManager = liveManager;
        this.messages = messages;
    }

    /** {@code /live} senza argomenti: mostra l'elenco dei sottocomandi. */
    @Default
    @CatchUnknown
    public void onDefault(CommandSender sender) {
        messages.send(sender, "general.usage");
    }

    @Subcommand("on")
    @CommandPermission(LiveManager.PERMISSION_USE)
    @Description("Attiva la modalita live e mostra il pallino in tablist")
    public void onEnable(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        liveManager.enable(player);
    }

    @Subcommand("off")
    @CommandPermission(LiveManager.PERMISSION_USE)
    @Description("Disattiva la modalita live e rimuove il pallino dalla tablist")
    public void onDisable(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        liveManager.disable(player);
    }

    @Subcommand("publish")
    @CommandPermission(LiveManager.PERMISSION_USE)
    @Syntax("<link>")
    @CommandCompletion("@nothing")
    @Description("Annuncia la tua live in chat")
    public void onPublish(CommandSender sender, @Optional @Single String link) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        if (link == null || link.isBlank()) {
            messages.send(player, "publish.usage");
            return;
        }
        liveManager.publish(player, link);
    }

    @Subcommand("reload")
    @CommandPermission(LiveManager.PERMISSION_RELOAD)
    @Description("Ricarica config.yml e messages.yml")
    public void onReload(CommandSender sender) {
        liveManager.reload();
        messages.send(sender, "reload.success");
    }

    /** Ritorna il sender come Player, oppure null avvisandolo che serve un giocatore. */
    private Player asPlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        messages.send(sender, "general.players-only");
        return null;
    }
}
