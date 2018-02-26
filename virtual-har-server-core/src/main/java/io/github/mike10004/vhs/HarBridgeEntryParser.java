package io.github.mike10004.vhs;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteSource;
import com.google.common.net.HostAndPort;
import com.google.common.net.HttpHeaders;
import io.github.mike10004.vhs.harbridge.HarBridge;
import io.github.mike10004.vhs.harbridge.HarBridge.ResponseData;
import io.github.mike10004.vhs.harbridge.HttpMethod;
import io.github.mike10004.vhs.harbridge.ParsedRequest;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public class HarBridgeEntryParser<E> implements EntryParser<E> {

    private final HarBridge<E> bridge;

    public HarBridgeEntryParser(HarBridge<E> bridge) {
        this.bridge = requireNonNull(bridge);
    }

    protected URI parseUrl(HttpMethod method, String url) {
        try {
            if (method == HttpMethod.CONNECT) {
                HostAndPort hostAndPort = HostAndPort.fromString(url);
                URI uri = new URI(null, null, hostAndPort.getHost(), hostAndPort.getPortOrDefault(-1), null, null, null);
                return uri;
            } else {
                return new URI(url);
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("URL has unexpected syntax", e);
        }
    }

    public ParsedRequest parseRequest(E harEntry) throws IOException {
        HttpMethod method = HttpMethod.valueOf(bridge.getRequestMethod(harEntry));
        URI parsedUrl = parseUrl(method, bridge.getRequestUrl(harEntry));
        Multimap<String, String> query = parseQuery(parsedUrl);
        Multimap<String, String> indexedHeaders = indexHeaders(bridge.getRequestHeaders(harEntry));
        ByteSource bodySource = bridge.getRequestPostData(harEntry);
        byte[] body = null;
        if (bodySource != null) {
            body = bodySource.read();
        }
        return ParsedRequest.inMemory(method, parsedUrl, query, indexedHeaders, body);
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

    @Override
    public HttpRespondable parseResponse(ParsedRequest request, E entry) throws IOException {
        int status = bridge.getResponseStatus(entry);
        ResponseData responseData = bridge.getResponseData(request, entry);
        return constructRespondable(status, responseData);
    }

    protected static void replaceContentLength(Multimap<String, String> headers, @Nullable Long value) {
        Set<String> caseSensitiveKeys = headers.keySet().stream().filter(HttpHeaders.CONTENT_LENGTH::equalsIgnoreCase).collect(Collectors.toSet());
        if (value != null && caseSensitiveKeys.size() == 1) {
            Collection<String> vals = headers.get(caseSensitiveKeys.iterator().next());
            if (vals.size() == 1) {
                if (value.toString().equals(vals.iterator().next())) {
                    // no replacement needed
                    return;
                }
            }
        }
        caseSensitiveKeys.forEach(headers::removeAll);
        if (value != null) {
            headers.put(HttpHeaders.CONTENT_LENGTH, value.toString());
        }
    }

    protected static HttpRespondable constructRespondable(int status, ResponseData responseData) throws IOException {
        Multimap<String, String> headers = ArrayListMultimap.create();
        responseData.headers.forEach(header -> {
            headers.put(header.getKey(), header.getValue());
        });
        replaceContentLength(headers, responseData.body.size());
        return HttpRespondable.inMemory(status, headers, responseData.contentType, responseData.body);
    }

}
