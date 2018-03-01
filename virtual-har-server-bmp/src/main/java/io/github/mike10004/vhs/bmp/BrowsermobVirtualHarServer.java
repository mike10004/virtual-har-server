package io.github.mike10004.vhs.bmp;

import com.google.common.net.HostAndPort;
import io.github.mike10004.vhs.VirtualHarServer;
import io.github.mike10004.vhs.VirtualHarServerControl;
import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.mitm.CertificateAndKeySource;
import net.lightbody.bmp.mitm.manager.ImpersonatingMitmManager;
import net.lightbody.bmp.proxy.CaptureType;
import org.apache.commons.io.FileUtils;
import org.littleshoot.proxy.MitmManager;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public class BrowsermobVirtualHarServer implements VirtualHarServer {

    private final BrowsermobVhsConfig config;

    public BrowsermobVirtualHarServer(BrowsermobVhsConfig config) {
        this.config = requireNonNull(config);
    }

    @Override
    public VirtualHarServerControl start() throws IOException {
        //noinspection ResultOfMethodCallIgnored
        config.scratchDir.toFile().mkdirs();
        if (!config.scratchDir.toFile().isDirectory()) {
            throw new IOException("could not create directory " + config.scratchDir);
        }
        Path dirToDelete = java.nio.file.Files.createTempDirectory(config.scratchDir, "virtual-har-server-bmp");
        CertificateAndKeySource certificateAndKeySource = new AutoCertificateAndKeySource(dirToDelete);
        BrowserMobProxy proxy = startProxy(certificateAndKeySource);
        return new BrowsermobVhsControl(proxy, dirToDelete);
    }

    private Supplier<BrowserMobProxy> localProxyInstantiator = BrowserMobProxyServer::new;
    private Supplier<InetSocketAddress> upstreamProxyProvider = () -> null;

    public BrowserMobProxy startProxy(CertificateAndKeySource certificateAndKeySource) {
        BrowserMobProxy bmp = instantiateProxy();
        configureProxy(bmp, certificateAndKeySource);
        bmp.enableHarCaptureTypes(getCaptureTypes());
        bmp.newHar();
        bmp.start();
        return bmp;
    }

    protected MitmManager createMitmManager(@SuppressWarnings("unused") BrowserMobProxy proxy, CertificateAndKeySource certificateAndKeySource) {
        MitmManager mitmManager = ImpersonatingMitmManager.builder()
                .rootCertificateSource(certificateAndKeySource)
                .build();
        return mitmManager;
    }

    protected BrowserMobProxy instantiateProxy() {
        return localProxyInstantiator.get();
    }

    protected void configureProxy(BrowserMobProxy bmp, CertificateAndKeySource certificateAndKeySource) {
        if (certificateAndKeySource != null) {
            MitmManager mitmManager = createMitmManager(bmp, certificateAndKeySource);
            bmp.setMitmManager(mitmManager);
        }
        @Nullable InetSocketAddress upstreamProxy = upstreamProxyProvider.get();
        if (upstreamProxy != null) {
            bmp.setChainedProxy(upstreamProxy);
        }
    }

    class BrowsermobVhsControl implements VirtualHarServerControl {

        private final BrowserMobProxy proxy;
        private final Path dirToDelete;

        BrowsermobVhsControl(BrowserMobProxy proxy, Path dirToDelete) {
            this.proxy = requireNonNull(proxy);
            this.dirToDelete = requireNonNull(dirToDelete);
        }

        @Override
        public HostAndPort getSocketAddress() {
            return HostAndPort.fromParts("localhost", proxy.getPort());
        }

        @Override
        public void close() throws IOException {
            proxy.stop();
            FileUtils.deleteDirectory(dirToDelete.toFile());
        }
    }

    private static final Set<CaptureType> ALL_CAPTURE_TYPES = EnumSet.allOf(CaptureType.class);

    protected Set<CaptureType> getCaptureTypes() {
        return ALL_CAPTURE_TYPES;
    }

}
