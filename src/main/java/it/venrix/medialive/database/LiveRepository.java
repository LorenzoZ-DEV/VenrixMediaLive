package it.venrix.medialive.database;

import it.venrix.medialive.cache.LiveData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Accesso alla tabella {@code venrix_media_live}.
 * <p>
 * Ogni metodo pubblico gira sull'executor dedicato passato dal costruttore:
 * nessuna query viene eseguita sul main thread.
 */
public final class LiveRepository {

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS `venrix_media_live` (
              `uuid` CHAR(36) NOT NULL,
              `live` BOOLEAN NOT NULL DEFAULT FALSE,
              `url` VARCHAR(255) NULL,
              `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (`uuid`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

    private static final String UPSERT = """
            INSERT INTO `venrix_media_live` (`uuid`, `live`, `url`) VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE `live` = VALUES(`live`), `url` = VALUES(`url`)
            """;

    private static final String SELECT = "SELECT `live`, `url` FROM `venrix_media_live` WHERE `uuid` = ?";

    private final Database database;
    private final Executor executor;
    private final Logger logger;

    public LiveRepository(Database database, Executor executor, Logger logger) {
        this.database = database;
        this.executor = executor;
        this.logger = logger;
    }

    /** Crea la tabella se non esiste. Invocato una sola volta all'avvio. */
    public void createTable() throws SQLException {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(CREATE_TABLE)) {
            statement.executeUpdate();
        }
    }

    /**
     * Carica lo stato di un giocatore.
     * Se non esiste alcuna riga viene restituito {@link LiveData#empty()}.
     */
    public CompletableFuture<LiveData> load(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(SELECT)) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        return new LiveData(result.getBoolean("live"), result.getString("url"));
                    }
                }
                return LiveData.empty();
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Errore nel caricamento dei dati live di " + uuid, ex);
                return LiveData.empty();
            }
        }, executor);
    }

    /** Salva (o aggiorna) stato live e link di un giocatore. */
    public CompletableFuture<Boolean> save(UUID uuid, boolean live, String url) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(UPSERT)) {
                statement.setString(1, uuid.toString());
                statement.setBoolean(2, live);
                if (url == null) {
                    statement.setNull(3, java.sql.Types.VARCHAR);
                } else {
                    statement.setString(3, url);
                }
                statement.executeUpdate();
                return true;
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Errore nel salvataggio dei dati live di " + uuid, ex);
                return false;
            }
        }, executor);
    }
}
