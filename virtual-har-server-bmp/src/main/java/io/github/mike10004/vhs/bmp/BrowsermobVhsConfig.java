package io.github.mike10004.vhs.bmp;

import io.github.mike10004.vhs.bmp.KeystoreGenerator.KeystoreType;
import net.lightbody.bmp.mitm.CertificateAndKeySource;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Path;

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
            tlsNanoServerFactory = new DefaultTlsNanoServerFactory(new KeystoreGenerator(KeystoreType.PKCS12));
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
