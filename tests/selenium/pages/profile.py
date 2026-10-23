from .base import BasePage


class ProfilePage(BasePage):
    def open_for(self, user_id):
        return self.open(f"/profile/{user_id}")

    def edit(self, headline, about, location, skills):
        self.click("profile-edit-button")
        self.type("profile-headline-input", headline)
        self.type("profile-about-input", about)
        self.type("profile-location-input", location)
        self.type("profile-skills-input", skills)
        self.click("profile-save")
