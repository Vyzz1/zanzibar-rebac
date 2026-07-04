COMPOSE := docker compose

.PHONY: infra infra-postgres down test format

infra:
	$(COMPOSE) --profile main up

infra-postgres:
	$(COMPOSE) --profile main --profile docker-postgres up

down:
	$(COMPOSE) down

test:
	mvn test

format:
	mvn spotless:apply
