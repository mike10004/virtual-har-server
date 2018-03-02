package io.github.mike10004.vhs.bmp;

import de.sstoehr.harreader.HarReaderException;
import de.sstoehr.harreader.model.HarEntry;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import io.github.mike10004.vhs.EntryMatcher;
import io.github.mike10004.vhs.EntryMatcherFactory;
import io.github.mike10004.vhs.EntryParser;
import io.github.mike10004.vhs.HarBridgeEntryParser;
import io.github.mike10004.vhs.VirtualHarServer;
import io.github.mike10004.vhs.bmp.BrowsermobVhsConfig.UpstreamBarrierFactory;
import io.github.mike10004.vhs.harbridge.sstoehr.SstoehrHarBridge;
import io.github.mike10004.vhs.testsupport.VirtualHarServerTestBase;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class BrowsermobVirtualHarServerTest extends VirtualHarServerTestBase {

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
        HarReplayManufacturer manufacturer = new HarReplayManufacturer(entryMatcher, Collections.emptyList());
        Path scratchParent = temporaryFolder.getRoot().toPath();
        BrowsermobVhsConfig config = BrowsermobVhsConfig.builder(manufacturer)
                .upstreamBarrierFactory(new LoggingUpstreamBarrierFactory())
                .scratchDirProvider(ScratchDirProvider.under(scratchParent))
                .build();
        return new BrowsermobVirtualHarServer(config);
    }

    private static class LoggingUpstreamBarrierFactory implements UpstreamBarrierFactory {

        private static void log(IHTTPSession session) {
            System.out.format("upstream: %s %s%n", session.getMethod(), session.getUri());
            session.getHeaders().forEach((name, value) -> {
                System.out.format("^   %s: %s%n", name, value);
            });
        }

        @Override
        public UpstreamBarrier produce(BrowsermobVhsConfig config, Path scratchDir) throws IOException {
            return new NanohttpdUpstreamBarrier() {
                @Override
                protected Response respond(IHTTPSession session) {
                    log(session);
                    return super.respond(session);
                }
            };
        }
    }
}