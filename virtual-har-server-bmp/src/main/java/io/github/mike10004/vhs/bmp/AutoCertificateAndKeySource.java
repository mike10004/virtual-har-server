package io.github.mike10004.vhs.bmp;

import com.google.common.base.Suppliers;
import io.github.mike10004.vhs.bmp.KeystoreGenerator.KeystoreType;
import net.lightbody.bmp.mitm.CertificateAndKey;
import net.lightbody.bmp.mitm.CertificateAndKeySource;
import net.lightbody.bmp.mitm.KeyStoreCertificateSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyStore;
import java.util.function.Supplier;

/**
 * Auto-generating certificate and key source. Basic usage of this class
 * doesn't have external dependencies, but generating PKCS12 and PEM files
 * requires {@code keytool} and {@code openssl} be installed.
 */
public class AutoCertificateAndKeySource implements CertificateAndKeySource {

    private static final Logger log = LoggerFactory.getLogger(AutoCertificateAndKeySource.class);

    private volatile Supplier<CertificateAndKey> memoizedCertificateAndKey;

    public AutoCertificateAndKeySource(KeystoreGenerator keystoreGenerator) {
        this(keystoreGenerator.asCertificateAndKeySupplier());
    }

    public AutoCertificateAndKeySource(Supplier<CertificateAndKey> certificateAndKeySupplier) {
        memoizedCertificateAndKey = Suppliers.memoize(certificateAndKeySupplier::get);
    }

    @Override
    public CertificateAndKey load() {
        return memoizedCertificateAndKey.get();
    }

}
