package com.linkedin.searchservice.telemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

import java.lang.management.ManagementFactory;

/**
 * Manual OpenTelemetry instrumentation (API only) - search KPIs.
 * Prometheus names: search_requests_total{type}, search_index_operations_total{index,outcome},
 * app_uptime_seconds
 */
public final class Telemetry {

    private static final Meter METER = GlobalOpenTelemetry.getMeter("com.linkedin.searchservice");
    private static final AttributeKey<String> TYPE = AttributeKey.stringKey("type");
    private static final AttributeKey<String> INDEX = AttributeKey.stringKey("index");
    private static final AttributeKey<String> OUTCOME = AttributeKey.stringKey("outcome");

    private static final LongCounter SEARCHES = METER.counterBuilder("search.requests")
            .setDescription("Search requests by type (people | skills | posts)").setUnit("1").build();
    private static final LongCounter INDEX_OPS = METER.counterBuilder("search.index.operations")
            .setDescription("Documents indexed from Kafka events").setUnit("1").build();

    static {
        METER.gaugeBuilder("app.uptime").setUnit("s").setDescription("Seconds since the JVM started")
                .buildWithCallback(m -> m.record(ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0));
    }

    private Telemetry() {
    }

    public static void search(String type) {
        SEARCHES.add(1, Attributes.of(TYPE, type));
    }

    public static void indexed(String index, boolean ok) {
        INDEX_OPS.add(1, Attributes.of(INDEX, index, OUTCOME, ok ? "success" : "error"));
    }
}
