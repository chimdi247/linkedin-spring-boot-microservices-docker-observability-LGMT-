import time
import uuid

import pytest

from conftest import BASE_URL
from pages.feed import FeedPage
from pages.login import LoginPage, RegisterPage
from pages.network import NetworkPage
from pages.profile import ProfilePage

pytestmark = pytest.mark.ui


def login(driver, user):
    page = LoginPage(driver).open()
    page.login(user["email"], user["password"])
    page.wait_path("/")
    page.el("nav-me")


def test_anonymous_is_redirected_to_login(driver, api):
    driver.get(BASE_URL + "/")
    LoginPage(driver).wait_path("/login")
    assert LoginPage(driver).el("login-form").is_displayed()


def test_admin_login_and_logout(driver, api):
    page = LoginPage(driver).open()
    page.login("admin@example.com", "password123")
    page.wait_path("/")
    FeedPage(driver).logout()
    page.wait_path("/login")


def test_wrong_password_shows_error(driver, api):
    page = LoginPage(driver).open()
    page.login("admin@example.com", "bad-password")
    assert "Invalid email or password" in page.error()


def test_register_through_the_form(driver, api):
    email = f"sel-ui-{uuid.uuid4().hex[:10]}@example.com"
    page = RegisterPage(driver).open()
    page.register("Sel", "Register", email, "password123", "Tester", "Prague")
    page.wait_path("/")
    page.el("nav-me")


def test_create_post_and_like_it(driver, new_user):
    login(driver, new_user)
    feed = FeedPage(driver)
    text = f"selenium post {uuid.uuid4().hex[:6]}"
    feed.create_post(text)
    assert text in feed.first_post_text()
    feed.like_first()
    feed.wait.until(lambda d: feed.first_like_count().strip().endswith("1"))


def test_accept_invitation_shows_connection(driver, api, new_user):
    # a second user sends an invitation through the API
    other = api.post("/auth/register", json={"firstName": "Inviter", "lastName": "X", "email": f"inv-{uuid.uuid4().hex[:8]}@example.com",
                                             "password": "password123"}).json()
    assert api.post(f"/users/{new_user['id']}/connect", headers={"Authorization": f"Bearer {other['accessToken']}"}).status_code == 200
    login(driver, new_user)
    net = NetworkPage(driver).open()
    net.wait.until(lambda d: net.invitations())
    net.accept_first()
    net.wait_gone("invitation-card")
    net.wait.until(lambda d: net.connections())


def test_edit_profile(driver, new_user):
    login(driver, new_user)
    prof = ProfilePage(driver).open_for(new_user["id"])
    prof.edit("Senior Tester", "Writes Selenium tests.", "Graz", "Python, Selenium")
    prof.wait.until(lambda d: "Senior Tester" in prof.text("profile-headline"))
    assert len(prof.els("profile-skill")) == 2


def test_navbar_search_goes_to_search_page(driver, new_user):
    login(driver, new_user)
    p = FeedPage(driver)
    p.type("nav-search-input", "admin\n")
    p.wait_path("/search")
