package it.venrix.medialive.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import it.venrix.medialive.config.PluginConfig;

import java.sql.Connection;
import java.sql.SQLException;

/** Wrapper sul pool HikariCP verso MariaDB. */
public final class Database {

    private static final String DRIVER = "org.mariadb.jdbc.Driver";

    private HikariDataSource dataSource;

    /**
     * Crea il pool. Va invocato da un thread asincrono: l'apertura della prima
     * connessione e' un'operazione bloccante.
     */
    public void connect(PluginConfig config) throws SQLException {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("VenrixMediaLive-Pool");
        // driverClassName esplicito: il driver viene fornito dal library loader di Paper
        // e non sarebbe visibile tramite il solo DriverManager.
        hikari.setDriverClassName(DRIVER);
        hikari.setJdbcUrl(config.getJdbcUrl());
        hikari.setUsername(config.getDbUser());
        hikari.setPassword(config.getDbPassword());
        hikari.setMaximumPoolSize(config.getPoolSize());
        hikari.setMinimumIdle(Math.min(2, config.getPoolSize()));
        hikari.setConnectionTimeout(config.getConnectionTimeout());
        hikari.setMaxLifetime(600_000L);
        hikari.setIdleTimeout(300_000L);
        hikari.setLeakDetectionThreshold(15_000L);
        hikari.addDataSourceProperty("cachePrepStmts", "true");
        hikari.addDataSourceProperty("prepStmtCacheSize", "250");
        hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        this.dataSource = new HikariDataSource(hikari);

        // Verifica immediata: se le credenziali sono sbagliate vogliamo saperlo subito.
        try (Connection connection = this.dataSource.getConnection()) {
            if (!connection.isValid(5)) {
                throw new SQLException("La connessione di test non risulta valida.");
            }
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Il pool di connessioni non e' inizializzato.");
        }
        return dataSource.getConnection();
    }

    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        dataSource = null;
    }
}
