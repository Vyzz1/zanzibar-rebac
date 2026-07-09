COMPOSE := docker compose

.PHONY: help infra infra-postgres down test format checkstyle

.DEFAULT_GOAL := help

help:
	@echo Usage: make TARGET
	@echo Targets:
	@echo   help            - Show this help
	@echo   infra           - Start local infrastructure: Postgres, Redis, RabbitMQ
	@echo   infra-postgres  - Start infrastructure including dockerized Postgres
	@echo   down            - Stop and remove local infrastructure
	@echo   test            - Run all tests
	@echo   format          - Auto-format the codebase with Spotless
	@echo   checkstyle      - Run static analysis with Checkstyle

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

checkstyle:
	mvn checkstyle:check
