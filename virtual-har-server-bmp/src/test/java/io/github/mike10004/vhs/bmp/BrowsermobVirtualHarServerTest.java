package io.github.mike10004.vhs.bmp;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import de.sstoehr.harreader.HarReaderException;
import de.sstoehr.harreader.model.HarEntry;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import io.github.mike10004.vhs.EntryMatcher;
import io.github.mike10004.vhs.EntryMatcherFactory;
import io.github.mike10004.vhs.EntryParser;
import io.github.mike10004.vhs.HarBridgeEntryParser;
import io.github.mike10004.vhs.VirtualHarServer;
import io.github.mike10004.vhs.bmp.BrowsermobVhsConfig.DefaultUpstreamBarrierFactory;
import io.github.mike10004.vhs.harbridge.sstoehr.SstoehrHarBridge;
import io.github.mike10004.vhs.testsupport.VirtualHarServerTestBase;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import net.lightbody.bmp.core.har.HarRequest;
import org.junit.Before;
import org.junit.Test;

import javax.net.ssl.SSLServerSocketFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class BrowsermobVirtualHarServerTest extends VirtualHarServerTestBase {

    private enum ServiceType {
        BMP, NANO
    }

    private Table<ServiceType, String, String> requests;

    @Before
    public void setUp() {
        requests = HashBasedTable.create();
    }

    @Override
    protected VirtualHarServer createServer(int port, File harFile, EntryMatcherFactory entryMatcherFactory) throws IOException {
        List<HarEntry> entries;
        try {
            entries = new de.sstoehr.harreader.HarReader().readFromFile(harFile).getLog().getEntries();
        } catch (HarReaderException e) {
            throw new IOException(e);
        }
        EntryParser<HarEntry> parser = new HarBridgeEntryParser<>(new SstoehrHarBridge());
        EntryMatcher entryMatcher = entryMatcherFactory.createEntryMatcher(entries, parser);
        HarReplayManufacturer manufacturer = new HarReplayManufacturer(entryMatcher, Collections.emptyList()) {
            @Override
            public HttpResponse manufacture(HttpRequest originalRequest, HarRequest fullCapturedRequest) {
                requests.put(ServiceType.BMP, fullCapturedRequest.getMethod(), fullCapturedRequest.getUrl());
                return super.manufacture(originalRequest, fullCapturedRequest);
            }

            @Override
            public Response manufacture(IHTTPSession session) {
                requests.put(ServiceType.NANO, session.getMethod().name(), session.getUri());
                return super.manufacture(session);
            }
        };
        Path scratchParent = temporaryFolder.getRoot().toPath();
        BrowsermobVhsConfig config = BrowsermobVhsConfig.builder(manufacturer, manufacturer)
                .upstreamBarrierFactory(new LoggingUpstreamBarrierFactory())
                .scratchDirProvider(ScratchDirProvider.under(scratchParent))
                .build();
        return new BrowsermobVirtualHarServer(config);
    }

    @Test
    @Override
    public void httpsTest() throws Exception {
        super.httpsTest();
        System.out.format("%s requests handled%n", requests.size());
        requests.cellSet().forEach(cell -> {
            System.out.format("%s %s %s%n", cell.getRowKey(), cell.getColumnKey(), cell.getValue());
        });
    }

    private static class LoggingUpstreamBarrierFactory extends DefaultUpstreamBarrierFactory {

        private static void log(IHTTPSession session) {
            System.out.format("upstream: %s %s%n", session.getMethod(), session.getUri());
            session.getHeaders().forEach((name, value) -> {
                System.out.format("^   %s: %s%n", name, value);
            });
        }

        @Override
        protected UpstreamBarrier createBarrier(SSLServerSocketFactory sslServerSocketFactory) throws IOException {
            return new NanohttpdUpstreamBarrier(sslServerSocketFactory) {
                @Override
                protected Response respond(IHTTPSession session) {
                    log(session);
                    return super.respond(session);
                }
            };
        }
    }
}