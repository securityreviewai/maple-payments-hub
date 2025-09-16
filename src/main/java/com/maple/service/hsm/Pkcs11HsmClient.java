package com.maple.service.hsm;

import com.maple.service.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.Certificate;
import java.util.Collections;

/**
 * PKCS#11 HSM client implementation for production cryptographic operations.
 * 
 * Integrates with hardware security modules via PKCS#11 interface:
 * - SoftHSM2 for development/testing
 * - Production HSMs (Thales, SafeNet, AWS CloudHSM, etc.)
 * - Key rotation and access policy enforcement
 * - Session management and connection pooling
 */
@Service
@ConditionalOnProperty(name = "maple.hsm.mode", havingValue = "pkcs11")
public class Pkcs11HsmClient implements HsmClient {

    private static final Logger logger = LoggerFactory.getLogger(Pkcs11HsmClient.class);
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final String PROVIDER_NAME = "SunPKCS11";

    @Value("${maple.hsm.pkcs11.library-path:/usr/lib/softhsm/libsofthsm2.so}")
    private String libraryPath;

    @Value("${maple.hsm.pkcs11.slot:0}")
    private String slotId;

    @Value("${maple.hsm.pkcs11.pin:1234}")
    private String pin;

    @Value("${maple.hsm.key-alias:maple-payment-signing-key}")
    private String keyAlias;

    @Value("${maple.hsm.pkcs11.login-timeout:30}")
    private int loginTimeoutSeconds;

    private final AuditService auditService;
    private Provider pkcs11Provider;
    private KeyStore keyStore;
    private volatile boolean initialized = false;

    @Autowired
    public Pkcs11HsmClient(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Initializes PKCS#11 provider and keystore.
     */
    private synchronized void initialize() throws HsmException {
        if (initialized) return;

        try {
            logger.info("Initializing PKCS#11 HSM client with library: {}", libraryPath);

            // Create PKCS#11 configuration
            String pkcs11Config = createPkcs11Config();
            logger.debug("PKCS#11 configuration: {}", pkcs11Config);

            // Initialize provider
            pkcs11Provider = Security.getProvider(PROVIDER_NAME);
            if (pkcs11Provider == null) {
                pkcs11Provider = new sun.security.pkcs11.SunPKCS11(new ByteArrayInputStream(pkcs11Config.getBytes()));
                Security.addProvider(pkcs11Provider);
            }

            // Initialize keystore
            keyStore = KeyStore.getInstance("PKCS11", pkcs11Provider);
            keyStore.load(null, pin.toCharArray());

            // Verify key exists
            if (!keyStore.containsAlias(keyAlias)) {
                throw new HsmException("Key alias not found in HSM: " + keyAlias);
            }

            initialized = true;
            auditService.auditSystemAction("HSM_INITIALIZED", "HsmProvider", pkcs11Provider.getName());
            logger.info("PKCS#11 HSM client initialized successfully");

        } catch (Exception e) {
            logger.error("Failed to initialize PKCS#11 HSM client", e);
            auditService.auditSystemAction("HSM_INITIALIZATION_FAILED", "HsmProvider", "PKCS11");
            throw new HsmException("HSM initialization failed", e);
        }
    }

    @Override
    public byte[] sign(byte[] data) throws HsmException {
        ensureInitialized();
        
        try {
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(keyAlias, null);
            if (privateKey == null) {
                throw new HsmException("Private key not found: " + keyAlias);
            }

            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM, pkcs11Provider);
            signature.initSign(privateKey);
            signature.update(data);
            byte[] result = signature.sign();

            auditService.auditHsmOperation("SIGN", keyAlias, true);
            logger.debug("Successfully signed {} bytes of data using HSM", data.length);
            return result;

        } catch (Exception e) {
            auditService.auditHsmOperation("SIGN", keyAlias, false);
            logger.error("Failed to sign data using HSM", e);
            throw new HsmException("HSM signing failed", e);
        }
    }

