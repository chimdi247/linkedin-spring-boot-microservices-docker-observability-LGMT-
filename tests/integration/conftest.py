"""Shared fixtures. Everything is configurable via environment variables:
API_URL (gateway, default http://localhost:8080), GRAFANA_URL, MIMIR_URL, LOKI_URL, TEMPO_URL,
KAFKA_BOOTSTRAP (default localhost:9092), MYSQL_HOST/MYSQL_PORT/MYSQL_ROOT_PASSWORD, PROXYSQL_HOST/PROXYSQL_PORT.
"""
import os
import time
import uuid
from dataclasses import dataclass, field

import httpx
import pytest

API_URL = os.getenv("API_URL", "http://localhost:8080").rstrip("/")
GRAFANA_URL = os.getenv("GRAFANA_URL", "http://localhost:3001").rstrip("/")
GRAFANA_AUTH = (os.getenv("GRAFANA_USER", "admin"), os.getenv("GRAFANA_PASSWORD", "admin"))
ADMIN_EMAIL = "admin@example.com"
ADMIN_PASSWORD = "password123"
ADMIN_ID = "00000000-0000-4000-8000-000000000001"
V1 = f"{API_URL}/api/v1"

# 1x1 transparent PNG
PNG = bytes.fromhex(
    "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c489"
    "0000000d49444154789c63000100000500010d0a2db40000000049454e44ae426082"
)


@dataclass
class User:
    email: str
    password: str
    user_id: str
    access_token: str
    refresh_token: str
    first_name: str
    client: httpx.Client = field(repr=False, default=None)

    @property
    def headers(self):
        return {"Authorization": f"Bearer {self.access_token}"}

    def get(self, path, **kw):
        return self.client.get(f"{V1}{path}", headers=self.headers, **kw)

    def post(self, path, **kw):
        return self.client.post(f"{V1}{path}", headers=self.headers, **kw)

    def put(self, path, **kw):
        return self.client.put(f"{V1}{path}", headers=self.headers, **kw)

    def delete(self, path, **kw):
        return self.client.delete(f"{V1}{path}", headers=self.headers, **kw)


def wait_until(fn, timeout=30.0, interval=1.0, message="condition not met"):
    """Poll fn() until it returns a truthy value (Kafka-driven features are eventually consistent)."""
    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        try:
            last = fn()
            if last:
                return last
        except Exception as e:  # noqa: BLE001 - keep polling on transient errors
            last = e
        time.sleep(interval)
    raise AssertionError(f"{message} within {timeout}s (last={last!r})")


@pytest.fixture(scope="session")
def http():
    with httpx.Client(timeout=20.0) as c:
        yield c


@pytest.fixture(scope="session")
def stack_up(http):
    try:
        r = http.post(f"{V1}/auth/login", json={"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD})
    except httpx.HTTPError as e:
        pytest.skip(f"gateway not reachable at {API_URL}: {e}")
    if r.status_code != 200:
        pytest.skip(f"gateway reachable but admin login failed ({r.status_code})")


@pytest.fixture(scope="session")
def make_user(http, stack_up):
    """Factory: registers a brand-new unique user through the public API and returns a User."""
    def _make(first="Test", last="User", headline="QA engineer", location="Berlin"):
        email = f"it-{uuid.uuid4().hex[:12]}@example.com"
        password = "password123"
        r = http.post(f"{V1}/auth/register", json={
            "firstName": first, "lastName": last, "email": email, "password": password,
            "headline": headline, "location": location})
        assert r.status_code == 201, r.text
        b = r.json()
        return User(email, password, b["userId"], b["accessToken"], b["refreshToken"], first, http)
    return _make


@pytest.fixture(scope="session")
def admin(http, stack_up):
    r = http.post(f"{V1}/auth/login", json={"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD})
    assert r.status_code == 200, r.text
    b = r.json()
    return User(ADMIN_EMAIL, ADMIN_PASSWORD, b["userId"], b["accessToken"], b["refreshToken"], "Admin", http)


@pytest.fixture()
def alice(make_user):
    return make_user(first="Alice", last=f"A{uuid.uuid4().hex[:6]}")


@pytest.fixture()
def bob(make_user):
    return make_user(first="Bob", last=f"B{uuid.uuid4().hex[:6]}")


def connect(a: User, b: User):
    """a sends, b accepts -> connected."""
    assert a.post(f"/users/{b.user_id}/connect").status_code == 200
    pend = b.get(f"/users/{b.user_id}/connections/pending").json()
    cid = next(c["id"] for c in pend if c["requesterId"] == a.user_id)
    assert b.put(f"/users/connection/{cid}/accept").status_code == 200
