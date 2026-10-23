import base64
import json
import time

import pytest

from conftest import V1

pytestmark = pytest.mark.integration


def _b64(d: dict) -> str:
    return base64.urlsafe_b64encode(json.dumps(d).encode()).rstrip(b"=").decode()


def test_missing_token_is_401_with_empty_body(http, stack_up):
    r = http.get(f"{V1}/users/suggestions")
    assert r.status_code == 401 and r.content == b""


def test_garbage_and_wrong_scheme_tokens_are_401(http, stack_up):
    assert http.get(f"{V1}/users/suggestions", headers={"Authorization": "Bearer not.a.jwt"}).status_code == 401
    assert http.get(f"{V1}/users/suggestions", headers={"Authorization": "Basic abc"}).status_code == 401


def test_forged_unsigned_and_wrongly_signed_tokens_are_401(http, stack_up):
    header = _b64({"alg": "none", "typ": "JWT"})
    body = _b64({"userId": "00000000-0000-4000-8000-000000000001", "type": "access", "sub": "admin@example.com",
                 "exp": int(time.time()) + 3600})
    assert http.get(f"{V1}/users/suggestions", headers={"Authorization": f"Bearer {header}.{body}."}).status_code == 401
    header = _b64({"alg": "HS256", "typ": "JWT"})
    assert http.get(f"{V1}/users/suggestions", headers={"Authorization": f"Bearer {header}.{body}.AAAA"}).status_code == 401


def test_refresh_token_cannot_be_used_as_access_token(alice, http):
    r = http.get(f"{V1}/users/suggestions", headers={"Authorization": f"Bearer {alice.refresh_token}"})
    assert r.status_code == 401


def test_valid_access_token_is_accepted(alice):
    assert alice.get("/users/suggestions").status_code == 200


def test_public_auth_routes_need_no_token(http, stack_up):
    assert http.post(f"{V1}/auth/login", json={"email": "x@y.zz", "password": "whatever"}).status_code == 401


def test_client_supplied_identity_headers_are_overwritten(alice, bob):
    # Alice tries to act as Bob by sending X-User-Id: the gateway must replace it with the token's user.
    r = alice.client.get(f"{V1}/users/{bob.user_id}/connection-status",
                         headers={**alice.headers, "X-User-Id": bob.user_id, "X-User-Email": bob.email})
    assert r.status_code == 200
    assert r.json()["status"] == "NONE"          # would be "SELF" if the spoofed header had survived


def test_client_cannot_read_someone_elses_feed_via_header_spoof(alice, bob):
    r = alice.client.get(f"{V1}/feed/{bob.user_id}", headers={**alice.headers, "X-User-Id": bob.user_id})
    assert r.status_code == 403
