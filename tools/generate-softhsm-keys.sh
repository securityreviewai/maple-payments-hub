#!/bin/bash

# SoftHSM2 Key Generation Script for Maple Payments Hub
# Creates RSA key pairs for payment signing and encryption in development/testing

set -e

# Configuration
SOFTHSM_CONF="${SOFTHSM2_CONF:-/etc/softhsm/softhsm2.conf}"
TOKEN_LABEL="${TOKEN_LABEL:-maple-payments-token}"
SO_PIN="${SO_PIN:-123456}"
USER_PIN="${USER_PIN:-1234}"
KEY_LABEL="${KEY_LABEL:-maple-payment-signing-key}"
KEY_SIZE="${KEY_SIZE:-2048}"
SLOT_ID="${SLOT_ID:-0}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Helper functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

check_dependencies() {
    log_info "Checking dependencies..."
    
    if ! command -v softhsm2-util &> /dev/null; then
        log_error "softhsm2-util not found. Please install SoftHSM2:"
        echo "  Ubuntu/Debian: sudo apt-get install softhsm2"
        echo "  RHEL/CentOS:   sudo yum install softhsm"
        echo "  macOS:         brew install softhsm"
        exit 1
    fi
    
    if ! command -v pkcs11-tool &> /dev/null; then
        log_warning "pkcs11-tool not found. Install opensc for additional functionality:"
        echo "  Ubuntu/Debian: sudo apt-get install opensc"
        echo "  RHEL/CentOS:   sudo yum install opensc"
        echo "  macOS:         brew install opensc"
    fi
    
    log_success "Dependencies check passed"
}

setup_softhsm_config() {
    log_info "Setting up SoftHSM configuration..."
    
    # Create config directory if it doesn't exist
    mkdir -p "$(dirname "$SOFTHSM_CONF")"
    
    # Create basic SoftHSM configuration
    cat > "$SOFTHSM_CONF" << EOF
# SoftHSM v2 configuration file for Maple Payments Hub

directories.tokendir = /var/lib/softhsm/tokens/
objectstore.backend = file
log.level = INFO

# Slots configuration
slots.removable = false
EOF

    # Create token directory
    sudo mkdir -p /var/lib/softhsm/tokens/
    sudo chown -R "$(whoami):$(id -gn)" /var/lib/softhsm/tokens/
    
    export SOFTHSM2_CONF="$SOFTHSM_CONF"
    log_success "SoftHSM configuration created at $SOFTHSM_CONF"
}

initialize_token() {
    log_info "Initializing SoftHSM token..."
    
    # Check if token already exists
    if softhsm2-util --show-slots | grep -q "$TOKEN_LABEL"; then
        log_warning "Token '$TOKEN_LABEL' already exists"
        read -p "Do you want to reinitialize it? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            log_info "Skipping token initialization"
            return
        fi
        
        # Delete existing token
        log_info "Deleting existing token..."
        softhsm2-util --delete-token --token "$TOKEN_LABEL"
    fi
    
    # Initialize new token
    log_info "Creating new token '$TOKEN_LABEL'..."
    softhsm2-util --init-token --slot "$SLOT_ID" --label "$TOKEN_LABEL" --so-pin "$SO_PIN" --pin "$USER_PIN"
    
    log_success "Token '$TOKEN_LABEL' initialized successfully"
}

generate_rsa_keypair() {
    log_info "Generating RSA key pair for payment signing..."
    
    # Check if key already exists
    if pkcs11-tool --module /usr/lib/softhsm/libsofthsm2.so --login --pin "$USER_PIN" --list-objects | grep -q "$KEY_LABEL"; then
        log_warning "Key '$KEY_LABEL' already exists"
        read -p "Do you want to regenerate it? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            log_info "Skipping key generation"
            return
        fi
    fi
    
    # Generate RSA key pair
    log_info "Generating $KEY_SIZE-bit RSA key pair..."
    pkcs11-tool --module /usr/lib/softhsm/libsofthsm2.so \
                --login --pin "$USER_PIN" \
                --keypairgen \
                --key-type rsa:$KEY_SIZE \
                --label "$KEY_LABEL" \
                --id 01
    
    log_success "RSA key pair generated successfully"
}

generate_test_certificate() {
    log_info "Generating self-signed test certificate..."
    
    # Create temporary directory for certificate generation
    TEMP_DIR=$(mktemp -d)
    cd "$TEMP_DIR"
    
    # Generate certificate signing request
    cat > cert.conf << EOF
[req]
distinguished_name = req_distinguished_name
req_extensions = v3_req
prompt = no

[req_distinguished_name]
CN = Maple Payments Hub Test Certificate
O = Maple Financial Corporation
OU = Information Technology
L = New York
ST = New York
C = US

[v3_req]
keyUsage = digitalSignature, nonRepudiation, keyEncipherment
extendedKeyUsage = serverAuth, clientAuth
subjectAltName = @alt_names

[alt_names]
DNS.1 = localhost
DNS.2 = maple-payments-hub
DNS.3 = *.maple-payments-hub
IP.1 = 127.0.0.1
EOF

    # Extract public key from HSM and create certificate
    pkcs11-tool --module /usr/lib/softhsm/libsofthsm2.so \
                --login --pin "$USER_PIN" \
                --read-object --type pubkey --id 01 \
                --output-file pubkey.der
    
    # Convert DER to PEM
    openssl rsa -pubin -inform DER -in pubkey.der -outform PEM -out pubkey.pem
    
    # Generate self-signed certificate (note: this is a simplified approach)
    # In production, you would use proper CA signing
    openssl req -new -x509 -key <(echo "dummy") -out test-cert.pem -days 365 -config cert.conf
    
    # Store certificate in HSM
    pkcs11-tool --module /usr/lib/softhsm/libsofthsm2.so \
                --login --pin "$USER_PIN" \
                --write-object test-cert.pem \
                --type cert \
                --id 01 \
                --label "$KEY_LABEL"
    
    # Cleanup
    cd - > /dev/null
    rm -rf "$TEMP_DIR"
    
    log_success "Test certificate generated and stored in HSM"
}

