package com.linkedin.apigateway.telemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

import java.lang.management.ManagementFactory;

/**
 * Manual OpenTelemetry instrumentation (API only).
 *
 * Auto-instrumentation (HTTP server/client, Netty, Lettuce, logs, JVM metrics) comes from the
 * OpenTelemetry Java agent. This class adds gateway-specific business/security signals.
 * Without the agent attached, GlobalOpenTelemetry is a no-op, so nothing here can fail.
 */
public final class Telemetry {

    private static final Meter METER = GlobalOpenTelemetry.getMeter("com.linkedin.apigateway");
    private static final AttributeKey<String> REASON = AttributeKey.stringKey("reason");

    /** JWTs rejected at the edge, by reason (missing_header | invalid_token). */
    private static final LongCounter AUTH_REJECTED = METER
            .counterBuilder("gateway.auth.rejected")
            .setDescription("Requests rejected by the JWT filter")
            .setUnit("1")
            .build();

    /** JWTs accepted at the edge. */
    private static final LongCounter AUTH_ACCEPTED = METER
            .counterBuilder("gateway.auth.accepted")
            .setDescription("Requests that passed JWT validation")
            .setUnit("1")
            .build();

    static {
        METER.gaugeBuilder("app.uptime")
                .setUnit("s")
                .setDescription("Seconds since the JVM started")
                .buildWithCallback(m -> m.record(ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0));
    }

    private Telemetry() {
    }

    public static void authRejected(String reason) {
        AUTH_REJECTED.add(1, Attributes.of(REASON, reason));
    }

    public static void authAccepted() {
        AUTH_ACCEPTED.add(1);
    }
}
