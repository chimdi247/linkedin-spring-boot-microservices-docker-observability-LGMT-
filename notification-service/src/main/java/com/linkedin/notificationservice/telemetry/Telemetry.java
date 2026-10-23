package com.linkedin.notificationservice.telemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

import java.lang.management.ManagementFactory;

/**
 * Manual OpenTelemetry instrumentation (API only) - notification KPIs.
 * Prometheus names: notifications_created_total{type,outcome}, app_uptime_seconds.
 */
public final class Telemetry {

    private static final Meter METER = GlobalOpenTelemetry.getMeter("com.linkedin.notificationservice");
    private static final AttributeKey<String> TYPE = AttributeKey.stringKey("type");
    private static final AttributeKey<String> OUTCOME = AttributeKey.stringKey("outcome");

    private static final LongCounter CREATED = METER.counterBuilder("notifications.created")
            .setDescription("Notifications produced from Kafka events").setUnit("1").build();

    static {
        METER.gaugeBuilder("app.uptime").setUnit("s").setDescription("Seconds since the JVM started")
                .buildWithCallback(m -> m.record(ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0));
    }

    private Telemetry() {
    }

    public static void created(String type, boolean ok) {
        CREATED.add(1, Attributes.of(TYPE, type, OUTCOME, ok ? "success" : "failure"));
    }
}
