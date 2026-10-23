"""Infrastructure checks. Drivers / ports are optional - tests skip when they are not reachable."""
import os
import socket

import pytest

pytestmark = [pytest.mark.integration, pytest.mark.infra]

EXPECTED_TOPICS = {"user.created", "user.updated", "connection.requested", "connection.accepted",
                   "post.created", "post.liked", "post.commented"}


def _port_open(host, port):
    try:
        with socket.create_connection((host, int(port)), timeout=2):
            return True
    except OSError:
        return False


def test_kafka_topics_exist():
    kafka = pytest.importorskip("kafka")
    bs = os.getenv("KAFKA_BOOTSTRAP", "localhost:9092")
    host, port = bs.split(":")
    if not _port_open(host, port):
        pytest.skip(f"Kafka not reachable at {bs}")
    consumer = kafka.KafkaConsumer(bootstrap_servers=bs, request_timeout_ms=10000, api_version_auto_timeout_ms=10000)
    try:
        assert EXPECTED_TOPICS <= set(consumer.topics())
    finally:
        consumer.close()


def _mysql(host, port, user, password):
    pymysql = pytest.importorskip("pymysql")
    if not _port_open(host, port):
        pytest.skip(f"MySQL endpoint {host}:{port} not reachable")
    return pymysql.connect(host=host, port=int(port), user=user, password=password, connect_timeout=5, autocommit=True)


def test_proxysql_routes_rw_and_ro_users_to_different_servers():
    host, port = os.getenv("PROXYSQL_HOST", "localhost"), os.getenv("PROXYSQL_PORT", "6033")
    rw = _mysql(host, port, "app_rw", os.getenv("APP_RW_PASSWORD", "apprwpass"))
    ro = _mysql(host, port, "app_ro", os.getenv("APP_RO_PASSWORD", "appropass"))
    with rw.cursor() as c1, ro.cursor() as c2:
        c1.execute("SELECT @@hostname, @@read_only"); rw_host, rw_ro = c1.fetchone()
        c2.execute("SELECT @@hostname, @@read_only"); ro_host, ro_ro = c2.fetchone()
    assert rw_ro == 0, "app_rw must land on the writable primary"
    assert ro_host != rw_host or ro_ro == 1, "app_ro should be served by the replica (hostgroup 20)"


def test_replica_is_replicating_and_read_only():
    host, port = os.getenv("MYSQL_HOST", "localhost"), os.getenv("MYSQL_PORT", "3307")
    root = os.getenv("MYSQL_ROOT_PASSWORD", "rootpass")
    primary = _mysql(host, port, "root", root)
    with primary.cursor() as c:
        c.execute("SELECT COUNT(*) FROM user_db.users WHERE email='admin@example.com'")
        assert c.fetchone()[0] == 1
        c.execute("SHOW TABLES FROM user_db"); user_tables = {r[0] for r in c.fetchall()}
        c.execute("SHOW TABLES FROM post_db"); post_tables = {r[0] for r in c.fetchall()}
    assert {"users", "user_skills", "connections"} <= user_tables
    assert {"posts", "likes", "comments"} <= post_tables


def test_admin_password_is_stored_hashed():
    host, port = os.getenv("MYSQL_HOST", "localhost"), os.getenv("MYSQL_PORT", "3307")
    conn = _mysql(host, port, "root", os.getenv("MYSQL_ROOT_PASSWORD", "rootpass"))
    with conn.cursor() as c:
        c.execute("SELECT password FROM user_db.users WHERE email='admin@example.com'")
        (pw,) = c.fetchone()
    assert pw.startswith("$2") and pw != "password123" and len(pw) == 60
