describe('authentication', () => {
  it('redirects anonymous visitors to /login', () => {
    cy.visit('/');
    cy.location('pathname').should('eq', '/login');
    cy.get('[data-testid=login-form]').should('be.visible');
  });

  it('seeded admin can log in and log out', () => {
    cy.uiLogin(Cypress.env('adminEmail'), Cypress.env('adminPassword'));
    cy.location('pathname').should('eq', '/');
    cy.get('[data-testid=nav-logout]').click();
    cy.location('pathname').should('eq', '/login');
    cy.visit('/');
    cy.location('pathname').should('eq', '/login'); // session really gone
  });

  it('shows an error for wrong credentials', () => {
    cy.visit('/login');
    cy.get('[data-testid=login-email]').type('admin@example.com');
    cy.get('[data-testid=login-password]').type('wrong-password');
    cy.get('[data-testid=login-submit]').click();
    cy.get('[data-testid=login-error]').should('be.visible').and('contain.text', 'Invalid email or password');
    cy.location('pathname').should('eq', '/login');
  });

  it('registers a new account through the form and lands on the feed', () => {
    const email = `ui-reg-${Date.now()}@example.com`;
    cy.visit('/login');
    cy.get('[data-testid=go-register]').click();
    cy.location('pathname').should('eq', '/register');
    cy.get('[data-testid=register-first-name]').type('Rita');
    cy.get('[data-testid=register-last-name]').type('Register');
    cy.get('[data-testid=register-email]').type(email);
    cy.get('[data-testid=register-password]').type('password123');
    cy.get('[data-testid=register-headline]').type('Tester');
    cy.get('[data-testid=register-location]').type('Oslo');
    cy.get('[data-testid=register-submit]').click();
    cy.location('pathname').should('eq', '/');
    cy.get('[data-testid=nav-me]').should('be.visible');
  });

  it('rejects a duplicate e-mail on the register form', () => {
    cy.apiRegister().then((u) => {
      cy.visit('/register');
      cy.get('[data-testid=register-first-name]').type('Dup');
      cy.get('[data-testid=register-last-name]').type('Licate');
      cy.get('[data-testid=register-email]').type(u.email);
      cy.get('[data-testid=register-password]').type('password123');
      cy.get('[data-testid=register-submit]').click();
      cy.get('[data-testid=register-error]').should('contain.text', 'already registered');
    });
  });

  it('validates the register form (short password)', () => {
    cy.visit('/register');
    cy.get('[data-testid=register-first-name]').type('Sho');
    cy.get('[data-testid=register-last-name]').type('Rt');
    cy.get('[data-testid=register-email]').type(`short-${Date.now()}@example.com`);
    cy.get('[data-testid=register-password]').type('123');
    cy.get('[data-testid=register-submit]').click();
    cy.location('pathname').should('eq', '/register');
  });

  it('an expired/invalid session (401 from the API) sends the user back to /login', () => {
    cy.apiRegister().then((u) => {
      cy.loginAs(u);
      // every API call now answers 401 like the gateway does for a bad token
      cy.intercept('GET', '/api/v1/**', { statusCode: 401, body: '' }).as('unauth');
      cy.get('[data-testid=nav-network]').click();
      cy.location('pathname', { timeout: 10000 }).should('eq', '/login');
    });
  });
});