verify_setup() {
    log_info "Verifying HSM setup..."
    
    # Show tokens
    echo
    log_info "Available tokens:"
    softhsm2-util --show-slots
    
    # Show objects
    echo
    log_info "Objects in token '$TOKEN_LABEL':"
    pkcs11-tool --module /usr/lib/softhsm/libsofthsm2.so \
                --login --pin "$USER_PIN" \
                --list-objects
    
    # Test signing operation
    echo
    log_info "Testing signing operation..."
    echo "test data" | pkcs11-tool --module /usr/lib/softhsm/libsofthsm2.so \
                                  --login --pin "$USER_PIN" \
                                  --sign --mechanism SHA256-RSA-PKCS \
                                  --id 01 > /tmp/test-signature.bin 2>/dev/null
    
    if [ $? -eq 0 ]; then
        log_success "Signing test passed"
        rm -f /tmp/test-signature.bin
    else
        log_error "Signing test failed"
        return 1
    fi
}

generate_spring_config() {
    log_info "Generating Spring Boot configuration..."
    
    cat > softhsm-config.yml << EOF
# SoftHSM configuration for Maple Payments Hub
# Add these properties to your application-dev.yml

maple:
  hsm:
    mode: pkcs11
    pkcs11:
      library-path: /usr/lib/softhsm/libsofthsm2.so
      slot: $SLOT_ID
      pin: $USER_PIN
      login-timeout: 30
    key-alias: $KEY_LABEL

# Alternative library paths for different systems:
# macOS (Homebrew): /usr/local/lib/softhsm/libsofthsm2.so
# Windows: C:\\SoftHSM2\\lib\\softhsm2.dll
# Ubuntu/Debian: /usr/lib/x86_64-linux-gnu/softhsm/libsofthsm2.so
EOF

    log_success "Spring configuration written to softhsm-config.yml"
}

create_key_backup() {
    log_info "Creating key backup and recovery instructions..."
    
    cat > hsm-recovery.md << EOF
# HSM Key Recovery Instructions

## Environment Details
- Token Label: $TOKEN_LABEL
- SO PIN: $SO_PIN
- User PIN: $USER_PIN
- Key Label: $KEY_LABEL
- Key Size: $KEY_SIZE bits
- Slot ID: $SLOT_ID

## Recovery Commands

### Restore Token
\`\`\`bash
softhsm2-util --init-token --slot $SLOT_ID --label "$TOKEN_LABEL" --so-pin "$SO_PIN" --pin "$USER_PIN"
\`\`\`

### Import Existing Key (if backup available)
\`\`\`bash
pkcs11-tool --module /usr/lib/softhsm/libsofthsm2.so \\
            --login --pin "$USER_PIN" \\
            --write-object key-backup.pem \\
            --type privkey \\
            --id 01 \\
            --label "$KEY_LABEL"
\`\`\`

### Generate New Key Pair
\`\`\`bash
pkcs11-tool --module /usr/lib/softhsm/libsofthsm2.so \\
            --login --pin "$USER_PIN" \\
            --keypairgen \\
            --key-type rsa:$KEY_SIZE \\
            --label "$KEY_LABEL" \\
            --id 01
\`\`\`

## Security Notes

- Change default PINs in production
- Store SO PIN securely (required for token recovery)
- Backup keys using proper HSM backup procedures
- Use hardware HSMs for production environments
- Rotate keys according to security policy

## Troubleshooting

### Library Path Issues
Check alternative paths:
- /usr/lib/x86_64-linux-gnu/softhsm/libsofthsm2.so (Ubuntu 64-bit)
- /usr/local/lib/softhsm/libsofthsm2.so (macOS Homebrew)
- /opt/softhsm/lib/softhsm/libsofthsm2.so (Custom installation)

### Permission Issues
\`\`\`bash
sudo chown -R \$(whoami):\$(id -gn) /var/lib/softhsm/
sudo chmod -R 755 /var/lib/softhsm/
\`\`\`
EOF

    log_success "Recovery instructions written to hsm-recovery.md"
}

main() {
    echo "========================================"
    echo "  SoftHSM2 Setup for Maple Payments Hub"
    echo "========================================"
    echo
    
    check_dependencies
    setup_softhsm_config
    initialize_token
    generate_rsa_keypair
    generate_test_certificate
    verify_setup
    generate_spring_config
    create_key_backup
    
    echo
    echo "========================================"
    echo "  Setup Complete!"
    echo "========================================"
    log_success "SoftHSM2 setup completed successfully"
    echo
    echo "Next steps:"
    echo "1. Copy configuration from softhsm-config.yml to your application-dev.yml"
    echo "2. Set maple.hsm.mode=pkcs11 in your application properties"
    echo "3. Start your application with SoftHSM2 integration"
    echo "4. Review hsm-recovery.md for backup and recovery procedures"
    echo
    echo "Test the setup:"
    echo "  curl http://localhost:8082/actuator/health"
    echo
}

# Run main function
main "$@"
