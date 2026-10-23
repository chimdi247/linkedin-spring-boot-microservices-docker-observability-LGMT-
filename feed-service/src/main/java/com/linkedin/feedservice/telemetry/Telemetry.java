package com.linkedin.feedservice.telemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

import java.lang.management.ManagementFactory;

/**
 * Manual OpenTelemetry instrumentation (API only) - feed KPIs.
 * Prometheus names: feed_fanout_recipients (histogram), feed_events_total{outcome},
 * feed_reads_total{result}, app_uptime_seconds
 */
public final class Telemetry {

    private static final Meter METER = GlobalOpenTelemetry.getMeter("com.linkedin.feedservice");
    private static final AttributeKey<String> OUTCOME = AttributeKey.stringKey("outcome");
    private static final AttributeKey<String> RESULT = AttributeKey.stringKey("result");

    /** How many followers each new post is pushed to (fan-out width). */
    private static final DoubleHistogram FANOUT = METER.histogramBuilder("feed.fanout.recipients")
            .setDescription("Feeds updated per post.created event").setUnit("1")
            .build();
    private static final LongCounter EVENTS = METER.counterBuilder("feed.events")
            .setDescription("post.created events processed").setUnit("1").build();
    private static final LongCounter READS = METER.counterBuilder("feed.reads")
            .setDescription("Feed reads by cache result (hit = non-empty feed)").setUnit("1").build();

    static {
        METER.gaugeBuilder("app.uptime").setUnit("s").setDescription("Seconds since the JVM started")
                .buildWithCallback(m -> m.record(ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0));
    }

    private Telemetry() {
    }

    public static void fanout(int recipients) {
        FANOUT.record(recipients);
    }

    public static void eventProcessed(boolean ok) {
        EVENTS.add(1, Attributes.of(OUTCOME, ok ? "success" : "error"));
    }

    public static void feedRead(boolean hit) {
        READS.add(1, Attributes.of(RESULT, hit ? "hit" : "miss"));
    }
}
