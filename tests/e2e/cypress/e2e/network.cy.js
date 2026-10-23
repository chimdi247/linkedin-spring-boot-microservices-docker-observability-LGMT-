describe('network: invitations, connections, suggestions', () => {
  it('accepts an invitation and shows the connection', () => {
    cy.apiRegister({ firstName: 'Sender' }).then((sender) => {
      cy.apiRegister({ firstName: 'Receiver' }).then((receiver) => {
        cy.apiAuth(sender, 'POST', `/users/${receiver.userId}/connect`).its('status').should('eq', 200);
        cy.loginAs(receiver);
        cy.get('[data-testid=nav-network]').click();
        cy.location('pathname').should('eq', '/network');
        cy.get('[data-testid=invitation-card]').should('have.length', 1).and('contain.text', 'Sender');
        cy.get('[data-testid=invitation-accept]').click();
        cy.get('[data-testid=invitation-card]').should('not.exist');
        cy.get('[data-testid=connection-card]').should('have.length', 1).and('contain.text', 'Sender');
      });
    });
  });

  it('rejects an invitation', () => {
    cy.apiRegister({ firstName: 'Spammer' }).then((sender) => {
      cy.apiRegister({ firstName: 'Picky' }).then((receiver) => {
        cy.apiAuth(sender, 'POST', `/users/${receiver.userId}/connect`);
        cy.loginAs(receiver);
        cy.get('[data-testid=nav-network]').click();
        cy.get('[data-testid=invitation-reject]').click();
        cy.get('[data-testid=invitation-card]').should('not.exist');
        cy.get('[data-testid=connection-card]').should('not.exist');
      });
    });
  });

  it('sends a connection request from the suggestions list', () => {
    cy.apiRegister({ firstName: 'Lonely' }).then((me) => {
      cy.loginAs(me);
      cy.get('[data-testid=nav-network]').click();
      cy.get('[data-testid=suggestion-card]').should('have.length.at.least', 1);
      cy.get('[data-testid=suggestion-connect]').first().click();
      cy.get('[data-testid=toast]').should('be.visible');
      cy.apiAuth(me, 'GET', '/users/suggestions?limit=50').its('body').should('have.length.at.least', 0);
    });
  });
});
