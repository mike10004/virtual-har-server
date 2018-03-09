package io.github.mike10004.vhs.bmp;

import com.google.common.base.Suppliers;
import net.lightbody.bmp.mitm.CertificateAndKey;
import net.lightbody.bmp.mitm.CertificateAndKeySource;

import java.util.function.Supplier;

/**
 * Lazily-generating certificate and key source.
 */
public class LazyCertificateAndKeySource implements CertificateAndKeySource {

    private volatile Supplier<CertificateAndKey> memoizedCertificateAndKey;

    public LazyCertificateAndKeySource(KeystoreGenerator keystoreGenerator) {
        this(keystoreGenerator.asCertificateAndKeySupplier(null));
    }

    public LazyCertificateAndKeySource(Supplier<CertificateAndKey> certificateAndKeySupplier) {
        memoizedCertificateAndKey = Suppliers.memoize(certificateAndKeySupplier::get);
    }

    @Override
    public CertificateAndKey load() {
        return memoizedCertificateAndKey.get();
    }

}
