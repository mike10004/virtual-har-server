package io.github.mike10004.vhs.bmp;

import net.lightbody.bmp.mitm.CertificateAndKey;

import javax.annotation.Nullable;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.function.Supplier;

public interface KeystoreGenerator {

    KeystoreData generate(@Nullable String certificateCommonName) throws IOException, GeneralSecurityException;

    /**
     * Generates keystore data with a new randomly-generated password.
     * @return the keystore data
     * @throws IOException on I/O error
     */
    default KeystoreData generate() throws IOException, GeneralSecurityException {
        return generate(null);
    }

    default Supplier<CertificateAndKey> asCertificateAndKeySupplier(@Nullable String certificateCommonName) {
        return () -> {
            try {
                return generate(certificateCommonName).asCertificateAndKeySource().load();
            } catch (IOException | GeneralSecurityException e) {
                throw new CertificateGenerationException(e);
            }
        };
    }

    class CertificateGenerationException extends RuntimeException {
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

    static KeystoreGenerator createJreGenerator(KeystoreType keystoreType) {
        return new JreKeystoreGenerator(keystoreType);
    }
}
