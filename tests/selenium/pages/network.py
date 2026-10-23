from .base import BasePage


class NetworkPage(BasePage):
    path = "/network"

    def invitations(self):
        return self.els("invitation-card")

    def accept_first(self):
        self.click("invitation-accept")

    def connections(self):
        return self.els("connection-card")
