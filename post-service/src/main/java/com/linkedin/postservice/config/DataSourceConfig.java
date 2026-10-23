package com.linkedin.postservice.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Read/write splitting for the ProxySQL tier.
 *
 * <ul>
 *   <li>{@code spring.datasource.*} - read/write pool (ProxySQL user mapped to the primary hostgroup)</li>
 *   <li>{@code app.datasource.read.*} - optional read-only pool (ProxySQL user mapped to the reader
 *       hostgroup: replica + primary)</li>
 * </ul>
 * Transactions marked {@code @Transactional(readOnly = true)} use the read pool, everything else uses
 * the read/write pool. When {@code app.datasource.read.url} is empty (plain local development against a
 * single MySQL) a single pool is used and nothing changes.
 *
 * Replicas are eventually consistent (milliseconds). Anything that must observe a just-committed write
 * (login, read-modify-write) is therefore NOT read-only.
 */
@Configuration
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    @Bean
    @Primary
    public DataSource dataSource(Environment env) {
        HikariDataSource rw = pool(env, "rw",
                env.getRequiredProperty("spring.datasource.url"),
                env.getProperty("spring.datasource.username"),
                env.getProperty("spring.datasource.password"));

        String readUrl = env.getProperty("app.datasource.read.url", "");
        if (readUrl.isBlank()) {
            log.info("app.datasource.read.url not set - using a single connection pool");
            return rw;
        }

        HikariDataSource ro = pool(env, "ro", readUrl,
                env.getProperty("app.datasource.read.username", env.getProperty("spring.datasource.username")),
                env.getProperty("app.datasource.read.password", env.getProperty("spring.datasource.password")));

        ReadWriteRoutingDataSource routing = new ReadWriteRoutingDataSource();
        Map<Object, Object> targets = new HashMap<>();
        targets.put(ReadWriteRoutingDataSource.RW, rw);
        targets.put(ReadWriteRoutingDataSource.RO, ro);
        routing.setTargetDataSources(targets);
        routing.setDefaultTargetDataSource(rw);
        routing.afterPropertiesSet();

        log.info("Read/write splitting enabled: writes -> {}, read-only transactions -> {}", rw.getJdbcUrl(), ro.getJdbcUrl());
        // Lazy proxy: the physical connection is only fetched on the first statement, i.e. AFTER Spring has
        // flagged the transaction read-only, so routing sees the right flag.
        return new ClosableLazyDataSource(routing, rw, ro);
    }

    private static HikariDataSource pool(Environment env, String name, String url, String user, String password) {
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName(name + "-pool");
        cfg.setJdbcUrl(url);
        cfg.setUsername(user);
        cfg.setPassword(password);
        cfg.setDriverClassName(env.getProperty("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver"));
        cfg.setMaximumPoolSize(env.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class, 10));
        cfg.setMinimumIdle(env.getProperty("spring.datasource.hikari.minimum-idle", Integer.class, 2));
        cfg.setConnectionTimeout(env.getProperty("spring.datasource.hikari.connection-timeout", Long.class, 10_000L));
        cfg.setMaxLifetime(env.getProperty("spring.datasource.hikari.max-lifetime", Long.class, 1_500_000L));
        // Do not fail the whole app if the database is still starting; Hikari retries on first use.
        cfg.setInitializationFailTimeout(env.getProperty("spring.datasource.hikari.initialization-fail-timeout", Long.class, 60_000L));
        return new HikariDataSource(cfg);
    }

    /** Chooses the pool from the current transaction's read-only flag. */
    static final class ReadWriteRoutingDataSource extends AbstractRoutingDataSource {
        static final String RW = "rw";
        static final String RO = "ro";

        @Override
        protected Object determineCurrentLookupKey() {
            return TransactionSynchronizationManager.isCurrentTransactionReadOnly() ? RO : RW;
        }
    }

    /** Lazy proxy that also closes both pools on shutdown. */
    static final class ClosableLazyDataSource extends LazyConnectionDataSourceProxy implements AutoCloseable {
        private final HikariDataSource rw;
        private final HikariDataSource ro;

        ClosableLazyDataSource(DataSource routing, HikariDataSource rw, HikariDataSource ro) {
            super(routing);
            this.rw = rw;
            this.ro = ro;
        }

        @Override
        public void close() {
            rw.close();
            ro.close();
        }
    }
}
