package io.github.mike10004.vhs.bmp;

import de.sstoehr.harreader.HarReaderException;
import de.sstoehr.harreader.model.HarEntry;
import io.github.mike10004.vhs.EntryMatcher;
import io.github.mike10004.vhs.EntryMatcherFactory;
import io.github.mike10004.vhs.EntryParser;
import io.github.mike10004.vhs.HarBridgeEntryParser;
import io.github.mike10004.vhs.VirtualHarServer;
import io.github.mike10004.vhs.harbridge.sstoehr.SstoehrHarBridge;
import io.github.mike10004.vhs.testsupport.VirtualHarServerTestBase;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import net.lightbody.bmp.core.har.HarRequest;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class BrowsermobVirtualHarServerTest extends VirtualHarServerTestBase {

    private List<String> requests;
    private List<String> customValues;

    @Before
    public void setUp() throws IOException {
        customValues = Collections.synchronizedList(new ArrayList<>());
        requests = Collections.synchronizedList(new ArrayList<>());
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
        BrowsermobVhsConfig config = configBuilder.build();
        return new BrowsermobVirtualHarServer(config);
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
            }
        }
    }

}