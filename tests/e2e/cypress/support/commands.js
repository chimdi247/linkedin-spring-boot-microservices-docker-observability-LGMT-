// Custom commands. Set-up goes through the gateway API (fast, deterministic); the behaviour under test goes through the UI.
const api = () => Cypress.env('apiUrl') + '/api/v1';
let counter = 0;

Cypress.Commands.add('apiRegister', (overrides = {}) => {
  const email = `e2e-${Date.now()}-${counter++}-${Math.floor(Math.random() * 1e6)}@example.com`;
  const body = { firstName: 'Cy', lastName: 'Press', email, password: 'password123', headline: 'E2E tester', location: 'Lisbon', ...overrides };
  return cy.request('POST', `${api()}/auth/register`, body).then((res) => ({ ...res.body, password: body.password, email }));
});

Cypress.Commands.add('apiAuth', (user, method, path, body) =>
  cy.request({ method, url: `${api()}${path}`, body, headers: { Authorization: `Bearer ${user.accessToken}` }, failOnStatusCode: false }));

// Log in through the real login form
Cypress.Commands.add('uiLogin', (email, password) => {
  cy.visit('/login');
  cy.get('[data-testid=login-email]').type(email);
  cy.get('[data-testid=login-password]').type(password, { log: false });
  cy.get('[data-testid=login-submit]').click();
  cy.get('[data-testid=nav-me]').should('be.visible');
});

// Skip the form: log in through the API, hand the session to the SPA via localStorage-agnostic route:
// we simply use the form (the SPA owns its session format, so the test must not guess it).
Cypress.Commands.add('loginAs', (user) => cy.uiLogin(user.email, user.password));

// Retry a GET until the predicate is true (Kafka-driven features are eventually consistent)
Cypress.Commands.add('pollApi', (user, path, predicate, timeoutMs = 30000) => {
  const started = Date.now();
  const attempt = () =>
    cy.apiAuth(user, 'GET', path).then((res) => {
      if (res.status === 200 && predicate(res.body)) return res.body;
      if (Date.now() - started > timeoutMs) throw new Error(`pollApi timeout for ${path}`);
      return cy.wait(1000, { log: false }).then(attempt);
    });
  return attempt();
});
