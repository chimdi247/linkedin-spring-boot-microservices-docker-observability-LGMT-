describe('profile', () => {
  it('edits my profile (headline, about, location, skills)', () => {
    cy.apiRegister({ firstName: 'Edith' }).then((me) => {
      cy.loginAs(me);
      cy.get('[data-testid=nav-me]').click();
      cy.location('pathname').should('eq', `/profile/${me.userId}`);
      cy.get('[data-testid=profile-name]').should('contain.text', 'Edith');
      cy.get('[data-testid=profile-edit-button]').click();
      cy.get('[data-testid=profile-headline-input]').clear().type('Principal Engineer');
      cy.get('[data-testid=profile-about-input]').clear().type('I build distributed systems.');
      cy.get('[data-testid=profile-location-input]').clear().type('Tallinn');
      cy.get('[data-testid=profile-skills-input]').clear().type('Java, Kafka, Grafana');
      cy.get('[data-testid=profile-save]').click();
      cy.get('[data-testid=profile-headline]').should('contain.text', 'Principal Engineer');
      cy.get('[data-testid=profile-about]').should('contain.text', 'distributed systems');
      cy.get('[data-testid=profile-skill]').should('have.length', 3);
      cy.reload();
      cy.get('[data-testid=profile-headline]').should('contain.text', 'Principal Engineer');
    });
  });

  it('uploads a profile photo', () => {
    cy.apiRegister().then((me) => {
      cy.loginAs(me);
      cy.get('[data-testid=nav-me]').click();
      cy.get('[data-testid=profile-photo-input]').selectFile('fixtures/avatar.png', { force: true });
      cy.get('[data-testid=toast]').should('be.visible');
      cy.apiAuth(me, 'GET', `/users/${me.userId}`).its('body.profilePhotoUrl').should('contain', 'avatar');
    });
  });

  it("another user's profile has a connect button and shows the status", () => {
    cy.apiRegister({ firstName: 'Viewer' }).then((viewer) => {
      cy.apiRegister({ firstName: 'Target' }).then((target) => {
        cy.loginAs(viewer);
        cy.visit(`/profile/${target.userId}`);
        cy.get('[data-testid=profile-name]').should('contain.text', 'Target');
        cy.get('[data-testid=profile-edit-button]').should('not.exist');
        cy.get('[data-testid=profile-connect-button]').click();
        cy.get('[data-testid=profile-status]').should('be.visible');
        cy.apiAuth(viewer, 'GET', `/users/${target.userId}/connection-status`).its('body.status').should('eq', 'PENDING_SENT');
      });
    });
  });
});
