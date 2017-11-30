package io.github.mike10004.vhs;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import fi.iki.elonen.NanoHTTPD;
import io.github.mike10004.vhs.repackaged.org.apache.http.client.utils.URLEncodedUtils;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Immutable class that represents an HTTP request.
 */
public abstract class ParsedRequest {

    /**
     * HTTP method of the request.
     */
    public final NanoHTTPD.Method method;

    /**
     * Full request URL (with query string).
     */
    public final URI url;

    /**
     * Query parameters parsed from query string. Null if the URL does not
     * contain a {@code ?}-character after the path. Empty if the URL contains
     * a {@code ?}-character but nothing after it. All keys are in original case.
     */
    @Nullable
    public final ImmutableMultimap<String, String> query;

    /**
     * Request headers with header names normalized to lowercase.
     */
    public final ImmutableMultimap<String, String> indexedHeaders;

    private ParsedRequest(NanoHTTPD.Method method, URI url, @Nullable Multimap<String, String> query, Multimap<String, String> indexedHeaders) {
        this.method = Objects.requireNonNull(method);
        this.url = Objects.requireNonNull(url);
        this.query = query == null ? null : ImmutableMultimap.copyOf(query);
        this.indexedHeaders = ImmutableMultimap.copyOf(indexedHeaders);

    }

    public abstract boolean isBodyPresent();

    public abstract InputStream openBodyStream() throws IOException;

    static class MemoryRequest extends ParsedRequest {

        @Nullable
        private final byte[] body;

        public MemoryRequest(NanoHTTPD.Method method, URI url, @Nullable Multimap<String, String> query, Multimap<String, String> indexedHeaders, @Nullable byte[] body) {
            super(method, url, query, indexedHeaders);
            this.body = body == null ? null : Arrays.copyOf(body, body.length);
        }

        public InputStream openBodyStream() throws IOException {
            if (body == null) {
                throw new IOException("no body present");
            }
            return new ByteArrayInputStream(body);
        }

        public boolean isBodyPresent() {
            return body != null;
        }
    }

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
