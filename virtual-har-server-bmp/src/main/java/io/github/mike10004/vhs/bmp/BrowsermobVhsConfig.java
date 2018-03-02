package io.github.mike10004.vhs.bmp;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import io.github.mike10004.vhs.bmp.KeystoreGenerator.GeneratedKeystore;
import net.lightbody.bmp.mitm.CertificateAndKeySource;
import org.apache.commons.io.FileUtils;

import javax.annotation.Nullable;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import static java.util.Objects.requireNonNull;

public class BrowsermobVhsConfig {

    public final ScratchDirProvider scratchDirProvider;
    public final NanoResponseManufacturer nanoResponseManufacturer;
    public final BmpResponseManufacturer bmpResponseManufacturer;
    public final TlsNanoServerFactory tlsNanoServerFactory;
    public final CertificateAndKeySourceFactory certificateAndKeySourceFactory;

    private BrowsermobVhsConfig(Builder builder) {
        scratchDirProvider = builder.scratchDirProvider;
        nanoResponseManufacturer = builder.nanoResponseManufacturer;
        bmpResponseManufacturer = builder.bmpResponseManufacturer;
        tlsNanoServerFactory = builder.tlsNanoServerFactory;
        certificateAndKeySourceFactory = builder.certificateAndKeySourceFactory;
    }

    public static Builder builder(BmpResponseManufacturer bmanufacturer, NanoResponseManufacturer nmanufacturer) {
        return new Builder(bmanufacturer, nmanufacturer);
    }

    public interface DependencyFactory<T> {
        T produce(BrowsermobVhsConfig config, Path scratchDir) throws IOException;
    }

    public interface CertificateAndKeySourceFactory extends DependencyFactory<CertificateAndKeySource> {
    }

    public interface TlsNanoServerFactory {
        TlsNanoServer produce(BrowsermobVhsConfig config, Path scratchDir, NanoResponseManufacturer responseManufacturer) throws IOException;
    }

    protected static class DefaultTlsNanoServerFactory implements TlsNanoServerFactory {
        @Override
        public TlsNanoServer produce(BrowsermobVhsConfig config, Path scratchDir, NanoResponseManufacturer responseManufacturer) throws IOException {
            try {
                KeystoreGenerator kgen = new KeystoreGenerator();
                GeneratedKeystore generatedKeystore = kgen.generate();
                KeyStore keystore = KeystoreGenerator.loadKeystore(generatedKeystore);
                SSLServerSocketFactory sslServerSocketFactory = KeystoreGenerator.createTlsServerSocketFactory(keystore, generatedKeystore.keystorePassword);
                return new TlsNanoServer(sslServerSocketFactory) {
                    @Nullable
                    @Override
                    protected Response respond(IHTTPSession session) {
                        return responseManufacturer.manufacture(session);
                    }
                };
            } catch (GeneralSecurityException e) {
                throw new IOException(e);
            }
        }
    }

    @SuppressWarnings("unused")
    public static final class Builder {
        private ScratchDirProvider scratchDirProvider;
        private final NanoResponseManufacturer nanoResponseManufacturer;
        private final BmpResponseManufacturer bmpResponseManufacturer;
        private TlsNanoServerFactory tlsNanoServerFactory;
        private CertificateAndKeySourceFactory certificateAndKeySourceFactory;

        private Builder(BmpResponseManufacturer bmpResponseManufacturer, NanoResponseManufacturer nanoResponseManufacturer) {
            this.bmpResponseManufacturer = requireNonNull(bmpResponseManufacturer);
            this.nanoResponseManufacturer = requireNonNull(nanoResponseManufacturer);
            scratchDirProvider = ScratchDirProvider.under(FileUtils.getTempDirectory().toPath());
            tlsNanoServerFactory = new DefaultTlsNanoServerFactory();
            certificateAndKeySourceFactory = (config, dir) -> new AutoCertificateAndKeySource(dir);
        }

        public Builder scratchDirProvider(ScratchDirProvider val) {
            scratchDirProvider = requireNonNull(val);
            return this;
        }

        public Builder tlsNanoServerFactory(TlsNanoServerFactory val) {
            tlsNanoServerFactory = requireNonNull(val);
            return this;
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
