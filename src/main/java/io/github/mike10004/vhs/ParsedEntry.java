package io.github.mike10004.vhs;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteStreams;
import de.sstoehr.harreader.model.HarEntry;
import de.sstoehr.harreader.model.HarHeader;
import de.sstoehr.harreader.model.HarPostData;
import de.sstoehr.harreader.model.HarPostDataParam;
import de.sstoehr.harreader.model.HarRequest;
import de.sstoehr.harreader.model.HttpMethod;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Method;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

class ParsedEntry {

    public final HttpMethod method;
    public final URI parsedUrl;
    @Nullable
    public final ImmutableMultimap<String, String> query;
    public final ImmutableMultimap<String, String> indexedHeaders;
    @Nullable
    private final byte[] body;

    private ParsedEntry(HttpMethod method, URI parsedUrl, @Nullable Multimap<String, String> query, Multimap<String, String> indexedHeaders, @Nullable byte[] body) {
        this.method = Objects.requireNonNull(method);
        this.parsedUrl = Objects.requireNonNull(parsedUrl);
        this.query = query == null ? null : ImmutableMultimap.copyOf(query);
        this.indexedHeaders = ImmutableMultimap.copyOf(indexedHeaders);
        this.body = body == null ? null : Arrays.copyOf(body, body.length);
    }

    public static ParsedEntry create(HttpMethod method, URI requestUrl, Stream<? extends Map.Entry<String, String>> headers) {
        return create(method, requestUrl, headers, null);
    }

    public static ParsedEntry create(HttpMethod method, URI requestUrl, Stream<? extends Map.Entry<String, String>> headers, byte[] body) {
        @Nullable Multimap<String, String> query = parseQuery(requestUrl);
        return new ParsedEntry(method, requestUrl, query, indexHeaders(headers), body);
    }

    private static final Function<HarHeader, Map.Entry<String, String>> harHeaderToMapEntry = header -> new SimpleImmutableEntry<>(header.getName(), header.getValue());

    public static ParsedEntry create(HarRequest request) {
        URI parsedUrl = URI.create(request.getUrl());
        Multimap<String, String> query = parseQuery(parsedUrl);
        Multimap<String, String> indexedHeaders = indexHeaders(request.getHeaders());
        byte[] body = Hars.translate(request.getPostData(), indexedHeaders);
        return new ParsedEntry(request.getMethod(), parsedUrl, query, indexedHeaders, body);
    }

    /**
     * Creates a lowercase-keyed multimap from a list of headers.
     */
    private static Multimap<String, String> indexHeaders(Stream<? extends Map.Entry<String, String>> entryHeaders) {
        Multimap<String, String> headers = ArrayListMultimap.create();
        entryHeaders.forEach(header -> {
            headers.put(header.getKey().toLowerCase(), header.getValue());
        });
        return headers;
    }

    private static Multimap<String, String> indexHeaders(List<HarHeader> entryHeaders) {
        return indexHeaders(entryHeaders.stream().map(harHeaderToMapEntry));
    }

    @Nullable
    static Multimap<String, String> parseQuery(URI uri) {
        if (uri.getQuery() == null) {
            return null;
        }
        List<NameValuePair> nvps = URLEncodedUtils.parse(uri, StandardCharsets.UTF_8);
        Multimap<String, String> mm = ArrayListMultimap.create();
        nvps.forEach(nvp -> {
            mm.put(nvp.getName().toLowerCase(), nvp.getValue());
        });
        return mm;
    }

    public static ParsedEntry parseNanoRequest(NanoHTTPD.IHTTPSession session) throws IOException {
        HttpMethod harMethod = HttpMethod.valueOf(session.getMethod().name());
        URI url = URI.create(session.getUri());
        Map<String, String> headers = session.getHeaders();
        Method method = session.getMethod();
        byte[] body = null;
        switch (method) {
            case POST:
            case PUT:
                try (InputStream in = getOrSubstituteEmpty(session.getInputStream())) {
                    body = ByteStreams.toByteArray(in);
                }
        }
        return create(harMethod, url, headers.entrySet().stream(), body);
    }

    private static InputStream getOrSubstituteEmpty(InputStream in) {
        return in == null ? new ByteArrayInputStream(new byte[0]) : in;
    }
}
