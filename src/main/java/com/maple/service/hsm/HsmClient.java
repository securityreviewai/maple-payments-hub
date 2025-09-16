package com.maple.service.hsm;

/**
 * Interface for Hardware Security Module operations.
 * 
 * Provides abstraction for cryptographic operations required for
 * payment processing, allowing for different HSM implementations
 * (hardware, simulated, cloud-based).
 */
public interface HsmClient {

    /**
     * Signs data using the configured signing key.
     * 
     * @param data the data to sign
     * @return the digital signature
     * @throws HsmException if signing fails
     */
    byte[] sign(byte[] data) throws HsmException;

    /**
     * Decrypts data using the configured decryption key.
     * 
     * @param encryptedData the data to decrypt
     * @return the decrypted data
     * @throws HsmException if decryption fails
     */
    byte[] decrypt(byte[] encryptedData) throws HsmException;

    /**
     * Encrypts data using the configured encryption key.
     * 
     * @param data the data to encrypt
     * @return the encrypted data
     * @throws HsmException if encryption fails
     */
    byte[] encrypt(byte[] data) throws HsmException;

    /**
     * Verifies a signature against the original data.
     * 
     * @param data the original data
     * @param signature the signature to verify
     * @return true if signature is valid
     * @throws HsmException if verification fails
     */
    boolean verify(byte[] data, byte[] signature) throws HsmException;

    /**
     * Gets the public key for the configured key alias.
     * 
     * @return the public key in DER format
     * @throws HsmException if key retrieval fails
     */
    byte[] getPublicKey() throws HsmException;

    /**
     * Checks if the HSM is available and responsive.
     * 
     * @return true if HSM is healthy
     */
    boolean isHealthy();

    /**
     * Gets the key alias being used.
     * 
     * @return the key alias
     */
    String getKeyAlias();

    /**
     * Exception thrown by HSM operations.
     */
    class HsmException extends Exception {
        public HsmException(String message) {
            super(message);
        }

        public HsmException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
