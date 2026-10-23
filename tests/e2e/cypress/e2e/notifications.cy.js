describe('notifications', () => {
  it('shows the welcome notification, a badge, and marks everything read', () => {
    cy.apiRegister({ firstName: 'Nora' }).then((me) => {
      cy.pollApi(me, '/notifications', (r) => r.length > 0); // welcome event consumed
      cy.loginAs(me);
      cy.get('[data-testid=nav-notifications-badge]').should('be.visible');
      cy.get('[data-testid=nav-notifications]').click();
      cy.location('pathname').should('eq', '/notifications');
      cy.get('[data-testid=notification-item]').should('have.length.at.least', 1).first().should('contain.text', 'Welcome');
      cy.get('[data-testid=notifications-mark-all-read]').click();
      cy.get('[data-testid=nav-notifications-badge]').should('not.exist');
      cy.apiAuth(me, 'GET', '/notifications/unread-count').its('body.count').should('eq', 0);
    });
  });

  it('a connection request creates a notification for the receiver', () => {
    cy.apiRegister({ firstName: 'Asker' }).then((a) => {
      cy.apiRegister({ firstName: 'Asked' }).then((b) => {
        cy.apiAuth(a, 'POST', `/users/${b.userId}/connect`);
        cy.pollApi(b, '/notifications', (r) => r.some((n) => n.type === 'CONNECTION_REQUEST'));
        cy.loginAs(b);
        cy.visit('/notifications');
        cy.get('[data-testid=notification-item]').should('have.length.at.least', 2);
      });
    });
  });

  it('shows the empty state helper when there is nothing (mocked API)', () => {
    cy.apiRegister().then((me) => {
      cy.loginAs(me);
      cy.intercept('GET', '/api/v1/notifications', { statusCode: 200, body: [] });
      cy.visit('/notifications');
      cy.get('[data-testid=notifications-empty]').should('be.visible');
    });
  });
});
