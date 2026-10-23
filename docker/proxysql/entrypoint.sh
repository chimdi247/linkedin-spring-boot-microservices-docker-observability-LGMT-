#!/bin/sh
set -e
sed -e "s|__PROXYSQL_ADMIN_PASSWORD__|${PROXYSQL_ADMIN_PASSWORD}|g" \
    -e "s|__PROXYSQL_MONITOR_PASSWORD__|${PROXYSQL_MONITOR_PASSWORD}|g" \
    -e "s|__APP_RW_PASSWORD__|${APP_RW_PASSWORD}|g" \
    -e "s|__APP_RO_PASSWORD__|${APP_RO_PASSWORD}|g" \
    /etc/proxysql.cnf.tmpl > /etc/proxysql.cnf
# --initial: always (re)load the config file, so a changed password in .env takes effect
exec proxysql -f --initial -c /etc/proxysql.cnf
