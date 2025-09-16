#!/bin/bash

# Generate JWT token for testing Maple Payments Hub API
# This script creates a simple JWT for development/testing purposes

set -e

# Configuration
SECRET="${MAPLE_JWT_SECRET:-your-secret-key-here-change-in-production}"
ISSUER="${MAPLE_JWT_ISSUER:-http://localhost:8080/auth/realms/maple}"
AUDIENCE="${MAPLE_JWT_AUDIENCE:-maple-payments-api}"
USER_ID="${1:-550e8400-e29b-41d4-a716-446655440000}"
USERNAME="${2:-test-user}"
ROLES="${3:-ROLE_TREASURY_OPS,ROLE_TREASURY_MANAGER}"
SCOPES="${4:-payments:read,payments:write,approval:write}"

# Calculate timestamps
NOW=$(date +%s)
EXP=$((NOW + 3600))  # 1 hour from now

# Create header
HEADER=$(echo -n '{"alg":"HS256","typ":"JWT"}' | base64 | tr -d '=' | tr '/+' '_-' | tr -d '\n')

# Create payload
PAYLOAD=$(cat <<EOF | base64 | tr -d '=' | tr '/+' '_-' | tr -d '\n'
{
  "sub": "$USER_ID",
  "preferred_username": "$USERNAME",
  "iss": "$ISSUER",
  "aud": "$AUDIENCE",
  "exp": $EXP,
  "iat": $NOW,
  "scope": "$SCOPES",
  "authorities": ["$(echo $ROLES | sed 's/,/","/g')"],
  "email": "${USERNAME}@mapleco.com",
  "name": "Test User"
}
EOF
)

# Create signature
SIGNATURE=$(echo -n "${HEADER}.${PAYLOAD}" | openssl dgst -sha256 -hmac "$SECRET" -binary | base64 | tr -d '=' | tr '/+' '_-' | tr -d '\n')

# Combine to create JWT
JWT="${HEADER}.${PAYLOAD}.${SIGNATURE}"

echo "Generated JWT for user: $USERNAME"
echo "User ID: $USER_ID"
echo "Roles: $ROLES"
echo "Scopes: $SCOPES"
echo "Expires: $(date -d @$EXP)"
echo ""
echo "JWT Token:"
echo "$JWT"
echo ""
echo "Usage examples:"
echo ""
echo "# Submit a payment"
echo "curl -X POST http://localhost:8082/api/v1/payments \\"
echo "  -H 'Authorization: Bearer $JWT' \\"
echo "  -H 'Content-Type: application/json' \\"
echo "  -H 'Idempotency-Key: test-$(date +%s)' \\"
echo "  -d '{"
echo "    \"paymentReference\": \"PAY-$(date +%Y%m%d)-001\","
echo "    \"amountCents\": 150000,"
echo "    \"currency\": \"USD\","
echo "    \"debtorAccount\": \"ACC-12345678\","
echo "    \"creditorAccount\": \"ACC-87654321\","
echo "    \"creditorName\": \"Test Recipient\","
echo "    \"paymentPurpose\": \"Test payment\""
echo "  }'"
echo ""
echo "# Get payment status"
echo "curl -H 'Authorization: Bearer $JWT' \\"
echo "  http://localhost:8082/api/v1/payments/{payment-id}"
echo ""
echo "# Health check (no auth required)"
echo "curl http://localhost:8082/public/health"
