"""Selenium UI tests (headless Chrome/Chromium, driver resolved by Selenium Manager - no webdriver-manager).
Env: BASE_URL (SPA, default http://localhost:3000), API_URL (gateway, default http://localhost:8080),
     HEADLESS=0 to watch the browser, CHROME_BIN to point at a specific Chrome/Chromium."""
import os
import uuid

import httpx
import pytest
from selenium import webdriver
from selenium.webdriver.chrome.options import Options

BASE_URL = os.getenv("BASE_URL", "http://localhost:3000").rstrip("/")
API_URL = os.getenv("API_URL", "http://localhost:8080").rstrip("/")


@pytest.fixture()
def driver():
    opts = Options()
    if os.getenv("HEADLESS", "1") != "0":
        opts.add_argument("--headless=new")
    if os.getenv("CHROME_BIN"):
        opts.binary_location = os.environ["CHROME_BIN"]
    for a in ("--no-sandbox", "--disable-dev-shm-usage", "--window-size=1366,850", "--disable-gpu"):
        opts.add_argument(a)
    d = webdriver.Chrome(options=opts)
    d.implicitly_wait(0)
    yield d
    d.quit()


@pytest.fixture(scope="session")
def api():
    with httpx.Client(base_url=f"{API_URL}/api/v1", timeout=20) as c:
        try:
            c.post("/auth/login", json={"email": "admin@example.com", "password": "password123"}).raise_for_status()
        except Exception as e:  # noqa: BLE001
            pytest.skip(f"stack not reachable at {API_URL}: {e}")
        yield c


@pytest.fixture()
def new_user(api):
    email = f"sel-{uuid.uuid4().hex[:12]}@example.com"
    r = api.post("/auth/register", json={"firstName": "Sel", "lastName": "Enium", "email": email,
                                         "password": "password123", "headline": "UI tester", "location": "Vienna"})
    assert r.status_code == 201, r.text
    b = r.json()
    return {"email": email, "password": "password123", "id": b["userId"], "token": b["accessToken"]}
