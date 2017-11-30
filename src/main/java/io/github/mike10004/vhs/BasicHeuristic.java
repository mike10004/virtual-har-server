package io.github.mike10004.vhs;

import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.io.ByteSource;
import fi.iki.elonen.NanoHTTPD;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collection;

public class BasicHeuristic implements Heuristic {

    public static final int DEFAULT_THRESHOLD_EXCLUSIVE = 0;

    private static final int DEFAULT_INCREMENT = 100;
    private final int increment;
    private final int halfIncrement;

    public BasicHeuristic() {
        this(DEFAULT_INCREMENT);
    }

    public BasicHeuristic(int increment) {
        this.increment = increment;
        this.halfIncrement = this.increment / 2;
    }

    @Override
    public int rate(ParsedRequest entryRequest, ParsedRequest request) {
        // String name;
        URI requestUrl = request.url;
        Multimap<String, String> requestHeaders = request.indexedHeaders;
        @Nullable Multimap<String, String> requestQuery = request.query;
        // method, host and pathname must match
        if (requestUrl == null) {
            return 0;
        }
        if (!entryRequest.method.equals(request.method)) { // TODO replace with enum != enum
            return 0;
        }
        if (!entryRequest.url.getHost().equals(requestUrl.getHost())) {
            return 0;
        }
        if (!entryRequest.url.getPath().equals(requestUrl.getPath())) {
            return 0;
        }
        int points = increment; // One point for matching above requirements

        // each query
        @Nullable Multimap<String, String> entryQuery = entryRequest.query;
        if (entryQuery != null && requestQuery != null) {
            for (String name : requestQuery.keySet()) {
                if (!entryQuery.containsKey(name)) {
                    points -= halfIncrement;
                } else {
                    points += stripProtocol(entryQuery.get(name)).equals(stripProtocol(requestQuery.get(name))) ? increment : 0;
                }
            }

            for (String name : entryQuery.keySet()) {
                if (!requestQuery.containsKey(name)) {
                    points -= halfIncrement;
                }
            }
        }

        // each header
        Multimap<String, String> entryHeaders = entryRequest.indexedHeaders;
        for (String name : requestHeaders.keySet()) {
            if (entryHeaders.containsKey(name)) {
                points += stripProtocol(entryHeaders.get(name)).equals(stripProtocol(requestHeaders.get(name))) ? increment : 0;
            }
            // TODO handle missing headers and adjust score appropriately
        }

        if (request.method == NanoHTTPD.Method.POST || request.method == NanoHTTPD.Method.PUT) {
            if (!request.isBodyPresent() && !entryRequest.isBodyPresent()) {
                points += halfIncrement;
            } else if (request.isBodyPresent() && entryRequest.isBodyPresent()) {
                ByteSource bs1 = bodyToByteSource(request);
                ByteSource bs2 = bodyToByteSource(entryRequest);
                try {
                    if (bs1.contentEquals(bs2)) {
                        points += increment;
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
