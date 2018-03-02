package io.github.mike10004.vhs.bmp;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Closeables;
import com.google.common.net.HostAndPort;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import io.github.mike10004.vhs.VirtualHarServer;
import io.github.mike10004.vhs.VirtualHarServerControl;
import io.github.mike10004.vhs.bmp.KeystoreGenerator.GeneratedKeystore;
import io.github.mike10004.vhs.bmp.ScratchDirProvider.Scratch;
import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.mitm.CertificateAndKeySource;
import net.lightbody.bmp.mitm.manager.ImpersonatingMitmManager;
import net.lightbody.bmp.proxy.CaptureType;
import org.littleshoot.proxy.MitmManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
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
        BrowserMobProxy proxy;
        Path scratchPath = scratch.getRoot();
        try {
            certificateAndKeySource = config.certificateAndKeySourceFactory.produce(config, scratchPath);
            TlsNanoServer httpsInterceptionServer = createTlsEnabledNanoServer(config.nanoResponseManufacturer);
            closeables.add(httpsInterceptionServer);
            proxy = startProxy(config.bmpResponseManufacturer, HostAndPort.fromParts("localhost", httpsInterceptionServer.getListeningPort()), certificateAndKeySource);
        } catch (RuntimeException | IOException e) {
            closeAll(closeables, true);
            throw e;
        }
        return new BrowsermobVhsControl(proxy, closeables);
    }

    private static TlsNanoServer createTlsEnabledNanoServer(NanoResponseManufacturer responseManufacturer) throws IOException {
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
                throw new RuntimeException(e);
            }
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

    public BrowserMobProxy startProxy(BmpResponseManufacturer responseManufacturer, HostAndPort httpsHostRewriteDestination, CertificateAndKeySource certificateAndKeySource) throws IOException {
        BrowserMobProxy bmp = instantiateProxy();
        configureProxy(bmp, responseManufacturer, httpsHostRewriteDestination, certificateAndKeySource);
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

    protected void configureProxy(BrowserMobProxy bmp, BmpResponseManufacturer responseManufacturer, HostAndPort httpsHostRewriteDestination, CertificateAndKeySource certificateAndKeySource) {
        MitmManager mitmManager = createMitmManager(bmp, certificateAndKeySource);
        bmp.setMitmManager(mitmManager);
//        InetSocketAddress upstreamProxy = upstreamBarrier.getSocketAddress();
        bmp.addFirstHttpFilterFactory(new ResponseManufacturingFiltersSource(responseManufacturer, httpsHostRewriteDestination));
//        bmp.setChainedProxy(upstreamProxy);
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

}
