package com.maple.service.hsm;

import com.maple.service.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Simulated HSM implementation for development and testing.
 * 
 * Uses in-memory RSA key pair for cryptographic operations.
 * In production, this would be replaced with actual HSM integration.
 */
@Service
public class SimulatedHsm implements HsmClient {

    private static final Logger logger = LoggerFactory.getLogger(SimulatedHsm.class);
    private static final String ALGORITHM = "RSA";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final int KEY_SIZE = 2048;

    private final String keyAlias;
    private final AuditService auditService;
    private final KeyPair keyPair;

    @Autowired
    public SimulatedHsm(@Value("${maple.hsm.key-alias:maple-payment-signing-key}") String keyAlias,
                       AuditService auditService) {
        this.keyAlias = keyAlias;
        this.auditService = auditService;
        this.keyPair = generateKeyPair();
        logger.info("Simulated HSM initialized with key alias: {}", keyAlias);
    }

    @Override
    public byte[] sign(byte[] data) throws HsmException {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(keyPair.getPrivate());
            signature.update(data);
            byte[] result = signature.sign();
            
            auditService.auditHsmOperation("SIGN", keyAlias, true);
            logger.debug("Successfully signed {} bytes of data", data.length);
            return result;
        } catch (Exception e) {
            auditService.auditHsmOperation("SIGN", keyAlias, false);
            logger.error("Failed to sign data", e);
            throw new HsmException("Signing failed", e);
        }
    }

    @Override
    public byte[] decrypt(byte[] encryptedData) throws HsmException {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
            byte[] result = cipher.doFinal(encryptedData);
            
            auditService.auditHsmOperation("DECRYPT", keyAlias, true);
            logger.debug("Successfully decrypted {} bytes of data", encryptedData.length);
            return result;
        } catch (Exception e) {
            auditService.auditHsmOperation("DECRYPT", keyAlias, false);
            logger.error("Failed to decrypt data", e);
            throw new HsmException("Decryption failed", e);
        }
    }

    @Override
    public byte[] encrypt(byte[] data) throws HsmException {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keyPair.getPublic());
            byte[] result = cipher.doFinal(data);
            
            auditService.auditHsmOperation("ENCRYPT", keyAlias, true);
            logger.debug("Successfully encrypted {} bytes of data", data.length);
            return result;
        } catch (Exception e) {
            auditService.auditHsmOperation("ENCRYPT", keyAlias, false);
            logger.error("Failed to encrypt data", e);
            throw new HsmException("Encryption failed", e);
        }
    }

    @Override
    public boolean verify(byte[] data, byte[] signature) throws HsmException {
        try {
            Signature verifier = Signature.getInstance(SIGNATURE_ALGORITHM);
            verifier.initVerify(keyPair.getPublic());
            verifier.update(data);
            boolean result = verifier.verify(signature);
            
            auditService.auditHsmOperation("VERIFY", keyAlias, result);
            logger.debug("Signature verification result: {}", result);
            return result;
        } catch (Exception e) {
            auditService.auditHsmOperation("VERIFY", keyAlias, false);
            logger.error("Failed to verify signature", e);
            throw new HsmException("Signature verification failed", e);
        }
    }

    @Override
    public byte[] getPublicKey() throws HsmException {
        try {
            return keyPair.getPublic().getEncoded();
        } catch (Exception e) {
            logger.error("Failed to get public key", e);
            throw new HsmException("Failed to get public key", e);
        }
    }

    @Override
    public boolean isHealthy() {
        try {
            // Simple health check - sign and verify a test message
            byte[] testData = "health-check".getBytes();
            byte[] signature = sign(testData);
            return verify(testData, signature);
        } catch (Exception e) {
            logger.error("HSM health check failed", e);
            return false;
        }
    }

    @Override
    public String getKeyAlias() {
        return keyAlias;
    }

    /**
     * Generates a new RSA key pair for the simulated HSM.
     */
    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
            generator.initialize(KEY_SIZE);
            KeyPair keyPair = generator.generateKeyPair();
            
            logger.info("Generated new RSA key pair with {} bit keys", KEY_SIZE);
            logger.debug("Public key: {}", 
                        Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
            
            return keyPair;
        } catch (Exception e) {
            logger.error("Failed to generate key pair", e);
            throw new RuntimeException("HSM initialization failed", e);
        }
    }

    /**
     * For testing purposes - get the private key (would not exist in real HSM).
     */
    public String getPrivateKeyForTesting() {
        return Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
    }

    /**
     * For testing purposes - get the public key as Base64 string.
     */
    public String getPublicKeyAsString() {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }
}
