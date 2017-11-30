package io.github.mike10004.vhs;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.net.MediaType;
import fi.iki.elonen.NanoHTTPD;
import io.github.mike10004.vhs.ParsedRequest.MemoryRequest;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.util.Map.Entry;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public class RequestParser<E> {

    private final HarBridge<E> bridge;

    public RequestParser(HarBridge<E> bridge) {
        this.bridge = requireNonNull(bridge);
    }

    public ParsedRequest parseRequest(E harEntry) throws IOException {
        URI parsedUrl = URI.create(bridge.getRequestUrl(harEntry));
        Multimap<String, String> query = parseQuery(parsedUrl);
        Multimap<String, String> indexedHeaders = indexHeaders(bridge.getRequestHeaders(harEntry));
        byte[] body = bridge.getRequestPostData(harEntry);
        return new MemoryRequest(NanoHTTPD.Method.valueOf(bridge.getRequestMethod(harEntry)), parsedUrl, query, indexedHeaders, body);
    }

    /**
     * Creates a lowercase-keyed multimap from a list of headers.
     */
    protected Multimap<String, String> indexHeaders(Stream<? extends Entry<String, String>> entryHeaders) {
        return ParsedRequest.indexHeaders(entryHeaders);
    }

    /**
     * Creates a multimap from the parameters specified in a URI.
     * @param uri the URI
     * @return the multimap
     */
    @Nullable
    protected Multimap<String, String> parseQuery(URI uri) {
        return ParsedRequest.parseQuery(uri);
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
}
