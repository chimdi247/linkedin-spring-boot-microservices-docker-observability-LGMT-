from .base import BasePage


class LoginPage(BasePage):
    path = "/login"

    def login(self, email, password):
        self.type("login-email", email)
        self.type("login-password", password)
        self.click("login-submit")

    def error(self):
        return self.text("login-error")


class RegisterPage(BasePage):
    path = "/register"

    def register(self, first, last, email, password, headline="", location=""):
        self.type("register-first-name", first)
        self.type("register-last-name", last)
        self.type("register-email", email)
        self.type("register-password", password)
        if headline:
            self.type("register-headline", headline)
        if location:
            self.type("register-location", location)
        self.click("register-submit")
