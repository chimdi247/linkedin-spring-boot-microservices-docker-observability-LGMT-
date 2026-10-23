from .base import BasePage


class FeedPage(BasePage):
    path = "/"

    def create_post(self, text):
        self.type("composer-input", text)
        self.click("composer-submit")

    def first_post(self):
        self.wait.until(lambda d: self.els("post-card"))
        return self.els("post-card")[0]

    def first_post_text(self):
        return self.first_post().find_element(*self.tid("post-content")).text

    def like_first(self):
        self.first_post().find_element(*self.tid("post-like-button")).click()

    def first_like_count(self):
        return self.first_post().find_element(*self.tid("post-like-count")).text

    def logout(self):
        self.click("nav-logout")
