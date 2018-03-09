package io.github.mike10004.vhs.bmp;

import net.lightbody.bmp.mitm.CertificateAndKeySource;
import org.apache.commons.io.FileUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

public class BrowsermobVhsConfig {

    static final KeystoreGenerator.KeystoreType DEFAULT_KEYSTORE_TYPE = KeystoreGenerator.KeystoreType.PKCS12;

    @Nullable
    public final Integer port;
    public final ScratchDirProvider scratchDirProvider;
    public final BmpResponseManufacturer bmpResponseManufacturer;
    public final TlsEndpointFactory tlsEndpointFactory;
    public final CertificateAndKeySourceFactory certificateAndKeySourceFactory;
    public final BmpResponseFilter proxyToClientResponseFilter;

    private BrowsermobVhsConfig(Builder builder) {
        port = builder.port;
        scratchDirProvider = builder.scratchDirProvider;
        bmpResponseManufacturer = builder.bmpResponseManufacturer;
        tlsEndpointFactory = builder.tlsEndpointFactory;
        certificateAndKeySourceFactory = builder.certificateAndKeySourceFactory;
        proxyToClientResponseFilter = builder.proxyToClientResponseFilter;
    }

    public static Builder builder(BmpResponseManufacturer bmanufacturer) {
        return new Builder(bmanufacturer);
    }

    public interface DependencyFactory<T> {
        T produce(BrowsermobVhsConfig config, Path scratchDir) throws IOException;
    }

    public interface CertificateAndKeySourceFactory extends DependencyFactory<CertificateAndKeySource> {

        static CertificateAndKeySourceFactory predefined(CertificateAndKeySource certificateAndKeySource) {
            return new CertificateAndKeySourceFactory() {
                @Override
                public CertificateAndKeySource produce(BrowsermobVhsConfig config, Path scratchDir) {
                    return certificateAndKeySource;
                }
            };
        }
    }

    public interface TlsEndpointFactory extends DependencyFactory<TlsEndpoint> {
    }

    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public static final class Builder {

        @Nullable
        private Integer port;
        private ScratchDirProvider scratchDirProvider;
        private final BmpResponseManufacturer bmpResponseManufacturer;
        private TlsEndpointFactory tlsEndpointFactory;
        private CertificateAndKeySourceFactory certificateAndKeySourceFactory;
        private BmpResponseFilter proxyToClientResponseFilter = response -> {};

        private Builder(BmpResponseManufacturer bmpResponseManufacturer) {
            this.bmpResponseManufacturer = requireNonNull(bmpResponseManufacturer);
            scratchDirProvider = ScratchDirProvider.under(FileUtils.getTempDirectory().toPath());
            tlsEndpointFactory = (config, dir) -> TlsEndpoint.createDefault();
            certificateAndKeySourceFactory = (config, dir) -> new LazyCertificateAndKeySource(new KeystoreGenerator(DEFAULT_KEYSTORE_TYPE));
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder scratchDirProvider(ScratchDirProvider val) {
            scratchDirProvider = requireNonNull(val);
            return this;
        }

        public Builder tlsEndpointFactory(TlsEndpointFactory val) {
            tlsEndpointFactory = requireNonNull(val);
            return this;
        }

        public Builder certificateAndKeySource(CertificateAndKeySource certificateAndKeySource) {
            return certificateAndKeySourceFactory(CertificateAndKeySourceFactory.predefined(certificateAndKeySource));
        }

        public Builder certificateAndKeySourceFactory(CertificateAndKeySourceFactory val) {
            certificateAndKeySourceFactory = requireNonNull(val);
            return this;
        }

        public Builder proxyToClientResponseFilter(BmpResponseFilter proxyToClientResponseFilter) {
            this.proxyToClientResponseFilter = requireNonNull(proxyToClientResponseFilter);
            return this;
        }

        public BrowsermobVhsConfig build() {
            return new BrowsermobVhsConfig(this);
        }
    }
}
