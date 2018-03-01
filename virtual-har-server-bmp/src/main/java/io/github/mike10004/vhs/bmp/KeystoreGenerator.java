package io.github.mike10004.vhs.bmp;

import com.google.common.io.Files;
import net.lightbody.bmp.mitm.RootCertificateGenerator;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Random;

public class KeystoreGenerator {

    private static final Logger log = LoggerFactory.getLogger(KeystoreGenerator.class);

    private static final String KEYSTORE_TYPE = "PKCS12";
    private static final String KEYSTORE_PRIVATE_KEY_ALIAS = "key";

    private final Path scratchDir;
    private final Random random;

    public KeystoreGenerator(Path scratchDir, Random random) {
        this.scratchDir = scratchDir;
        this.random = random;
    }

    public KeystoreGenerator() {
        this(FileUtils.getTempDirectory().toPath(), new SecureRandom());
    }

    public static class GeneratedKeystore {
        public final byte[] keystoreBytes;
        public final char[] keystorePassword;

        public GeneratedKeystore(byte[] keystoreBytes, char[] keystorePassword) {
            this.keystoreBytes = keystoreBytes;
            this.keystorePassword = keystorePassword;
        }
    }

    public GeneratedKeystore generate() throws IOException {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String password = Base64.getEncoder().encodeToString(bytes);
        byte[] keystoreBytes = generate(password);
        return new GeneratedKeystore(keystoreBytes, password.toCharArray());
    }

    protected byte[] generate(String keystorePassword) throws IOException {
        File keystoreFile = File.createTempFile("dynamically-generated-certificate", ".keystore", scratchDir.toFile());
        try {
            // create a dynamic CA root certificate generator using default settings (2048-bit RSA keys)
            RootCertificateGenerator rootCertificateGenerator = RootCertificateGenerator.builder().build();
            rootCertificateGenerator.saveRootCertificateAndKey(KEYSTORE_TYPE, keystoreFile,
                    KEYSTORE_PRIVATE_KEY_ALIAS, keystorePassword);
            log.debug("saved keystore to {} ({} bytes)%n", keystoreFile, keystoreFile.length());
            byte[] keystoreBytes = Files.toByteArray(keystoreFile);
            return keystoreBytes;
        } finally {
            FileUtils.forceDelete(keystoreFile);
        }
    }
}