    @Override
    public byte[] decrypt(byte[] encryptedData) throws HsmException {
        ensureInitialized();

        try {
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(keyAlias, null);
            if (privateKey == null) {
                throw new HsmException("Private key not found: " + keyAlias);
            }

            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWITHSHA-256ANDMGF1PADDING", pkcs11Provider);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] result = cipher.doFinal(encryptedData);

            auditService.auditHsmOperation("DECRYPT", keyAlias, true);
            logger.debug("Successfully decrypted {} bytes of data using HSM", encryptedData.length);
            return result;

        } catch (Exception e) {
            auditService.auditHsmOperation("DECRYPT", keyAlias, false);
            logger.error("Failed to decrypt data using HSM", e);
            throw new HsmException("HSM decryption failed", e);
        }
    }

    @Override
    public byte[] encrypt(byte[] data) throws HsmException {
        ensureInitialized();

        try {
            Certificate certificate = keyStore.getCertificate(keyAlias);
            if (certificate == null) {
                throw new HsmException("Certificate not found: " + keyAlias);
            }

            PublicKey publicKey = certificate.getPublicKey();
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWITHSHA-256ANDMGF1PADDING", pkcs11Provider);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] result = cipher.doFinal(data);

            auditService.auditHsmOperation("ENCRYPT", keyAlias, true);
            logger.debug("Successfully encrypted {} bytes of data using HSM", data.length);
            return result;

        } catch (Exception e) {
            auditService.auditHsmOperation("ENCRYPT", keyAlias, false);
            logger.error("Failed to encrypt data using HSM", e);
            throw new HsmException("HSM encryption failed", e);
        }
    }

    @Override
    public boolean verify(byte[] data, byte[] signature) throws HsmException {
        ensureInitialized();

        try {
            Certificate certificate = keyStore.getCertificate(keyAlias);
            if (certificate == null) {
                throw new HsmException("Certificate not found: " + keyAlias);
            }

            PublicKey publicKey = certificate.getPublicKey();
            Signature verifier = Signature.getInstance(SIGNATURE_ALGORITHM, pkcs11Provider);
            verifier.initVerify(publicKey);
            verifier.update(data);
            boolean result = verifier.verify(signature);

            auditService.auditHsmOperation("VERIFY", keyAlias, result);
            logger.debug("Signature verification result: {}", result);
            return result;

        } catch (Exception e) {
            auditService.auditHsmOperation("VERIFY", keyAlias, false);
            logger.error("Failed to verify signature using HSM", e);
            throw new HsmException("HSM signature verification failed", e);
        }
    }

    @Override
    public byte[] getPublicKey() throws HsmException {
        ensureInitialized();

        try {
            Certificate certificate = keyStore.getCertificate(keyAlias);
            if (certificate == null) {
                throw new HsmException("Certificate not found: " + keyAlias);
            }

            return certificate.getPublicKey().getEncoded();

        } catch (Exception e) {
            logger.error("Failed to get public key from HSM", e);
            throw new HsmException("Failed to get public key", e);
        }
    }

    @Override
    public boolean isHealthy() {
        try {
            ensureInitialized();
            
            // Perform basic operations to verify HSM connectivity
            byte[] testData = "health-check".getBytes();
            byte[] signature = sign(testData);
            boolean verified = verify(testData, signature);
            
            if (verified) {
                auditService.auditSystemAction("HSM_HEALTH_CHECK_PASSED", "HsmProvider", "PKCS11");
                return true;
            } else {
                auditService.auditSystemAction("HSM_HEALTH_CHECK_FAILED", "HsmProvider", "PKCS11");
                return false;
            }

        } catch (Exception e) {
            logger.error("HSM health check failed", e);
            auditService.auditSystemAction("HSM_HEALTH_CHECK_ERROR", "HsmProvider", "PKCS11");
            return false;
        }
    }

    @Override
    public String getKeyAlias() {
        return keyAlias;
    }

