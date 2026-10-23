describe('search (Elasticsearch, indexed asynchronously via Kafka)', () => {
  const unique = `Qwx${Date.now().toString(36)}`;
  let me, other;

  before(() => {
    cy.apiRegister({ firstName: 'Searcher' }).then((u) => (me = u));
    cy.apiRegister({ firstName: unique, lastName: 'Findable' }).then((u) => {
      other = u;
      cy.apiAuth(u, 'PUT', `/users/${u.userId}/profile`, { headline: 'x', about: 'y', location: 'Rome', skills: [`sk${unique.toLowerCase()}`] });
      cy.request({ method: 'POST', url: `${Cypress.env('apiUrl')}/api/v1/posts`, form: true, body: { content: `searchable ${unique} post` },
        headers: { Authorization: `Bearer ${u.accessToken}` } });
    });
    // wait until indexing finished instead of sleeping blindly
    cy.then(() => cy.pollApi(me, `/search/people?q=${unique}`, (r) => r.length > 0, 60000));
    cy.then(() => cy.pollApi(me, `/search/posts?q=${unique}`, (r) => r.length > 0, 60000));
  });

  beforeEach(() => cy.loginAs(me));

  it('finds people from the navbar search', () => {
    cy.get('[data-testid=nav-search-input]').type(`${unique}{enter}`);
    cy.location('pathname').should('eq', '/search');
    cy.get('[data-testid=search-result-person]').should('contain.text', unique);
  });

  it('finds posts on the posts tab', () => {
    cy.visit(`/search?q=${unique}&type=posts`);
    cy.get('[data-testid=search-tab-posts]').should('exist');
    cy.get('[data-testid=search-result-post]').should('contain.text', unique);
  });

  it('finds people by exact skill on the skills tab', () => {
    cy.visit(`/search?q=sk${unique.toLowerCase()}&type=skills`);
    cy.get('[data-testid=search-result-person]').should('contain.text', unique);
  });

  it('shows the empty state when nothing matches', () => {
    cy.visit('/search?q=zzzzqqqqnomatch&type=people');
    cy.get('[data-testid=search-empty]').should('be.visible');
  });

  it('can switch tabs', () => {
    cy.visit(`/search?q=${unique}&type=people`);
    cy.get('[data-testid=search-tab-posts]').click();
    cy.location('search').should('contain', 'type=posts');
    cy.get('[data-testid=search-tab-people]').click();
    cy.location('search').should('contain', 'type=people');
  });
});
