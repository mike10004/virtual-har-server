package io.github.mike10004.vhs;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.net.MediaType;
import io.github.mike10004.vhs.ParsedRequest.MemoryRequest;
import io.github.mike10004.vhs.harbridge.HarBridge;
import io.github.mike10004.vhs.repackaged.org.apache.http.client.utils.URLEncodedUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public class EntryParser<E> {

    private final HarBridge<E> bridge;

    public EntryParser(HarBridge<E> bridge) {
        this.bridge = requireNonNull(bridge);
    }

    public ParsedRequest parseRequest(E harEntry) throws IOException {
        URI parsedUrl = URI.create(bridge.getRequestUrl(harEntry));
        Multimap<String, String> query = parseQuery(parsedUrl);
        Multimap<String, String> indexedHeaders = indexHeaders(bridge.getRequestHeaders(harEntry));
        byte[] body = bridge.getRequestPostData(harEntry);
        return new MemoryRequest(HttpMethod.valueOf(bridge.getRequestMethod(harEntry)), parsedUrl, query, indexedHeaders, body);
    }

    /**
     * Creates a lowercase-keyed multimap from a list of headers.
     */
    protected Multimap<String, String> indexHeaders(Stream<? extends Entry<String, String>> entryHeaders) {
        return Defaults.indexHeaders(entryHeaders);
    }

    /**
     * Creates a multimap from the parameters specified in a URI.
     * @param uri the URI
     * @return the multimap
     */
    @Nullable
    public Multimap<String, String> parseQuery(URI uri) {
        return Defaults.parseQuery(uri);
    }

    public HttpRespondable parseResponse(E entry) throws IOException {
        int status = bridge.getResponseStatus(entry);
        byte[] body = bridge.getResponseBody(entry);
        Multimap<String, String> headers = ArrayListMultimap.create();
        bridge.getResponseHeaders(entry).forEach(header -> {
            headers.put(header.getKey(), header.getValue());
        });
        MediaType contentType = bridge.getResponseContentType(entry);
        return HttpRespondable.inMemory(status, headers, contentType, body);
    }

    protected static class Defaults {

        /**
         * Creates a lowercase-keyed multimap from a list of headers.
         */
        @VisibleForTesting
        static Multimap<String, String> indexHeaders(Stream<? extends Entry<String, String>> entryHeaders) {
            Multimap<String, String> headers = ArrayListMultimap.create();
            entryHeaders.forEach(header -> {
                headers.put(header.getKey().toLowerCase(), header.getValue());
            });
            return headers;
        }

        @Nullable
        @VisibleForTesting
        static Multimap<String, String> parseQuery(URI uri) {
            if (uri.getQuery() == null) {
                return null;
            }
            List<Entry<String, String>> nvps = URLEncodedUtils.parse(uri, StandardCharsets.UTF_8);
            Multimap<String, String> mm = ArrayListMultimap.create();
            nvps.forEach(nvp -> {
                mm.put(nvp.getKey().toLowerCase(), nvp.getValue());
            });
            return mm;
        }

    }


}
