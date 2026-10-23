#!/bin/bash
# Replica bootstrap: wait for the primary, attach with GTID auto-positioning (it then replays the
# primary's whole history - schema, seed data, users), finally make the replica read-only.
set -euo pipefail
until mysqladmin ping -h mysql-primary -urepl -p"${REPLICATION_PASSWORD}" --silent 2>/dev/null \
   || mysqladmin ping -h mysql-primary -uroot -p"${MYSQL_ROOT_PASSWORD}" --silent 2>/dev/null; do
  echo "[replica-init] waiting for mysql-primary ..."; sleep 3
done
# the repl user is created by the primary's 03-users.sh; wait until it exists
until mysql -h mysql-primary -urepl -p"${REPLICATION_PASSWORD}" -e "SELECT 1" >/dev/null 2>&1; do
  echo "[replica-init] waiting for repl user ..."; sleep 3
done
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CHANGE REPLICATION SOURCE TO
  SOURCE_HOST='mysql-primary', SOURCE_PORT=3306,
  SOURCE_USER='repl', SOURCE_PASSWORD='${REPLICATION_PASSWORD}',
  SOURCE_AUTO_POSITION=1, GET_SOURCE_PUBLIC_KEY=1;
START REPLICA;
SET PERSIST read_only = ON;
SET PERSIST super_read_only = ON;
SQL
echo "[replica-init] replication started, replica is read-only"
