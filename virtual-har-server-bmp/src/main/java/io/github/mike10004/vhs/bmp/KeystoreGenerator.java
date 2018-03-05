package io.github.mike10004.vhs.bmp;

import net.lightbody.bmp.mitm.RootCertificateGenerator;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
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

    @SuppressWarnings("unused")
    public enum KeystoreType {
        PKCS12,
        JKS
    }

    private static final String KEYSTORE_PRIVATE_KEY_ALIAS = "key";

    private final Path scratchDir;
    private final Random random;
    private final KeystoreType keystoreType;

    public KeystoreGenerator(KeystoreType keystoreType, Path scratchDir, Random random) {
        this.scratchDir = scratchDir;
        this.random = random;
        this.keystoreType = requireNonNull(keystoreType);
    }

    public KeystoreGenerator(KeystoreType keystoreType, Path scratchDir) {
        this(keystoreType, scratchDir, new SecureRandom());
    }

    public KeystoreGenerator(KeystoreType keystoreType) {
        this(keystoreType, FileUtils.getTempDirectory().toPath());
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
        File keystoreFile = File.createTempFile("dynamically-generated-certificate", ".keystore", scratchDir.toFile());
        try {
            RootCertificateGenerator rootCertificateGenerator = buildCertificateGenerator();
            rootCertificateGenerator.saveRootCertificateAndKey(keystoreType.name(), keystoreFile,
                    KEYSTORE_PRIVATE_KEY_ALIAS, keystorePassword);
            log.debug("saved keystore to {} ({} bytes)%n", keystoreFile, keystoreFile.length());
            byte[] keystoreBytes = java.nio.file.Files.readAllBytes(keystoreFile.toPath());
            return keystoreBytes;
        } finally {
            FileUtils.forceDelete(keystoreFile);
        }
    }

}
