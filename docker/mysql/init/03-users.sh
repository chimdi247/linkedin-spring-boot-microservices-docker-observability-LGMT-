#!/bin/bash
# Creates the technical users. Passwords come from the container environment (.env).
# Runs only on the PRIMARY at first start; the statements replicate to the replica via GTID.
set -euo pipefail
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
-- replication
CREATE USER IF NOT EXISTS 'repl'@'%' IDENTIFIED WITH mysql_native_password BY '${REPLICATION_PASSWORD}';
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'repl'@'%';

-- ProxySQL health monitor (read_only / ping / lag checks)
CREATE USER IF NOT EXISTS 'proxysql_monitor'@'%' IDENTIFIED WITH mysql_native_password BY '${PROXYSQL_MONITOR_PASSWORD}';
GRANT USAGE, REPLICATION CLIENT ON *.* TO 'proxysql_monitor'@'%';

-- application: read/write user (ProxySQL default hostgroup 10 = primary)
CREATE USER IF NOT EXISTS 'app_rw'@'%' IDENTIFIED WITH mysql_native_password BY '${APP_RW_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE ON user_db.* TO 'app_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON post_db.* TO 'app_rw'@'%';

-- application: read-only user (ProxySQL default hostgroup 20 = replica)
CREATE USER IF NOT EXISTS 'app_ro'@'%' IDENTIFIED WITH mysql_native_password BY '${APP_RO_PASSWORD}';
GRANT SELECT ON user_db.* TO 'app_ro'@'%';
GRANT SELECT ON post_db.* TO 'app_ro'@'%';

-- Prometheus mysqld_exporter
CREATE USER IF NOT EXISTS 'exporter'@'%' IDENTIFIED WITH mysql_native_password BY '${EXPORTER_PASSWORD}' WITH MAX_USER_CONNECTIONS 5;
GRANT PROCESS, REPLICATION CLIENT, SELECT ON *.* TO 'exporter'@'%';

FLUSH PRIVILEGES;
SQL
