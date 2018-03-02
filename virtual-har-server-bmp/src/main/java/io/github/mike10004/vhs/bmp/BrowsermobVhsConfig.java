package io.github.mike10004.vhs.bmp;

import net.lightbody.bmp.mitm.CertificateAndKeySource;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

public class BrowsermobVhsConfig {

    public final ScratchDirProvider scratchDirProvider;
    public final ResponseManufacturer manufacturer;
    public final UpstreamBarrierFactory upstreamBarrierFactory;
    public final CertificateAndKeySourceFactory certificateAndKeySourceFactory;

    private BrowsermobVhsConfig(Builder builder) {
        scratchDirProvider = builder.scratchDirProvider;
        manufacturer = builder.manufacturer;
        upstreamBarrierFactory = builder.upstreamBarrierFactory;
        certificateAndKeySourceFactory = builder.certificateAndKeySourceFactory;
    }

    public static Builder builder(ResponseManufacturer manufacturer) {
        return new Builder(manufacturer);
    }

    public interface DependencyFactory<T> {
        T produce(BrowsermobVhsConfig config, Path scratchDir) throws IOException;
    }

    public interface UpstreamBarrierFactory extends DependencyFactory<UpstreamBarrier> {
    }

    public interface CertificateAndKeySourceFactory extends DependencyFactory<CertificateAndKeySource> {
    }

    public static final class Builder {
        private ScratchDirProvider scratchDirProvider;
        private final ResponseManufacturer manufacturer;
        private UpstreamBarrierFactory upstreamBarrierFactory;
        private CertificateAndKeySourceFactory certificateAndKeySourceFactory;

        private Builder(ResponseManufacturer manufacturer) {
            this.manufacturer = requireNonNull(manufacturer);
            scratchDirProvider = ScratchDirProvider.under(FileUtils.getTempDirectory().toPath());
            upstreamBarrierFactory = (config, dir) -> new NanohttpdUpstreamBarrier();
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
