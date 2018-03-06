package io.github.mike10004.vhs.bmp;

import net.lightbody.bmp.mitm.CertificateAndKey;
import net.lightbody.bmp.mitm.RootCertificateGenerator;
import net.lightbody.bmp.mitm.tools.SecurityProviderTool;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.util.Base64;
import java.util.Random;

import static java.util.Objects.requireNonNull;

public class KeystoreGenerator {

    private static final Logger log = LoggerFactory.getLogger(KeystoreGenerator.class);

    private static final int KEYSTORE_BUFFER_LEN = 8192 * 2;

    private static final String KEYSTORE_PRIVATE_KEY_ALIAS = "key";

    @SuppressWarnings("unused")
    public enum KeystoreType {
        PKCS12,
        JKS
    }

    private final Random random;
    private final KeystoreType keystoreType;
    private final MemorySecurityProviderTool securityProviderTool;

    public KeystoreGenerator(KeystoreType keystoreType, MemorySecurityProviderTool securityProviderTool, Random random) {
        this.random = requireNonNull(random);
        this.keystoreType = requireNonNull(keystoreType);
        this.securityProviderTool = requireNonNull(securityProviderTool);
    }

    public KeystoreGenerator(KeystoreType keystoreType) {
        this(keystoreType, new MemorySecurityProviderTool(), new SecureRandom());
    }

    public static class KeystoreData {

        public final KeystoreType keystoreType;
        public final byte[] keystoreBytes;
        public final char[] keystorePassword;

        public KeystoreData(KeystoreType keystoreType, byte[] keystoreBytes, char[] keystorePassword) {
            this.keystoreBytes = requireNonNull(keystoreBytes);
            this.keystorePassword = keystorePassword;
            this.keystoreType = requireNonNull(keystoreType);
        }

        public KeyStore loadKeystore() throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
            return loadKeystore(keystoreType.name(), keystoreBytes, keystorePassword);
        }

        static KeyStore loadKeystore(String keystoreType, byte[] keystoreBytes, char[] keystorePassword) throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
            KeyStore keystore = KeyStore.getInstance(keystoreType);
            try (InputStream stream = new ByteArrayInputStream(keystoreBytes)) {
                keystore.load(stream, keystorePassword);
            }
            return keystore;
        }

    }

    /**
     * Generates keystore data with a new randomly-generated password.
     * @return the keystore data
     * @throws IOException on I/O error
     */
    public KeystoreData generate() throws IOException {
        byte[] bytes = new byte[PASSWORD_GENERATION_BYTE_LENGTH];
        random.nextBytes(bytes);
        String password = Base64.getEncoder().encodeToString(bytes);
        byte[] keystoreBytes = generate(password);
        return new KeystoreData(keystoreType, keystoreBytes, password.toCharArray());
    }

    private static final int PASSWORD_GENERATION_BYTE_LENGTH = 32;

    /**
     * Creates a dynamic CA root certificate generator using default settings (2048-bit RSA keys).
     * @return the generator
     */
    protected RootCertificateGenerator buildCertificateGenerator() {
        return RootCertificateGenerator.builder().build();
    }

    protected byte[] generate(String keystorePassword) throws IOException {
        RootCertificateGenerator rootCertificateGenerator = buildCertificateGenerator();
        byte[] keystoreBytes = saveRootCertificateAndKey(rootCertificateGenerator.load(),
                KEYSTORE_PRIVATE_KEY_ALIAS, keystorePassword);
        log.debug("saved keystore to {}-byte array", keystoreBytes.length);
        return keystoreBytes;
    }

    /**
     * Saves the generated certificate and private key as a file, using the specified password to protect the key store.
     * @param certificateAndKey the root certificate and key
     * @param privateKeyAlias alias for the private key in the KeyStore
     * @param password        password for the private key and the KeyStore
     */
    private byte[] saveRootCertificateAndKey(CertificateAndKey certificateAndKey,
                                          String privateKeyAlias,
                                          String password) throws IOException {
        ;
        KeyStore keyStore = securityProviderTool.createRootCertificateKeyStore(keystoreType.name(), certificateAndKey, privateKeyAlias, password);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(KEYSTORE_BUFFER_LEN);
        securityProviderTool.saveKeyStore(baos, keyStore, password);
        baos.flush();
        return baos.toByteArray();
    }

}
