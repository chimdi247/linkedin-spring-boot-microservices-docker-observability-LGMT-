describe('feed: posts, likes, comments', () => {
  let me;
  beforeEach(() => {
    cy.apiRegister({ firstName: 'Poster' }).then((u) => { me = u; cy.loginAs(u); });
  });

  it('shows the empty state for a brand-new user', () => {
    cy.get('[data-testid=feed-empty]').should('be.visible');
  });

  it('creates a text post that appears immediately', () => {
    const text = `hello e2e ${Date.now()}`;
    cy.get('[data-testid=composer-input]').type(text);
    cy.get('[data-testid=composer-submit]').click();
    cy.get('[data-testid=post-card]').first().find('[data-testid=post-content]').should('contain.text', text);
    cy.get('[data-testid=feed-empty]').should('not.exist');
  });

  it('creates a post with an image', () => {
    cy.get('[data-testid=composer-input]').type('with picture');
    cy.get('[data-testid=composer-image-input]').selectFile('fixtures/avatar.png', { force: true });
    cy.get('[data-testid=composer-submit]').click();
    cy.get('[data-testid=post-card]').first().find('img').should('exist');
  });

  it('likes and un-likes a post (count + optimistic UI)', () => {
    cy.get('[data-testid=composer-input]').type('like me');
    cy.get('[data-testid=composer-submit]').click();
    const card = () => cy.get('[data-testid=post-card]').first();
    card().find('[data-testid=post-like-count]').should('contain.text', '0');
    card().find('[data-testid=post-like-button]').click();
    card().find('[data-testid=post-like-count]').should('contain.text', '1');
    card().find('[data-testid=post-like-button]').click();
    card().find('[data-testid=post-like-count]').should('contain.text', '0');
  });

  it('adds a comment', () => {
    cy.get('[data-testid=composer-input]').type('comment me');
    cy.get('[data-testid=composer-submit]').click();
    cy.get('[data-testid=post-card]').first().within(() => {
      cy.get('[data-testid=post-comment-toggle]').click();
      cy.get('[data-testid=post-comment-input]').type('first!');
      cy.get('[data-testid=post-comment-submit]').click();
      cy.get('[data-testid=comment-item]').should('have.length.at.least', 1).first().should('contain.text', 'first!');
      cy.get('[data-testid=post-comment-count]').should('contain.text', '1');
    });
  });

  it('deletes my own post', () => {
    cy.get('[data-testid=composer-input]').type('delete me');
    cy.get('[data-testid=composer-submit]').click();
    cy.get('[data-testid=post-card]').should('have.length', 1);
    cy.get('[data-testid=post-delete-button]').first().click();
    cy.get('[data-testid=post-card]').should('not.exist');
  });

  it("a connection's post shows up in my feed (Kafka fan-out)", () => {
    cy.apiRegister({ firstName: 'Friend' }).then((friend) => {
      cy.apiAuth(me, 'POST', `/users/${friend.userId}/connect`).then(() => {
        cy.apiAuth(friend, 'GET', `/users/${friend.userId}/connections/pending`).then((res) => {
          cy.apiAuth(friend, 'PUT', `/users/connection/${res.body[0].id}/accept`);
        });
      });
      const text = `from friend ${Date.now()}`;
      cy.request({
        method: 'POST', url: `${Cypress.env('apiUrl')}/api/v1/posts`, form: true, body: { content: text },
        headers: { Authorization: `Bearer ${friend.accessToken}` },
      });
      cy.pollApi(me, `/feed/${me.userId}`, (ids) => ids.length > 0);
      cy.reload();
      cy.get('[data-testid=post-card]', { timeout: 15000 }).should('contain.text', text);
    });
  });
});
