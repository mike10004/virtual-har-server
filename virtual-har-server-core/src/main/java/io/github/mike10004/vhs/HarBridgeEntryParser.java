package io.github.mike10004.vhs;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.net.MediaType;
import io.github.mike10004.vhs.ParsedRequest.MemoryRequest;
import io.github.mike10004.vhs.harbridge.HarBridge;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.util.Map.Entry;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public class HarBridgeEntryParser<E> implements EntryParser<E> {

    private final HarBridge<E> bridge;

    public HarBridgeEntryParser(HarBridge<E> bridge) {
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
        return HttpRequests.indexHeaders(entryHeaders);
    }

    /**
     * Creates a multimap from the parameters specified in a URI.
     * @param uri the URI
     * @return the multimap
     */
    @Nullable
    public Multimap<String, String> parseQuery(URI uri) {
        return HttpRequests.parseQuery(uri);
    }

    public HttpRespondable parseResponse(E entry) throws IOException {
        int status = bridge.getResponseStatus(entry);
        @Nullable byte[] body = bridge.getResponseBody(entry);
        @Nullable MediaType contentType = bridge.getResponseContentType(entry);
        return constructRespondable(status, body, () -> bridge.getResponseHeaders(entry), contentType);
    }

    protected HttpRespondable constructRespondable(int status, @Nullable byte[] body, Supplier<Stream<Entry<String, String>>> headersGetter, @Nullable MediaType contentType) {
        Multimap<String, String> headers = ArrayListMultimap.create();
        headersGetter.get().forEach(header -> {
            headers.put(header.getKey(), header.getValue());
        });
        if (body == null) {
            body = EMPTY_BYTE_ARRAY;
        }
        if (contentType == null) {
            contentType = MediaType.OCTET_STREAM;
        }
        return HttpRespondable.inMemory(status, headers, contentType, body);
    }

    private static final byte[] EMPTY_BYTE_ARRAY = {};
}
