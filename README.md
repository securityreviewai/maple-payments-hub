# Maple Payments Hub

A comprehensive corporate payment initiation and processing system built with Java 17 and Spring Boot 3.x.

## Overview

The Maple Payments Hub is a production-ready payment processing system that provides:

- **Payment Lifecycle Management**: Complete payment workflow from initiation to settlement
- **Role-Based Access Control**: Treasury operations, management, clearing, and audit roles
- **ISO20022 Standards**: Industry-standard payment messaging formats
- **Comprehensive Audit Trail**: Full compliance logging and event tracking
- **SFTP Integration**: Secure file transfer for payment batches
- **Real-time Processing**: Kafka-based event streaming architecture
- **HSM Simulation**: Cryptographic operations for payment security
- **Webhook Support**: Partner integration and notifications

## Quick Start

### Prerequisites

- Java 17+
- Docker and Docker Compose
- Make (optional, for convenience commands)

### Running Locally

1. **Clone and build the project:**
   ```bash
   git clone <repository-url>
   cd maple-payments-hub
   make build
   ```

2. **Start all services:**
   ```bash
   make up
   ```

3. **Seed with demo data:**
   ```bash
   make seed
   ```

4. **Access the application:**
   - Application: http://localhost:8082
   - API Documentation: http://localhost:8082/api/docs
   - Health Check: http://localhost:8082/actuator/health
   - Kafka UI: http://localhost:8081
   - SFTP Viewer: http://localhost:8080

### Manual Setup

If you prefer not to use Make:

```bash
# Build the application
./gradlew clean build

# Start services
docker-compose up -d

# Seed database
./gradlew runSeed
```

## Architecture

### Core Components

- **Payment Service**: Handles payment creation, validation, and lifecycle management
- **Approval Service**: Manages approval workflows for high-value payments
- **Batch Processing**: Automated ISO20022 file generation and SFTP submission
- **Audit Service**: Comprehensive event logging to database and Kafka
- **HSM Client**: Cryptographic operations (simulated for development)
- **Webhook Handler**: Partner notification processing with signature verification

### Technology Stack

- **Backend**: Java 17, Spring Boot 3.x, Spring Security (OAuth2), Spring Data JPA
- **Database**: PostgreSQL 15 with Flyway migrations
- **Messaging**: Apache Kafka for event streaming
- **File Transfer**: SFTP client for payment batch submission
- **Caching**: Redis for session and idempotency storage
- **Monitoring**: Micrometer, Prometheus metrics, structured logging
- **API Documentation**: OpenAPI 3 with Swagger UI
- **Testing**: JUnit 5, Testcontainers for integration tests

## API Overview

### Authentication

All API endpoints require OAuth2 JWT authentication with appropriate scopes:

- `payments:read` - View payment information
- `payments:write` - Create and modify payments  
- `approval:write` - Approve or reject payments
- `clearing:write` - Submit payments to clearing network
- `audit:read` - Access audit information

### Core Endpoints

#### Payment Operations
- `POST /api/v1/payments` - Submit new payment
- `GET /api/v1/payments/{id}` - Get payment details
- `POST /api/v1/payments/{id}/approve` - Approve payment
- `POST /api/v1/payments/{id}/reject` - Reject payment
- `POST /api/v1/payments/{id}/cancel` - Cancel payment
- `POST /api/v1/payments/{id}/submit-to-clearing` - Submit to clearing

#### Webhook Integration
- `POST /api/v1/webhooks/partner` - Partner notification webhook

#### Admin Operations
- `GET /api/v1/admin/batches` - List payment batches
- `GET /api/v1/admin/payments` - Search payments with filters

### User Roles

- **ROLE_TREASURY_OPS**: Basic payment operations and viewing
- **ROLE_TREASURY_MANAGER**: Payment approval and management oversight
- **ROLE_CLEARING**: Submit approved payments to clearing network
- **ROLE_AUDITOR**: Read-only access to audit trails and reports
- **ROLE_INTEGRATION**: External system integration access

## Development

### Database Schema

The system uses PostgreSQL with Flyway migrations. Key tables:

- **payments**: Core payment data with full lifecycle tracking
- **approvals**: Payment approval decisions and audit trail  
- **audit_events**: Comprehensive system event logging
- **users**: User management with role-based permissions
- **payment_batches**: Batch processing metadata

### Configuration

Configuration is handled through Spring Boot profiles:

- **dev**: Development settings with debug logging
- **prod**: Production settings with security hardening
- **test**: Test-specific configurations

Key configuration properties:

```yaml
maple:
  payment:
    approval-threshold-cents: 10000000  # $100,000
    batch-size: 100
    max-daily-limit-cents: 1000000000  # $10M
  
  sftp:
    host: localhost
    port: 2222
    username: sftp_user
    password: sftp_password
  
  hsm:
    mode: simulated
    key-alias: maple-payment-signing-key
```

### Testing

Run the complete test suite:

```bash
make test
```

Or run specific test types:

```bash
# Unit tests only
./gradlew test

# Integration tests with Testcontainers
./gradlew integrationTest

# All tests including static analysis
make ci-test
```

### Code Quality

The project enforces code quality through:

- **Spotless**: Code formatting
- **SpotBugs**: Static analysis
- **SonarQube**: Code quality metrics (CI integration)

```bash
# Format code
make format

# Check formatting and style
make lint
```

## Deployment

### Docker

Build and run with Docker:

```bash
# Build image
make docker-build

# Run with docker-compose
docker-compose up -d
```

### Infrastructure

The `infra/terraform/` directory contains Terraform configurations for AWS deployment:

- **VPC**: Network infrastructure
- **ECS/Fargate**: Container orchestration
- **RDS**: Managed PostgreSQL database
- **MSK**: Managed Kafka service
- **S3**: File storage for ISO20022 files
- **Secrets Manager**: Secure configuration storage

## Monitoring and Observability

### Health Checks

- **Application Health**: `/actuator/health`
- **Database**: Connection pool and query health
- **Kafka**: Producer and consumer health
- **External Services**: SFTP and partner connectivity

### Metrics

Prometheus metrics are available at `/actuator/prometheus`:

- Request/response metrics
- Database connection pool metrics
- Kafka producer/consumer metrics
- Custom business metrics (payment counts, amounts, etc.)

### Logging

Structured JSON logging with:

- Request tracing with correlation IDs
- Sensitive data masking
- Audit event logging
- Performance metrics

## Security

### Authentication & Authorization

- OAuth2 JWT tokens with role-based claims
- Request-level permission checking
- API rate limiting and throttling

### Data Protection

- Sensitive field masking in logs and responses
- Request idempotency with deduplication
- HMAC signature verification for webhooks
- TLS encryption for all external communications

### Audit Compliance

- Comprehensive audit trail in database and Kafka
- Immutable audit records with full context
- Real-time monitoring of security events
- Compliance reporting capabilities

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make changes with appropriate tests
4. Run code quality checks: `make lint`
5. Submit a pull request

## License

Proprietary - Maple Financial Corporation

## Support

For technical support or questions:

- **Email**: payments-team@mapleco.com
- **Documentation**: https://payments.mapleco.com/docs
- **Issue Tracker**: https://github.com/maple-financial/payments-hub/issues
