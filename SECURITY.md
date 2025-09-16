# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.x.x   | :white_check_mark: |

## Reporting a Vulnerability

The Maple Payments Hub team takes security vulnerabilities seriously. We appreciate your efforts to responsibly disclose your findings.

### How to Report

**DO NOT** create public GitHub issues for security vulnerabilities.

Instead, please report security vulnerabilities by emailing:

**security@mapleco.com**

Include the following information:
- Type of issue (e.g., buffer overflow, SQL injection, cross-site scripting, etc.)
- Full paths of source file(s) related to the manifestation of the issue
- The location of the affected source code (tag/branch/commit or direct URL)
- Any special configuration required to reproduce the issue
- Step-by-step instructions to reproduce the issue
- Proof-of-concept or exploit code (if possible)
- Impact of the issue, including how an attacker might exploit it

### Response Timeline

- **Acknowledgment**: We will acknowledge receipt of your vulnerability report within 48 hours
- **Initial Assessment**: We will provide an initial assessment within 5 business days
- **Resolution**: We aim to resolve critical vulnerabilities within 30 days

### Security Measures

#### Authentication & Authorization

- **OAuth2 JWT Authentication**: All API endpoints require valid JWT tokens
- **Role-Based Access Control (RBAC)**: Fine-grained permissions based on user roles
- **Scope-Based Authorization**: API access controlled by OAuth2 scopes
- **Request-Level Permissions**: Dynamic permission checking for sensitive operations

#### Data Protection

- **Sensitive Data Masking**: Automatic masking of sensitive fields in logs and API responses
- **Encryption in Transit**: TLS 1.3 for all external communications
- **Encryption at Rest**: Database-level encryption for sensitive data
- **Key Management**: HSM-based cryptographic operations (simulated in development)

#### API Security

- **Request Rate Limiting**: Protection against brute force and DoS attacks
- **Request Validation**: Comprehensive input validation and sanitization
- **CORS Configuration**: Restricted cross-origin resource sharing
- **Content Security Policy**: Protection against XSS attacks

#### Audit & Monitoring

- **Comprehensive Audit Trail**: All actions logged to immutable audit store
- **Real-time Security Monitoring**: Automated detection of suspicious activities
- **Failed Authentication Tracking**: Monitoring and alerting on authentication failures
- **Request Tracing**: Full request lifecycle tracking with correlation IDs

#### Infrastructure Security

- **Container Security**: Regular base image updates and vulnerability scanning
- **Network Segmentation**: Isolated network zones for different service tiers
- **Secrets Management**: Secure storage and rotation of cryptographic keys and passwords
- **Database Security**: Connection encryption, prepared statements, and access controls

### Security Configuration

#### Production Deployment Checklist

- [ ] Enable TLS encryption for all endpoints
- [ ] Configure secure session management
- [ ] Set up proper CORS policies
- [ ] Enable security headers (HSTS, CSP, etc.)
- [ ] Configure rate limiting
- [ ] Set up monitoring and alerting
- [ ] Rotate default secrets and keys
- [ ] Enable audit logging
- [ ] Configure firewall rules
- [ ] Set up intrusion detection

#### Environment Variables

Sensitive configuration must be provided via environment variables or secure secret stores:

```bash
# Database
SPRING_DATASOURCE_PASSWORD=<secure-password>

# JWT Configuration  
MAPLE_JWT_SECRET=<cryptographically-secure-key>

# Webhook Security
MAPLE_WEBHOOK_SECRET=<secure-webhook-secret>

# SFTP Credentials
MAPLE_SFTP_PASSWORD=<secure-sftp-password>
```

#### Security Headers

The application automatically sets security headers:

```
Strict-Transport-Security: max-age=31536000; includeSubDomains
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
Content-Security-Policy: default-src 'self'
```

### Compliance

#### Payment Card Industry (PCI)

- **Data Minimization**: Only store necessary payment information
- **Access Controls**: Role-based access with principle of least privilege  
- **Audit Logging**: Comprehensive logging of all access and changes
- **Encryption**: Strong encryption for data in transit and at rest

#### General Data Protection Regulation (GDPR)

- **Data Subject Rights**: Support for data access, rectification, and erasure
- **Privacy by Design**: Default privacy-preserving configurations
- **Data Processing Records**: Comprehensive audit trail of data processing activities
- **Data Breach Notification**: Automated alerting for potential data breaches

#### SOX Compliance

- **Segregation of Duties**: Role-based access controls prevent conflicts of interest
- **Audit Trail**: Immutable audit records for all financial transactions
- **Access Reviews**: Regular review and certification of user access rights
- **Change Management**: Controlled deployment processes with approval workflows

### Security Testing

#### Automated Security Testing

- **SAST (Static Analysis)**: Integrated with build pipeline using SpotBugs and SonarQube
- **DAST (Dynamic Analysis)**: Regular automated penetration testing
- **Dependency Scanning**: Regular scanning for vulnerable dependencies
- **Container Scanning**: Automated scanning of Docker images for vulnerabilities

#### Manual Security Testing

- **Penetration Testing**: Annual third-party security assessments
- **Code Reviews**: Security-focused code reviews for all changes
- **Architecture Reviews**: Regular security architecture assessments

### Incident Response

#### Security Incident Classification

- **Critical**: Active exploitation, data breach, or system compromise
- **High**: Potential for exploitation or significant security weakness
- **Medium**: Security vulnerability requiring attention but not immediately exploitable
- **Low**: Security improvement opportunities

#### Response Process

1. **Detection**: Automated monitoring and manual reporting
2. **Assessment**: Rapid evaluation of impact and severity
3. **Containment**: Immediate steps to prevent further damage
4. **Investigation**: Detailed forensic analysis
5. **Recovery**: System restoration and security improvements
6. **Lessons Learned**: Post-incident review and process improvements

### Security Contacts

- **Security Team**: security@mapleco.com
- **Emergency Response**: security-emergency@mapleco.com (24/7)
- **Security Officer**: ciso@mapleco.com

### Acknowledgments

We recognize and appreciate security researchers who responsibly disclose vulnerabilities:

- Security Hall of Fame: https://mapleco.com/security/hall-of-fame

### Updates

This security policy is reviewed and updated regularly. Last updated: January 2024.
