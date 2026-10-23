package com.linkedin.postservice.telemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

import java.lang.management.ManagementFactory;

/**
 * Manual OpenTelemetry instrumentation (API only) - business KPIs for the post domain.
 * Prometheus names: posts_created_total{has_image}, posts_deleted_total, post_likes_total{action},
 * comments_added_total, app_uptime_seconds
 */
public final class Telemetry {

    private static final Meter METER = GlobalOpenTelemetry.getMeter("com.linkedin.postservice");
    private static final AttributeKey<String> HAS_IMAGE = AttributeKey.stringKey("has_image");
    private static final AttributeKey<String> ACTION = AttributeKey.stringKey("action");

    private static final LongCounter POSTS_CREATED = METER.counterBuilder("posts.created")
            .setDescription("Posts created").setUnit("1").build();
    private static final LongCounter POSTS_DELETED = METER.counterBuilder("posts.deleted")
            .setDescription("Posts deleted").setUnit("1").build();
    private static final LongCounter POST_LIKES = METER.counterBuilder("post.likes")
            .setDescription("Like / unlike actions").setUnit("1").build();
    private static final LongCounter COMMENTS_ADDED = METER.counterBuilder("comments.added")
            .setDescription("Comments added").setUnit("1").build();

    static {
        METER.gaugeBuilder("app.uptime").setUnit("s").setDescription("Seconds since the JVM started")
                .buildWithCallback(m -> m.record(ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0));
    }

    private Telemetry() {
    }

    public static void postCreated(boolean hasImage) {
        POSTS_CREATED.add(1, Attributes.of(HAS_IMAGE, String.valueOf(hasImage)));
    }

    public static void postDeleted() {
        POSTS_DELETED.add(1);
    }

    public static void postLike(boolean liked) {
        POST_LIKES.add(1, Attributes.of(ACTION, liked ? "like" : "unlike"));
    }

    public static void commentAdded() {
        COMMENTS_ADDED.add(1);
    }
}
