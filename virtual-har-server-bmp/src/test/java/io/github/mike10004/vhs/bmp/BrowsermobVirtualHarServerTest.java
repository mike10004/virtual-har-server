package io.github.mike10004.vhs.bmp;

import com.google.common.collect.ImmutableList;
import de.sstoehr.harreader.HarReaderException;
import de.sstoehr.harreader.model.HarEntry;
import io.github.mike10004.vhs.EntryMatcher;
import io.github.mike10004.vhs.EntryMatcherFactory;
import io.github.mike10004.vhs.EntryParser;
import io.github.mike10004.vhs.HarBridgeEntryParser;
import io.github.mike10004.vhs.VirtualHarServer;
import io.github.mike10004.vhs.harbridge.sstoehr.SstoehrHarBridge;
import io.github.mike10004.vhs.testsupport.VirtualHarServerTestBase;

import java.io.File;
import java.io.IOException;
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
        BrowsermobVhsConfig config = BrowsermobVhsConfig.builder(manufacturer).build();
        return new BrowsermobVirtualHarServer(config);
    }

}