    /**
     * Rotates the HSM key to a new alias.
     */
    public void rotateKey(String newKeyAlias) throws HsmException {
        ensureInitialized();

        try {
            logger.info("Initiating key rotation from {} to {}", keyAlias, newKeyAlias);

            // Verify new key exists
            if (!keyStore.containsAlias(newKeyAlias)) {
                throw new HsmException("New key alias not found in HSM: " + newKeyAlias);
            }

            // Test new key
            PrivateKey newPrivateKey = (PrivateKey) keyStore.getKey(newKeyAlias, null);
            if (newPrivateKey == null) {
                throw new HsmException("Cannot access new private key: " + newKeyAlias);
            }

            // Perform test operation with new key
            byte[] testData = "key-rotation-test".getBytes();
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM, pkcs11Provider);
            signature.initSign(newPrivateKey);
            signature.update(testData);
            signature.sign();

            // Update key alias
            String oldKeyAlias = this.keyAlias;
            this.keyAlias = newKeyAlias;

            auditService.auditSystemAction("HSM_KEY_ROTATED", "HsmKey", 
                                          String.format("from:%s,to:%s", oldKeyAlias, newKeyAlias));
            logger.info("Key rotation completed successfully: {} -> {}", oldKeyAlias, newKeyAlias);

        } catch (Exception e) {
            logger.error("Key rotation failed", e);
            auditService.auditSystemAction("HSM_KEY_ROTATION_FAILED", "HsmKey", keyAlias);
            throw new HsmException("Key rotation failed", e);
        }
    }

    /**
     * Lists all available key aliases in the HSM.
     */
    public java.util.Set<String> listKeys() throws HsmException {
        ensureInitialized();

        try {
            return Collections.list(keyStore.aliases()).stream()
                .collect(java.util.stream.Collectors.toSet());
        } catch (Exception e) {
            logger.error("Failed to list keys from HSM", e);
            throw new HsmException("Failed to list keys", e);
        }
    }

    /**
     * Gets detailed information about the HSM session.
     */
    public HsmSessionInfo getSessionInfo() throws HsmException {
        ensureInitialized();

        return new HsmSessionInfo(
            pkcs11Provider.getName(),
            pkcs11Provider.getVersion(),
            libraryPath,
            slotId,
            keyAlias,
            keyStore.size()
        );
    }

    private void ensureInitialized() throws HsmException {
        if (!initialized) {
            initialize();
        }
    }

    private String createPkcs11Config() {
        return String.format(
            "name = SoftHSM%n" +
            "library = %s%n" +
            "slot = %s%n" +
            "attributes = compatibility%n" +
            "showInfo = false%n",
            libraryPath, slotId
        );
    }

    /**
     * HSM session information holder.
     */
    public static class HsmSessionInfo {
        private final String providerName;
        private final double providerVersion;
        private final String libraryPath;
        private final String slotId;
        private final String activeKeyAlias;
        private final int keyCount;

        public HsmSessionInfo(String providerName, double providerVersion, String libraryPath,
                             String slotId, String activeKeyAlias, int keyCount) {
            this.providerName = providerName;
            this.providerVersion = providerVersion;
            this.libraryPath = libraryPath;
            this.slotId = slotId;
            this.activeKeyAlias = activeKeyAlias;
            this.keyCount = keyCount;
        }

        // Getters
        public String getProviderName() { return providerName; }
        public double getProviderVersion() { return providerVersion; }
        public String getLibraryPath() { return libraryPath; }
        public String getSlotId() { return slotId; }
        public String getActiveKeyAlias() { return activeKeyAlias; }
        public int getKeyCount() { return keyCount; }

        @Override
        public String toString() {
            return String.format("HsmSessionInfo{provider=%s, version=%.1f, library=%s, slot=%s, activeKey=%s, keyCount=%d}",
                                providerName, providerVersion, libraryPath, slotId, activeKeyAlias, keyCount);
        }
    }
}
