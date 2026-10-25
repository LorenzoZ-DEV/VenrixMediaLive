package it.venrix.medialive.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.regex.Pattern;

/**
 * Utility di conversione colori.
 * <p>
 * Formato supportato: codici legacy con {@code &} piu' il formato hex "strano"
 * di Bukkit {@code &x&r&r&g&g&b&b} (e anche {@code &#rrggbb}).
 * <p>
 * In aggiunta ai colori viene riconosciuto il tag {@code <sprite:ATLAS:SPRITE>}
 * (object component, Minecraft 1.21.9+): e' la stessa sintassi usata da TAB e da
 * MiniMessage, cosi' la stessa stringa di config funziona in entrambi i contesti.
 */
public final class Text {

    /** Serializer legacy configurato per accettare sia &#rrggbb sia &x&r&r&g&g&b&b. */
    public static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexCharacter('#')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    /** Il carattere di sezione usato da Minecraft per i colori legacy. */
    private static final char SECTION = '§';

    /** Caratteri validi subito dopo il carattere di escape dei colori. */
    private static final String CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";

    /** Apertura del tag sprite: lunghezza fissa, usata per normalizzare il nome del tag. */
    private static final String SPRITE_PREFIX = "<sprite:";

    /** Riconosce un tag sprite completo, es. {@code <sprite:gui:icon/trial_available>}. */
    private static final Pattern SPRITE_PATTERN = Pattern.compile("<sprite:[^<>]+>", Pattern.CASE_INSENSITIVE);

    /**
     * Parser MiniMessage ridotto al solo tag {@code <sprite>}.
     * <p>
     * Nessun altro tag viene registrato: colori e decorazioni restano di competenza
     * del serializer legacy, quindi un messaggio che contiene {@code <...>} per altri
     * motivi non viene reinterpretato.
     */
    private static final MiniMessage SPRITE_PARSER = MiniMessage.builder()
            .tags(TagResolver.resolver(StandardTags.sprite()))
            .build();

    /**
     * Sostituisce ogni tag sprite con il relativo object component.
     * <p>
     * La sostituzione avviene DOPO il parsing legacy: lo sprite diventa figlio del
     * componente che lo contiene e ne eredita quindi il colore gia' applicato.
     */
    private static final TextReplacementConfig SPRITE_REPLACEMENT = TextReplacementConfig.builder()
            .match(SPRITE_PATTERN)
            .replacement((result, builder) -> sprite(result.group()))
            .build();

    private Text() {
    }

    /** Converte una stringa con codici {@code &} e tag sprite in un {@link Component}. */
    public static Component component(String input) {
        return LEGACY.deserialize(input == null ? "" : input).replaceText(SPRITE_REPLACEMENT);
    }

    /**
     * Converte i codici {@code &} in codici con carattere di sezione, lasciando il resto intatto.
     * Usato dai placeholder, che devono restituire una stringa legacy gia' colorata.
     * <p>
     * Gli eventuali tag sprite restano testuali: una stringa legacy non puo' contenere un
     * object component. Vengono resi solo dai consumatori che li sanno interpretare
     * (TAB e i plugin che usano MiniMessage).
     */
    public static String sectionize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        char[] chars = input.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && CODES.indexOf(chars[i + 1]) > -1) {
                chars[i] = SECTION;
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    /** Versione senza colori, utile per i log. */
    public static String plain(String input) {
        return PlainTextComponentSerializer.plainText().serialize(component(input));
    }

    /**
     * Converte un singolo tag in un object component.
     * Se il tag e' malformato (atlas o sprite non validi) viene lasciato come testo,
     * cosi' un errore di battitura in config non fa saltare l'intero messaggio.
     */
    private static Component sprite(String tag) {
        try {
            return SPRITE_PARSER.deserialize(SPRITE_PREFIX + tag.substring(SPRITE_PREFIX.length()));
        } catch (RuntimeException ex) {
            return Component.text(tag);
        }
    }
}
