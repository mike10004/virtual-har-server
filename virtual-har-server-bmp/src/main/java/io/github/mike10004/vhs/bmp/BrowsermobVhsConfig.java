package io.github.mike10004.vhs.bmp;

import io.github.mike10004.vhs.bmp.KeystoreGenerator.KeystoreType;
import net.lightbody.bmp.mitm.CertificateAndKeySource;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

public class BrowsermobVhsConfig {

    static final KeystoreType DEFAULT_KEYSTORE_TYPE = KeystoreType.PKCS12;

    public final ScratchDirProvider scratchDirProvider;
    public final BmpResponseManufacturer bmpResponseManufacturer;
    public final TlsEndpointFactory tlsEndpointFactory;
    public final CertificateAndKeySourceFactory certificateAndKeySourceFactory;

    private BrowsermobVhsConfig(Builder builder) {
        scratchDirProvider = builder.scratchDirProvider;
        bmpResponseManufacturer = builder.bmpResponseManufacturer;
        tlsEndpointFactory = builder.tlsEndpointFactory;
        certificateAndKeySourceFactory = builder.certificateAndKeySourceFactory;
    }

    public static Builder builder(BmpResponseManufacturer bmanufacturer) {
        return new Builder(bmanufacturer);
    }

    public interface DependencyFactory<T> {
        T produce(BrowsermobVhsConfig config, Path scratchDir) throws IOException;
    }

    public interface CertificateAndKeySourceFactory extends DependencyFactory<CertificateAndKeySource> {
    }

    public interface TlsEndpointFactory extends DependencyFactory<TlsEndpoint> {
    }

    @SuppressWarnings("unused")
    public static final class Builder {
        private ScratchDirProvider scratchDirProvider;
        private final BmpResponseManufacturer bmpResponseManufacturer;
        private TlsEndpointFactory tlsEndpointFactory;
        private CertificateAndKeySourceFactory certificateAndKeySourceFactory;

        private Builder(BmpResponseManufacturer bmpResponseManufacturer) {
            this.bmpResponseManufacturer = requireNonNull(bmpResponseManufacturer);
            scratchDirProvider = ScratchDirProvider.under(FileUtils.getTempDirectory().toPath());
            tlsEndpointFactory = (config, dir) -> new BrokenTlsEndpoint();
            certificateAndKeySourceFactory = (config, dir) -> new AutoCertificateAndKeySource(new KeystoreGenerator(DEFAULT_KEYSTORE_TYPE, dir));
        }

        public Builder scratchDirProvider(ScratchDirProvider val) {
            scratchDirProvider = requireNonNull(val);
            return this;
        }

        public Builder tlsEndpointFactory(TlsEndpointFactory val) {
            tlsEndpointFactory = requireNonNull(val);
            return this;
        }

        public Builder handleHttps(NanoResponseManufacturer nanoResponseManufacturer) {
            return handleHttps(new KeystoreGenerator(KeystoreType.PKCS12), nanoResponseManufacturer);
        }

        public Builder handleHttps(KeystoreGenerator keystoreGenerator, NanoResponseManufacturer nanoResponseManufacturer) {
            return tlsEndpointFactory(new DefaultTlsNanoServerFactory(keystoreGenerator, nanoResponseManufacturer));
        }

        public Builder certificateAndKeySourceFactory(CertificateAndKeySourceFactory val) {
            certificateAndKeySourceFactory = requireNonNull(val);
            return this;
        }

        public BrowsermobVhsConfig build() {
            return new BrowsermobVhsConfig(this);
        }
    }
}
