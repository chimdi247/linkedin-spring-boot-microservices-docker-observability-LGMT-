const { defineConfig } = require('cypress');

// CYPRESS_BASE_URL  -> the SPA (default http://localhost:3000)
// CYPRESS_API_URL   -> the gateway used by cy.request() set-up helpers (default http://localhost:8080)
module.exports = defineConfig({
  e2e: {
    baseUrl: process.env.CYPRESS_BASE_URL || 'http://localhost:3000',
    specPattern: 'e2e/**/*.cy.js',
    supportFile: 'support/e2e.js',
    fixturesFolder: 'fixtures',
    video: false,
    screenshotsFolder: 'screenshots',
    viewportWidth: 1366,
    viewportHeight: 850,
    defaultCommandTimeout: 8000,
    retries: { runMode: 1, openMode: 0 },
    env: {
      apiUrl: process.env.CYPRESS_API_URL || 'http://localhost:8080',
      adminEmail: 'admin@example.com',
      adminPassword: 'password123',
    },
  },
});
