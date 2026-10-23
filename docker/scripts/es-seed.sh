#!/bin/sh
# One-shot job: index the seeded admin user so it is searchable (users created through the API are
# indexed from Kafka events; the SQL-seeded admin never produced one).
set -e
ES="${ELASTICSEARCH_URL:-http://elasticsearch:9200}"
until curl -fsS "$ES/_cluster/health?wait_for_status=yellow&timeout=5s" >/dev/null; do sleep 3; done
# the users index is created by search-service on startup (Spring Data) - wait for it
for i in $(seq 1 40); do
  curl -fsS "$ES/users" >/dev/null 2>&1 && break; sleep 3
done
curl -fsS -X PUT "$ES/users/_doc/00000000-0000-4000-8000-000000000001?refresh=true" \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Admin","lastName":"User","headline":"Platform administrator","location":"Remote","skills":["administration"],"email":"admin@example.com"}'
echo
