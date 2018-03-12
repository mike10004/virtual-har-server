package io.github.mike10004.vhs;

import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.io.ByteSource;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.github.mike10004.vhs.harbridge.HttpMethod;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import io.github.mike10004.vhs.repackaged.org.apache.http.NameValuePair;
import io.github.mike10004.vhs.repackaged.org.apache.http.client.utils.URLEncodedUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of a heuristic that compares request headers, parameters, and bodies.
 * This is inspired by the JavaScript implementation of https://github.com/Stuk/server-replay.
 */
public class BasicHeuristic implements Heuristic {

    private static final Charset DEFAULT_FORM_DATA_CHARSET = StandardCharsets.ISO_8859_1;

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
        @Nullable Multimap<String, Optional<String>> requestQuery = request.query;
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
        @Nullable Multimap<String, Optional<String>> entryQuery = entryRequest.query;
        if (entryQuery != null && requestQuery != null) {
            for (String name : requestQuery.keySet()) {
                if (!entryQuery.containsKey(name)) {
                    points -= halfIncrement;
                } else {
                    points += stripProtocolFromOptionals(entryQuery.get(name)).equals(stripProtocolFromOptionals(requestQuery.get(name))) ? increment : 0;
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
                points += stripProtocolFromStrings(entryHeaders.get(name)).equals(stripProtocolFromStrings(requestHeaders.get(name))) ? increment : 0;
            }
            // TODO handle missing headers and adjust score appropriately
        }

        if (request.method == HttpMethod.POST || request.method == HttpMethod.PUT) {
            if (!request.isBodyPresent() && !entryRequest.isBodyPresent()) {
                points += halfIncrement;
            } else if (request.isBodyPresent() && entryRequest.isBodyPresent()) {
                if (isSameBody(entryRequest, request)) {
                    points += increment;
                }
            }
        }

        return points;
    }

    static boolean isSameBody(ParsedRequest entryRequest, ParsedRequest request) {
        ByteSource requestBody = bodyToByteSource(request);
        ByteSource entryBody = bodyToByteSource(entryRequest);
        @Nullable String entryContentType = entryRequest.getFirstHeaderValue(HttpHeaders.CONTENT_TYPE);
        @Nullable String requestContentType = request.getFirstHeaderValue(HttpHeaders.CONTENT_TYPE);
        return isSameBody(entryBody, entryContentType, requestBody, requestContentType);
    }

    @Nullable
    private static Multiset<NameValuePair> parseIfWwwFormData(ByteSource body, @Nullable String contentType) {
        if (contentType != null) {
            try {
                MediaType mediaType = MediaType.parse(contentType);
                if (MediaType.FORM_DATA.withoutParameters().equals(mediaType.withoutParameters())) {
                    Charset charset = mediaType.charset().or(DEFAULT_FORM_DATA_CHARSET);
                    String queryString = body.asCharSource(charset).read();
                    List<Map.Entry<String, String>> params = URLEncodedUtils.parse(queryString, charset);
                    if (params == null) {
                        return ImmutableMultiset.of();
                    }
                    return params.stream().map(NameValuePair::from)
                            .collect(ImmutableMultiset.toImmutableMultiset());
                }
            } catch (RuntimeException | IOException ignore) {
            }
        }
        return null;
    }

    static boolean isSameBody(ByteSource entryBody, @Nullable String entryContentType, ByteSource requestBody, @Nullable String requestContentType) {
        if (entryBody.sizeIfKnown().equals(requestBody.sizeIfKnown())) {
            if (entryBody.sizeIfKnown().isPresent() && entryBody.sizeIfKnown().get().equals(0L)) {
                return true;
            }
        }
        @Nullable Multiset<NameValuePair> entryParams = parseIfWwwFormData(entryBody, entryContentType);
        if (entryParams != null) {
            @Nullable Multiset<NameValuePair> requestParams = parseIfWwwFormData(requestBody, requestContentType);
            if (requestParams != null) {
                return entryParams.equals(requestParams);
            }
        }
        try {
            if (requestBody.contentEquals(entryBody)) {
                return true;
            }
        } catch (IOException ignore) {
        }
        return false;
    }

    private static ByteSource bodyToByteSource(ParsedRequest request) {
        return new ByteSource() {
            @Override
            public InputStream openStream() throws IOException {
                return request.openBodyStream();
            }
        };
    }

    private static Multiset<String> stripProtocolFromStrings(Collection<String> strings) {
        return strings.stream().map(string -> string.replaceAll("^https?", "")).collect(ImmutableMultiset.toImmutableMultiset());
    }

    private static Multiset<Optional<String>> stripProtocolFromOptionals(Collection<Optional<String>> strings) {
        return strings.stream()
                .map(stringOpt -> {
                    if (stringOpt.isPresent()) {
                        String string = stringOpt.get();
                        return Optional.of(string.replaceAll("^https?", ""));
                    } else {
                        return stringOpt;
                    }
                }).collect(ImmutableMultiset.toImmutableMultiset());
    }

}
