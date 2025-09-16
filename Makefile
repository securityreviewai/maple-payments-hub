.PHONY: help up down clean build test seed docs lint format

# Default target
help:
	@echo "Maple Payments Hub - Development Commands"
	@echo ""
	@echo "Usage: make <target>"
	@echo ""
	@echo "Targets:"
	@echo "  up           - Start all services with Docker Compose"
	@echo "  down         - Stop all services"
	@echo "  clean        - Stop services and remove volumes"
	@echo "  build        - Build the application"
	@echo "  test         - Run all tests"
	@echo "  integration  - Run integration tests"
	@echo "  seed         - Run database seeding"
	@echo "  docs         - Generate API documentation"
	@echo "  lint         - Run code linting"
	@echo "  format       - Format code"
	@echo "  logs         - Show application logs"
	@echo "  shell        - Open shell in running container"

# Docker Compose operations
up:
	@echo "Starting Maple Payments Hub services..."
	docker-compose up -d postgres kafka zookeeper redis sftp-server
	@echo "Waiting for services to be ready..."
	sleep 30
	@echo "Building and starting application..."
	docker-compose up -d maple-payments-hub
	@echo "Services started. Application will be available at http://localhost:8082"
	@echo "Kafka UI: http://localhost:8081"
	@echo "SFTP Viewer: http://localhost:8080"

down:
	@echo "Stopping all services..."
	docker-compose down

clean:
	@echo "Stopping services and cleaning up..."
	docker-compose down -v
	docker system prune -f

# Application operations
build:
	@echo "Building application..."
	./gradlew clean build -x test

test:
	@echo "Running all tests..."
	./gradlew test

integration:
	@echo "Running integration tests..."
	./gradlew integrationTest

seed:
	@echo "Seeding database with demo data..."
	./gradlew runSeed

# Development tools
docs:
	@echo "Generating API documentation..."
	./gradlew generateOpenApiDocs
	@echo "API docs available at http://localhost:8082/api/docs"

lint:
	@echo "Running code linting..."
	./gradlew spotlessCheck

format:
	@echo "Formatting code..."
	./gradlew spotlessApply

# Debugging and monitoring
logs:
	docker-compose logs -f maple-payments-hub

logs-all:
	docker-compose logs -f

shell:
	docker-compose exec maple-payments-hub /bin/bash

db-shell:
	docker-compose exec postgres psql -U maple_user -d maple_payments

kafka-topics:
	docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list

# Development setup
dev-setup: build up seed
	@echo "Development environment setup complete!"
	@echo "Application: http://localhost:8082"
	@echo "API Docs: http://localhost:8082/api/docs"
	@echo "Health: http://localhost:8082/actuator/health"

# Production builds
docker-build:
	@echo "Building production Docker image..."
	docker build -t maple-payments-hub:latest .

# Testing specific services
test-db:
	@echo "Testing database connection..."
	docker-compose exec postgres pg_isready -U maple_user -d maple_payments

test-kafka:
	@echo "Testing Kafka connection..."
	docker-compose exec kafka kafka-broker-api-versions --bootstrap-server localhost:9092

# Utility commands
reset-db:
	@echo "Resetting database..."
	docker-compose exec postgres psql -U maple_user -d maple_payments -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
	make seed

backup-db:
	@echo "Creating database backup..."
	mkdir -p ./backups
	docker-compose exec postgres pg_dump -U maple_user maple_payments > ./backups/backup-$(shell date +%Y%m%d-%H%M%S).sql

restore-db:
	@echo "Restoring database from backup..."
	@read -p "Enter backup filename: " backup; \
	docker-compose exec -T postgres psql -U maple_user maple_payments < ./backups/$$backup

# CI/CD helpers
ci-test:
	@echo "Running CI test suite..."
	./gradlew clean test integrationTest spotlessCheck

ci-build:
	@echo "Running CI build..."
	./gradlew clean build -x test
	docker build -t maple-payments-hub:$(shell git rev-parse --short HEAD) .

# Monitoring and health checks
health:
	@echo "Checking service health..."
	@curl -f http://localhost:8082/public/health || echo "Application not responding"
	@curl -f http://localhost:8082/actuator/health || echo "Actuator not responding"

metrics:
	@echo "Fetching metrics..."
	@curl -s http://localhost:8082/actuator/metrics | jq .

prometheus:
	@echo "Fetching Prometheus metrics..."
	@curl -s http://localhost:8082/actuator/prometheus
