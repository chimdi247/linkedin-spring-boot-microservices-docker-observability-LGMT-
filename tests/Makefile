# Test runner. The stack must be up for everything except `unit`:   docker compose up -d --build   (from the repo root)
# Override endpoints with env vars, e.g.  make integration API_URL=http://localhost:8080
API_URL      ?= http://localhost:8080
BASE_URL     ?= http://localhost:3000
GRAFANA_URL  ?= http://localhost:3001
K6_IMAGE     ?= grafana/k6:0.55.0
K6_NET       ?= host
PY           ?= python3
export API_URL BASE_URL GRAFANA_URL

.PHONY: help unit unit-frontend smoke integration observability e2e selenium load stress spike soak breakpoint all clean venv-integration venv-selenium

help:
	@echo "unit | unit-frontend | smoke | integration | observability | e2e | selenium | load | stress | spike | soak | breakpoint | all"

unit:                       ## JUnit 5 + Mockito, per service (needs Maven + JDK 17)
	@for s in api-gateway user-service post-service feed-service search-service notification-service; do \
	  echo "== $$s"; (cd ../$$s && mvn -B -q test) || exit 1; done

unit-frontend:              ## vitest lives in the frontend project
	cd ../frontend && npm test --if-present

smoke:
	bash smoke/smoke.sh

venv-integration:
	cd integration && $(PY) -m venv .venv && .venv/bin/pip install -q -r requirements.txt
integration:                ## API integration + infra checks (needs `make venv-integration` once)
	cd integration && .venv/bin/pytest -m "integration and not observability"
observability:
	cd integration && .venv/bin/pytest -m observability

e2e:                        ## Cypress (npm ci installs the pinned Cypress)
	cd e2e/cypress && npm install --no-audit --no-fund && npx cypress run

venv-selenium:
	cd selenium && $(PY) -m venv .venv && .venv/bin/pip install -q -r requirements.txt
selenium:                   ## needs Chrome/Chromium installed (driver is fetched by Selenium Manager)
	cd selenium && .venv/bin/pytest

K6 = docker run --rm -i --network $(K6_NET) -e API_URL=$(API_URL) -v $(CURDIR)/performance:/scripts -w /scripts $(K6_IMAGE) run
load stress spike soak breakpoint:
	$(K6) $@.js

all: smoke integration observability e2e selenium
clean:
	rm -rf integration/.venv selenium/.venv e2e/cypress/node_modules e2e/cypress/screenshots
