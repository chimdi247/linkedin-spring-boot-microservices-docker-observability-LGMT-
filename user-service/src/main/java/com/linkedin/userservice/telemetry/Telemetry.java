package com.linkedin.userservice.telemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

import java.lang.management.ManagementFactory;

/**
 * Manual OpenTelemetry instrumentation (API only) - business KPIs for the user domain.
 * HTTP, JDBC, Kafka, logs and JVM signals are produced by the OpenTelemetry Java agent.
 * Prometheus names after the collector -> Mimir hop:
 * users_registered_total, auth_logins_total{outcome}, connections_requested_total,
 * connections_accepted_total, profile_updates_total, app_uptime_seconds
 */
public final class Telemetry {

    private static final Meter METER = GlobalOpenTelemetry.getMeter("com.linkedin.userservice");
    private static final AttributeKey<String> OUTCOME = AttributeKey.stringKey("outcome");

    private static final LongCounter USERS_REGISTERED = METER.counterBuilder("users.registered")
            .setDescription("New accounts created").setUnit("1").build();
    private static final LongCounter LOGINS = METER.counterBuilder("auth.logins")
            .setDescription("Login attempts by outcome").setUnit("1").build();
    private static final LongCounter CONNECTIONS_REQUESTED = METER.counterBuilder("connections.requested")
            .setDescription("Connection requests sent").setUnit("1").build();
    private static final LongCounter CONNECTIONS_ACCEPTED = METER.counterBuilder("connections.accepted")
            .setDescription("Connection requests accepted").setUnit("1").build();
    private static final LongCounter PROFILE_UPDATES = METER.counterBuilder("profile.updates")
            .setDescription("Profile edits").setUnit("1").build();

    static {
        METER.gaugeBuilder("app.uptime").setUnit("s").setDescription("Seconds since the JVM started")
                .buildWithCallback(m -> m.record(ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0));
    }

    private Telemetry() {
    }

    public static void userRegistered() {
        USERS_REGISTERED.add(1);
    }

    public static void login(boolean success) {
        LOGINS.add(1, Attributes.of(OUTCOME, success ? "success" : "failure"));
    }

    public static void connectionRequested() {
        CONNECTIONS_REQUESTED.add(1);
    }

    public static void connectionAccepted() {
        CONNECTIONS_ACCEPTED.add(1);
    }

    public static void profileUpdated() {
        PROFILE_UPDATES.add(1);
    }
}
