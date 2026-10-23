import uuid

import pytest

from conftest import V1, ADMIN_EMAIL, ADMIN_PASSWORD

pytestmark = pytest.mark.integration


def test_register_returns_tokens_and_201(http, stack_up):
    email = f"reg-{uuid.uuid4().hex[:10]}@example.com"
    r = http.post(f"{V1}/auth/register", json={"firstName": "Reg", "lastName": "Ister", "email": email, "password": "secret1"})
    assert r.status_code == 201
    b = r.json()
    assert b["email"] == email and b["tokenType"] == "Bearer"
    assert b["accessToken"] and b["refreshToken"] and b["userId"]


def test_register_normalizes_email_case_and_duplicate_is_409(http, stack_up):
    base = f"dup-{uuid.uuid4().hex[:10]}@example.com"
    assert http.post(f"{V1}/auth/register", json={"firstName": "A", "lastName": "B", "email": base, "password": "secret1"}).status_code == 201
    r = http.post(f"{V1}/auth/register", json={"firstName": "A", "lastName": "B", "email": base.upper(), "password": "secret1"})
    assert r.status_code == 409
    assert r.json()["message"] == "Email already registered"


@pytest.mark.parametrize("payload,fragment", [
    ({"firstName": "", "lastName": "x", "email": "a@b.co", "password": "secret1"}, "First name"),
    ({"firstName": "x", "lastName": "", "email": "a@b.co", "password": "secret1"}, "Last name"),
    ({"firstName": "x", "lastName": "y", "email": "not-an-email", "password": "secret1"}, "email"),
    ({"firstName": "x", "lastName": "y", "email": "a@b.co", "password": "123"}, "6 characters"),
])
def test_register_validation_400(http, stack_up, payload, fragment):
    r = http.post(f"{V1}/auth/register", json=payload)
    assert r.status_code == 400
    assert fragment.lower() in r.json()["message"].lower()


def test_login_success_for_seeded_admin(http, stack_up):
    r = http.post(f"{V1}/auth/login", json={"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD})
    assert r.status_code == 200
    assert r.json()["userId"] == "00000000-0000-4000-8000-000000000001"


def test_login_is_case_insensitive_on_email(http, stack_up):
    r = http.post(f"{V1}/auth/login", json={"email": "  ADMIN@Example.com ", "password": ADMIN_PASSWORD})
    assert r.status_code == 200


def test_bad_credentials_return_same_401_message(http, stack_up):
    wrong_pw = http.post(f"{V1}/auth/login", json={"email": ADMIN_EMAIL, "password": "nope-nope"})
    unknown = http.post(f"{V1}/auth/login", json={"email": "ghost-user@example.com", "password": "nope-nope"})
    assert wrong_pw.status_code == unknown.status_code == 401
    assert wrong_pw.json()["message"] == unknown.json()["message"] == "Invalid email or password"


def test_login_validation_400(http, stack_up):
    assert http.post(f"{V1}/auth/login", json={"email": "", "password": ""}).status_code == 400
