package io.github.mike10004.vhs.bmp;

import net.lightbody.bmp.mitm.CertificateAndKeySource;
import org.apache.commons.io.FileUtils;

import javax.net.ssl.SSLServerSocketFactory;
import java.io.IOException;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

public class BrowsermobVhsConfig {

    public final ScratchDirProvider scratchDirProvider;
    public final NanoResponseManufacturer nanoResponseManufacturer;
    public final BmpResponseManufacturer bmpResponseManufacturer;
    public final UpstreamBarrierFactory upstreamBarrierFactory;
    public final CertificateAndKeySourceFactory certificateAndKeySourceFactory;

    private BrowsermobVhsConfig(Builder builder) {
        scratchDirProvider = builder.scratchDirProvider;
        nanoResponseManufacturer = builder.nanoResponseManufacturer;
        bmpResponseManufacturer = builder.bmpResponseManufacturer;
        upstreamBarrierFactory = builder.upstreamBarrierFactory;
        certificateAndKeySourceFactory = builder.certificateAndKeySourceFactory;
    }

    public static Builder builder(BmpResponseManufacturer bmanufacturer, NanoResponseManufacturer nmanufacturer) {
        return new Builder(bmanufacturer, nmanufacturer);
    }

    public interface DependencyFactory<T> {
        T produce(BrowsermobVhsConfig config, Path scratchDir) throws IOException;
    }

    public interface UpstreamBarrierFactory extends DependencyFactory<UpstreamBarrier> {
    }

    public interface CertificateAndKeySourceFactory extends DependencyFactory<CertificateAndKeySource> {
    }

    protected static class DefaultUpstreamBarrierFactory implements UpstreamBarrierFactory {

        @Override
        public UpstreamBarrier produce(BrowsermobVhsConfig config, Path scratchDir) throws IOException {
//            try {
//                KeystoreGenerator kgen = new KeystoreGenerator();
//                GeneratedKeystore generatedKeystore = kgen.generate();
//                KeyStore keystore = KeystoreGenerator.loadKeystore(generatedKeystore);
//                SSLServerSocketFactory sslServerSocketFactory = KeystoreGenerator.createTlsServerSocketFactory(keystore, generatedKeystore.keystorePassword);
//                return createBarrier(sslServerSocketFactory);
//            } catch (GeneralSecurityException e) {
//                throw new RuntimeException(e);
//            }
            return new NanohttpdUpstreamBarrier(null);
        }

        protected UpstreamBarrier createBarrier(SSLServerSocketFactory sslServerSocketFactory) throws IOException {
            return new NanohttpdUpstreamBarrier(sslServerSocketFactory);
        }
    }

    public static final class Builder {
        private ScratchDirProvider scratchDirProvider;
        private final NanoResponseManufacturer nanoResponseManufacturer;
        private final BmpResponseManufacturer bmpResponseManufacturer;
        private UpstreamBarrierFactory upstreamBarrierFactory;
        private CertificateAndKeySourceFactory certificateAndKeySourceFactory;

        private Builder(BmpResponseManufacturer bmpResponseManufacturer, NanoResponseManufacturer nanoResponseManufacturer) {
            this.bmpResponseManufacturer = requireNonNull(bmpResponseManufacturer);
            this.nanoResponseManufacturer = requireNonNull(nanoResponseManufacturer);
            scratchDirProvider = ScratchDirProvider.under(FileUtils.getTempDirectory().toPath());
            upstreamBarrierFactory = new DefaultUpstreamBarrierFactory();
            certificateAndKeySourceFactory = (config, dir) -> new AutoCertificateAndKeySource(dir);
        }

        public Builder scratchDirProvider(ScratchDirProvider val) {
            scratchDirProvider = requireNonNull(val);
            return this;
        }

        public Builder upstreamBarrierFactory(UpstreamBarrierFactory val) {
            upstreamBarrierFactory = requireNonNull(val);
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
