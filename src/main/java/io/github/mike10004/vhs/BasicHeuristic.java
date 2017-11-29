package io.github.mike10004.vhs;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.collect.Ordering;
import com.google.common.io.ByteSource;
import edu.umass.cs.benchlab.har.HarEntry;
import fi.iki.elonen.NanoHTTPD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class BasicHeuristic implements Heuristic {
    private final ImmutableList<ParsedEntry> entries;

    BasicHeuristic(Collection<ParsedEntry> entries) {
        this.entries = ImmutableList.copyOf(entries);
    }

    static HeuristicFactory factory() {
        return new Factory();
    }

    private static class Factory implements HeuristicFactory {

        @Override
        public Heuristic createHeuristic(List<HarEntry> entries) {
            return fromHarEntries(entries);
        }

        private static final Logger log = LoggerFactory.getLogger(BasicHeuristic.class.getName() + "$Factory");

        public static Heuristic fromHarEntries(List<HarEntry> entries) {
            log.trace("constructing heuristic from {} har entries", entries.size());
            return new BasicHeuristic(entries.stream()
                    .map(harEntry -> {
                        return new ParsedEntry(harEntry, ParsedRequest.create(harEntry.getRequest()));
                    }).collect(ImmutableList.toImmutableList()));
        }

    }

    static class ParsedEntry {
        public final HarEntry harEntry;
        public final ParsedRequest parsedEntry;

        ParsedEntry(HarEntry harEntry, ParsedRequest parsedEntry) {
            this.harEntry = Objects.requireNonNull(harEntry);
            this.parsedEntry = Objects.requireNonNull(parsedEntry);
        }

        public HarEntry getHarEntry() {
            return harEntry;
        }
    }

    @Override
    @Nullable
    public HarEntry findTopEntry(ParsedRequest request) {
        AtomicInteger topRating = new AtomicInteger(THRESHOLD_EXCLUSIVE);
        @Nullable HarEntry topEntry = entries.stream()
                .max(Ordering.<Integer>natural().onResultOf(entry -> {
                    int rating = rate(Objects.requireNonNull(entry).parsedEntry, request);
                    if (rating > THRESHOLD_EXCLUSIVE) {
                        topRating.set(rating);
                    }
                    return rating;
                }))
                .map(ParsedEntry::getHarEntry)
                .orElse(null);
        if (topRating.get() > THRESHOLD_EXCLUSIVE) {
            return topEntry;
        }
        return null;
    }

    private static final int INCREMENT = 100;
    private static final int THRESHOLD_EXCLUSIVE = 0;
    private static final int HALF_INCREMENT = INCREMENT / 2;

    public int rate(ParsedRequest entryRequest, ParsedRequest request) {
        // String name;
        URI requestUrl = request.parsedUrl;
        Multimap<String, String> requestHeaders = request.indexedHeaders;
        @Nullable Multimap<String, String> requestQuery = request.query;
        // method, host and pathname must match
        if (requestUrl == null) {
            return 0;
        }
        if (!entryRequest.method.equals(request.method)) { // TODO replace with enum != enum
            return 0;
        }
        if (!entryRequest.parsedUrl.getHost().equals(requestUrl.getHost())) {
            return 0;
        }
        if (!entryRequest.parsedUrl.getPath().equals(requestUrl.getPath())) {
            return 0;
        }
        int points = INCREMENT; // One point for matching above requirements

        // each query
        @Nullable Multimap<String, String> entryQuery = entryRequest.query;
        if (entryQuery != null && requestQuery != null) {
            for (String name : requestQuery.keySet()) {
                if (!entryQuery.containsKey(name)) {
                    points -= HALF_INCREMENT;
                } else {
                    points += stripProtocol(entryQuery.get(name)).equals(stripProtocol(requestQuery.get(name))) ? INCREMENT : 0;
                }
            }

            for (String name : entryQuery.keySet()) {
                if (!requestQuery.containsKey(name)) {
                    points -= HALF_INCREMENT;
                }
            }
        }

        // each header
        Multimap<String, String> entryHeaders = entryRequest.indexedHeaders;
        for (String name : requestHeaders.keySet()) {
            if (entryHeaders.containsKey(name)) {
                points += stripProtocol(entryHeaders.get(name)).equals(stripProtocol(requestHeaders.get(name))) ? INCREMENT : 0;
            }
            // TODO handle missing headers and adjust score appropriately
        }

        if (request.method == NanoHTTPD.Method.POST || request.method == NanoHTTPD.Method.PUT) {
            if (!request.isBodyPresent() && !entryRequest.isBodyPresent()) {
                points += HALF_INCREMENT;
            } else if (request.isBodyPresent() && entryRequest.isBodyPresent()) {
                ByteSource bs1 = bodyToByteSource(request);
                ByteSource bs2 = bodyToByteSource(entryRequest);
                try {
                    if (bs1.contentEquals(bs2)) {
                        points += INCREMENT;
                    }
                } catch (IOException ignore) {
                }
            }
        }

        return points;
    }

    private static ByteSource bodyToByteSource(ParsedRequest request) {
        return new ByteSource() {
            @Override
            public InputStream openStream() throws IOException {
                return request.openBodyStream();
            }
        };
    }

    private static Multiset<String> stripProtocol(Collection<String> strings) {
        return strings.stream().map(string -> string.replaceAll("^https?", "")).collect(ImmutableMultiset.toImmutableMultiset());
    }

}
