package io.github.mike10004.vhs.bmp;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Closeables;
import com.google.common.net.HostAndPort;
import io.github.mike10004.vhs.VirtualHarServer;
import io.github.mike10004.vhs.VirtualHarServerControl;
import io.github.mike10004.vhs.bmp.ScratchDirProvider.Scratch;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.mitm.CertificateAndKeySource;
import net.lightbody.bmp.mitm.manager.ImpersonatingMitmManager;
import net.lightbody.bmp.proxy.CaptureType;
import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;
import org.littleshoot.proxy.MitmManager;
import org.littleshoot.proxy.impl.ProxyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public class BrowsermobVirtualHarServer implements VirtualHarServer {

    private static final Logger log = LoggerFactory.getLogger(BrowsermobVirtualHarServer.class);

    private Supplier<BrowserMobProxy> localProxyInstantiator = BrowserMobProxyServer::new;

    private final BrowsermobVhsConfig config;

    public BrowsermobVirtualHarServer(BrowsermobVhsConfig config) {
        this.config = requireNonNull(config);
    }

    @Override
    public VirtualHarServerControl start() throws IOException {
        List<Closeable> closeables = new ArrayList<>();
        Scratch scratch = config.scratchDirProvider.createScratchDir();
        closeables.add(scratch);
        CertificateAndKeySource certificateAndKeySource;
        UpstreamBarrier upstreamBarrier;
        BrowserMobProxy proxy;
        Path scratchPath = scratch.getRoot();
        try {
            certificateAndKeySource = config.certificateAndKeySourceFactory.produce(config, scratchPath);
            upstreamBarrier = config.upstreamBarrierFactory.produce(config, scratchPath);
            closeables.add(upstreamBarrier);
            proxy = startProxy(upstreamBarrier, certificateAndKeySource);
        } catch (RuntimeException | IOException e) {
            closeAll(closeables, true);
            throw e;
        }
        return new BrowsermobVhsControl(proxy, closeables);
    }

    private static void closeAll(Iterable<? extends Closeable> closeables, @SuppressWarnings("SameParameterValue") boolean swallowIOException) {
        for (Closeable closeable : closeables) {
            try {
                Closeables.close(closeable, swallowIOException);
            } catch (IOException e) {
                log.warn("failed to close " + closeable, e);
            }
        }
    }

    public BrowserMobProxy startProxy(UpstreamBarrier upstreamBarrier, CertificateAndKeySource certificateAndKeySource) throws IOException {
        BrowserMobProxy bmp = instantiateProxy();
        configureProxy(bmp, upstreamBarrier, certificateAndKeySource);
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

    protected void configureProxy(BrowserMobProxy bmp, UpstreamBarrier upstreamBarrier, CertificateAndKeySource certificateAndKeySource) {
        MitmManager mitmManager = createMitmManager(bmp, certificateAndKeySource);
        bmp.setMitmManager(mitmManager);
        ResponseManufacturer responseManufacturer = config.manufacturer;
        bmp.addFirstHttpFilterFactory(new MonitorFiltersSource(responseManufacturer));
        InetSocketAddress upstreamProxy = upstreamBarrier.getSocketAddress();
        bmp.setChainedProxy(upstreamProxy);
    }

    class BrowsermobVhsControl implements VirtualHarServerControl {

        private final BrowserMobProxy proxy;
        private final ImmutableList<Closeable> closeables;

        BrowsermobVhsControl(BrowserMobProxy proxy, Iterable<Closeable> closeables) {
            this.proxy = requireNonNull(proxy);
            this.closeables = ImmutableList.copyOf(closeables);
        }

        @Override
        public HostAndPort getSocketAddress() {
            return HostAndPort.fromParts("localhost", proxy.getPort());
        }

        @Override
        public void close() throws IOException {
            closeAll(closeables, true);
            proxy.stop();
        }
    }

    private static final Set<CaptureType> ALL_CAPTURE_TYPES = EnumSet.allOf(CaptureType.class);

    protected Set<CaptureType> getCaptureTypes() {
        return ALL_CAPTURE_TYPES;
    }

    private static class MonitorFiltersSource extends HttpFiltersSourceAdapter {

        private final ResponseManufacturer responseManufacturer;

        public MonitorFiltersSource(ResponseManufacturer responseManufacturer) {
            this.responseManufacturer = requireNonNull(responseManufacturer);
        }

        @Override
        public HttpFilters filterRequest(HttpRequest originalRequest) {
            return doFilterRequest(originalRequest, null);
        }

        @Override
        public HttpFilters filterRequest(HttpRequest originalRequest, ChannelHandlerContext ctx) {
            return doFilterRequest(originalRequest, ctx);
        }

        private HttpFilters doFilterRequest(HttpRequest originalRequest, @Nullable ChannelHandlerContext ctx) {
            if (!ProxyUtils.isCONNECT(originalRequest)) {
                return new ResponseManufacturingFilter(originalRequest, ctx, responseManufacturer);
            } else {
                return null;
            }
        }

        @Override
        public int getMaximumRequestBufferSizeInBytes() {
            return 0;
        }

        @Override
        public int getMaximumResponseBufferSizeInBytes() {
            return 0;
        }
    }

}
