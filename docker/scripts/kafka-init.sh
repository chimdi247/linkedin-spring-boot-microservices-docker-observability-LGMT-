#!/bin/bash
# One-shot job: create the domain-event topics (idempotent).
set -euo pipefail
BS="${KAFKA_BOOTSTRAP_SERVERS:-kafka:29092}"
PARTITIONS="${KAFKA_TOPIC_PARTITIONS:-3}"
for t in user.created user.updated connection.requested connection.accepted post.created post.liked post.commented; do
  kafka-topics --bootstrap-server "$BS" --create --if-not-exists --topic "$t" \
    --partitions "$PARTITIONS" --replication-factor 1
done
kafka-topics --bootstrap-server "$BS" --list
