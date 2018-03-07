package io.github.mike10004.vhs.bmp;

import de.sstoehr.harreader.HarReaderException;
import de.sstoehr.harreader.model.HarEntry;
import io.github.mike10004.vhs.BasicHeuristic;
import io.github.mike10004.vhs.EntryMatcher;
import io.github.mike10004.vhs.EntryMatcherFactory;
import io.github.mike10004.vhs.EntryParser;
import io.github.mike10004.vhs.HarBridgeEntryParser;
import io.github.mike10004.vhs.HeuristicEntryMatcher;
import io.github.mike10004.vhs.ResponseInterceptor;
import io.github.mike10004.vhs.harbridge.sstoehr.SstoehrHarBridge;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class BmpTests {

    public static HarReplayManufacturer createManufacturer(File harFile, Iterable<ResponseInterceptor> responseInterceptors, BmpResponseListener responseListener) throws IOException {
        List<HarEntry> entries;
        try {
            entries = new de.sstoehr.harreader.HarReader().readFromFile(harFile).getLog().getEntries();
        } catch (HarReaderException e) {
            throw new IOException(e);
        }
        System.out.println("requests contained in HAR:");
        entries.stream().map(HarEntry::getRequest).forEach(request -> {
            System.out.format("  %s %s%n", request.getMethod(), request.getUrl());
        });
        EntryMatcherFactory entryMatcherFactory = HeuristicEntryMatcher.factory(new BasicHeuristic(), BasicHeuristic.DEFAULT_THRESHOLD_EXCLUSIVE);
        EntryParser<HarEntry> parser = new HarBridgeEntryParser<>(new SstoehrHarBridge());
        EntryMatcher entryMatcher = entryMatcherFactory.createEntryMatcher(entries, parser);
        return new HarReplayManufacturer(entryMatcher, responseInterceptors, responseListener);
    }
}
