package io.github.mike10004.vhs.bmp;

import de.sstoehr.harreader.HarReaderException;
import de.sstoehr.harreader.model.HarEntry;
import io.github.mike10004.vhs.EntryMatcher;
import io.github.mike10004.vhs.EntryMatcherFactory;
import io.github.mike10004.vhs.EntryParser;
import io.github.mike10004.vhs.HarBridgeEntryParser;
import io.github.mike10004.vhs.VirtualHarServer;
import io.github.mike10004.vhs.bmp.KeystoreGenerator.KeystoreType;
import io.github.mike10004.vhs.harbridge.sstoehr.SstoehrHarBridge;
import io.github.mike10004.vhs.repackaged.fi.iki.elonen.NanoHTTPD;
import io.github.mike10004.vhs.repackaged.fi.iki.elonen.NanoHTTPD.ClientHandler;
import io.github.mike10004.vhs.repackaged.fi.iki.elonen.NanoHTTPD.IHTTPSession;
import io.github.mike10004.vhs.repackaged.fi.iki.elonen.NanoHTTPD.Response;
import io.github.mike10004.vhs.testsupport.VirtualHarServerTestBase;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import net.lightbody.bmp.core.har.HarRequest;
import org.junit.Before;
import org.junit.Test;

import javax.net.ssl.SSLServerSocketFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BrowsermobVirtualHarServerTest extends VirtualHarServerTestBase {

    private List<String> requests;
    private List<Socket> secureSockets;
    private AtomicInteger nanohttpdRespondings;
    private List<String> customValues;

    @Before
    public void setUp() throws IOException {
        customValues = Collections.synchronizedList(new ArrayList<>());
        requests = Collections.synchronizedList(new ArrayList<>());
        secureSockets = Collections.synchronizedList(new ArrayList<>());
        nanohttpdRespondings = new AtomicInteger(0);
    }

    private static final String CUSTOM_HEADER_NAME = "X-Virtual-Har-Server-Unit-Test";

    @Override
    protected VirtualHarServer createServer(int port, File harFile, EntryMatcherFactory entryMatcherFactory, TlsStrategy tlsStrategy) throws IOException {
        List<HarEntry> entries;
        try {
            entries = new de.sstoehr.harreader.HarReader().readFromFile(harFile).getLog().getEntries();
        } catch (HarReaderException e) {
            throw new IOException(e);
        }
        EntryParser<HarEntry> parser = new HarBridgeEntryParser<>(new SstoehrHarBridge());
        EntryMatcher entryMatcher = entryMatcherFactory.createEntryMatcher(entries, parser);
        HarReplayManufacturer responseManufacturer = new HarReplayManufacturer(entryMatcher, Collections.emptyList()) {
            @Override
            public HttpResponse manufacture(HttpRequest originalRequest, HarRequest fullCapturedRequest) {
                requests.add(String.format("%s %s", fullCapturedRequest.getMethod(), fullCapturedRequest.getUrl()));
                return super.manufacture(originalRequest, fullCapturedRequest);
            }
        };
        Path scratchParent = temporaryFolder.getRoot().toPath();

        BmpResponseFilter responseFilter = new HeaderAddingFilter(CUSTOM_HEADER_NAME, () -> {
            String value = UUID.randomUUID().toString();
            customValues.add(value);
            return value;
        });
        BrowsermobVhsConfig.Builder configBuilder = BrowsermobVhsConfig.builder(responseManufacturer)
                .proxyToClientResponseFilter(responseFilter)
                .scratchDirProvider(ScratchDirProvider.under(scratchParent));
        if (tlsStrategy == TlsStrategy.SUPPORT_TLS) {
//            configBuilder.tlsEndpointFactory(new UnitTestTlsNanoServerFactory());
        }
        BrowsermobVhsConfig config = configBuilder.build();
        return new BrowsermobVirtualHarServer(config);
    }

    private class UnitTestTlsNanoServerFactory extends DefaultTlsNanoServerFactory {

        public UnitTestTlsNanoServerFactory() {
            super(new KeystoreGenerator(KeystoreType.PKCS12));
        }
        @Override
        protected TlsNanoServer createNanohttpdServer(SSLServerSocketFactory sslServerSocketFactory) throws IOException {
            return new TlsNanoServer(sslServerSocketFactory) {
                @Override
                protected ClientHandler createClientHandler(NanoHTTPD server, Socket finalAccept, InputStream inputStream, Supplier<ClientHandler> superSupplier) {
                    secureSockets.add(finalAccept);
                    return super.createClientHandler(server, finalAccept, inputStream, superSupplier);
                }

                @Override
                protected Response respond(IHTTPSession session) {
                    nanohttpdRespondings.incrementAndGet();
                    return super.respond(session);
                }
            };
        }
    }

    @Test
    @Override
    public void httpsTest() throws Exception {
        boolean clean = false;
        try {
            super.httpsTest();
            clean = true;
        } finally {
            System.out.format("%s requests handled%n", requests.size());
            requests.forEach(request -> {
                System.out.format("%s%n", request);
            });
            if (clean) {
                assertEquals("num requests", 1, requests.size());
                assertEquals("num responses", 1, customValues.size());
                assertEquals("num sockets", 0, secureSockets.size());
                assertEquals("num nanohttpd respondings", 0, nanohttpdRespondings.get());
            }
        }
    }

}