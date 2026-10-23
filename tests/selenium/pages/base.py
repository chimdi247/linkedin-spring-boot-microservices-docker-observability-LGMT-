from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait

from conftest import BASE_URL


class BasePage:
    path = "/"

    def __init__(self, driver, timeout=10):
        self.d = driver
        self.wait = WebDriverWait(driver, timeout)

    def open(self, path=None):
        self.d.get(BASE_URL + (path or self.path))
        return self

    @staticmethod
    def tid(name):
        return (By.CSS_SELECTOR, f'[data-testid="{name}"]')

    def el(self, name):
        return self.wait.until(EC.visibility_of_element_located(self.tid(name)))

    def els(self, name):
        return self.d.find_elements(*self.tid(name))

    def click(self, name):
        self.wait.until(EC.element_to_be_clickable(self.tid(name))).click()

    def type(self, name, text, clear=True):
        e = self.el(name)
        if clear:
            e.clear()
        e.send_keys(text)

    def text(self, name):
        return self.el(name).text

    def wait_path(self, path):
        self.wait.until(lambda d: d.execute_script("return location.pathname") == path)

    def wait_gone(self, name):
        self.wait.until(lambda d: not d.find_elements(*self.tid(name)))
