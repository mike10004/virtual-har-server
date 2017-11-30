package io.github.mike10004.vhs;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import fi.iki.elonen.NanoHTTPD;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Objects;

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


}
