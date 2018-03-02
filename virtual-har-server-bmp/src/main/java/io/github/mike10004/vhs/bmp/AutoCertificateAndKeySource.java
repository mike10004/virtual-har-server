package io.github.mike10004.vhs.bmp;

import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import io.github.mike10004.vhs.bmp.KeystoreGenerator.KeystoreData;
import net.lightbody.bmp.mitm.CertificateAndKey;
import net.lightbody.bmp.mitm.CertificateAndKeySource;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

/**
 * Auto-generating certificate and key source. Basic usage of this class
 * doesn't have external dependencies, but generating PKCS12 and PEM files
 * requires {@code keytool} and {@code openssl} be installed.
 */
public class AutoCertificateAndKeySource implements CertificateAndKeySource, java.io.Closeable {

    private static final String KEYSTORE_PRIVATE_KEY_ALIAS = "key";

    private static final Logger log = LoggerFactory.getLogger(AutoCertificateAndKeySource.class);

    private final KeystoreGenerator keystoreGenerator;
    private volatile MemoryKeyStoreCertificateSource onDemandSource;
    private volatile boolean closed;
    private transient final Object generationLock = new Object();

    public AutoCertificateAndKeySource(KeystoreGenerator keystoreGenerator) {
        this.keystoreGenerator = requireNonNull(keystoreGenerator);
    }

    @Override
    public void close() throws IOException {
        synchronized (generationLock) {
            if (onDemandSource != null) {
                Arrays.fill(onDemandSource.keystoreBytes, (byte) 0);
                closed = true;
                onDemandSource = null;
            }
        }
    }

    private void generateIfNecessary() {
        synchronized (generationLock) {
            checkState(!closed, "this source is closed");
            if (onDemandSource == null) {
                try {
                    onDemandSource = generate();
                } catch (IOException e) {
                    throw new CertificateGenerationException(e);
                }
            }
        }

    }

    @Override
    public CertificateAndKey load() {
        generateIfNecessary();
        return onDemandSource.load();
    }

    static class CertificateGenerationException extends RuntimeException {
        public CertificateGenerationException(String message) {
            super(message);
        }

        @SuppressWarnings("unused")
        public CertificateGenerationException(String message, Throwable cause) {
            super(message, cause);
        }

        public CertificateGenerationException(Throwable cause) {
            super(cause);
        }
    }

    protected static class MemoryKeyStoreCertificateSource extends KeyStoreStreamCertificateSource {

        public final byte[] keystoreBytes;
        public final String keystorePassword;

        public MemoryKeyStoreCertificateSource(String keyStoreType, byte[] keystoreBytes, String privateKeyAlias, String keyStorePassword) {
            super(keyStoreType, ByteSource.wrap(keystoreBytes), privateKeyAlias, keyStorePassword);
            this.keystoreBytes = requireNonNull(keystoreBytes);
            this.keystorePassword = requireNonNull(keyStorePassword);
        }

    }

    public static class SerializableForm {
        public String keystoreBase64;
        public String password;

        public SerializableForm(String keystoreBase64, String password) {
            this.keystoreBase64 = keystoreBase64;
            this.password = password;
        }
    }

    public SerializableForm createSerializableForm() throws IOException {
        generateIfNecessary();
        return new SerializableForm(Base64.getEncoder().encodeToString(onDemandSource.keystoreBytes), onDemandSource.keystorePassword);
    }

    protected MemoryKeyStoreCertificateSource generate() throws IOException {
        KeystoreData keystoreData = keystoreGenerator.generate();
        return new MemoryKeyStoreCertificateSource(keystoreData.keystoreType.name(), keystoreData.keystoreBytes, KEYSTORE_PRIVATE_KEY_ALIAS, String.copyValueOf(keystoreData.keystorePassword));
    }

}
