package it.venrix.medialive;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

/** Validazione dei link passati a {@code /live publish}. */
public final class LinkValidator {

    /** Esito della validazione. */
    public enum Result {
        VALID,
        /** Il link non inizia con https:// oppure non e' un URL ben formato. */
        INVALID,
        /** URL corretto ma dominio fuori dalla whitelist. */
        DOMAIN_NOT_ALLOWED
    }

    private LinkValidator() {
    }

    public static Result validate(String link, List<String> allowedDomains) {
        if (link == null || link.isBlank()) {
            return Result.INVALID;
        }
        if (!link.toLowerCase(Locale.ROOT).startsWith("https://")) {
            return Result.INVALID;
        }

        final URI uri;
        try {
            uri = new URI(link);
        } catch (URISyntaxException ex) {
            return Result.INVALID;
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return Result.INVALID;
        }

        host = host.toLowerCase(Locale.ROOT);
        if (host.startsWith("www.")) {
            host = host.substring(4);
        }

        for (String domain : allowedDomains) {
            // Match esatto oppure sottodominio (es. clips.twitch.tv per twitch.tv)
            if (host.equals(domain) || host.endsWith("." + domain)) {
                return Result.VALID;
            }
        }
        return Result.DOMAIN_NOT_ALLOWED;
    }
}
