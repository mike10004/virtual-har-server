package io.github.mike10004.vhs;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.io.ByteSource;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.github.mike10004.vhs.harbridge.HttpMethod;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import io.github.mike10004.vhs.repackaged.org.apache.http.client.utils.URLEncodedUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;

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
        checkArgument(increment % 2 == 0, "increment must be even: %s", increment);
        this.halfIncrement = this.increment / 2;
    }

    protected int rateQuerySameness(@Nullable Multimap<String, Optional<String>> entryQuery, @Nullable Multimap<String, Optional<String>> requestQuery) {
        int points = 0;
        if (entryQuery == null && requestQuery == null) {
            points += increment;
        } else {
            //noinspection ConstantConditions
            entryQuery = MoreObjects.firstNonNull(entryQuery, ImmutableMultimap.of());
            //noinspection ConstantConditions
            requestQuery = MoreObjects.firstNonNull(requestQuery, ImmutableMultimap.of());
            for (String name : requestQuery.keySet()) {
                if (!entryQuery.containsKey(name)) {
                    points -= halfIncrement;
                } else {
                    Multiset<Optional<String>> entryParamValues = stripProtocolFromOptionals(entryQuery.get(name));
                    Multiset<Optional<String>> requestParamValues = stripProtocolFromOptionals(requestQuery.get(name));
                    if (entryParamValues.equals(requestParamValues)) {
                        points += increment;
                    }
                }
            }

            for (String name : entryQuery.keySet()) {
                if (!requestQuery.containsKey(name)) {
                    points -= halfIncrement;
                }
            }
        }
        return Math.max(0, points);
    }

    @Override
    public int rate(ParsedRequest entryRequest, ParsedRequest request) {
        // String name;
        URI requestUrl = request.url;
        Multimap<String, String> requestHeaders = request.indexedHeaders;
        // method, host and pathname must match
        if (requestUrl == null) {
            return 0;
        }
        if (entryRequest.method != request.method) {
            return 0;
        }
        if (!entryRequest.url.getHost().equals(requestUrl.getHost())) {
            return 0;
        }
        if (!entryRequest.url.getPath().equals(requestUrl.getPath())) {
            return 0;
        }
        int points = increment; // One point for matching above requirements
        points += rateQuerySameness(entryRequest.query, request.query);

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
                points += rateBodySameness(entryRequest, request);
            }
        }

        return points;
    }

    protected int rateBodySameness(ParsedRequest entryRequest, ParsedRequest request) {
        ByteSource requestBody = bodyToByteSource(request);
        ByteSource entryBody = bodyToByteSource(entryRequest);
        @Nullable String entryContentType = entryRequest.getFirstHeaderValue(HttpHeaders.CONTENT_TYPE);
        @Nullable String requestContentType = request.getFirstHeaderValue(HttpHeaders.CONTENT_TYPE);
        return rateBodySameness(entryBody, entryContentType, requestBody, requestContentType);
    }

    @Nullable
    private static Multimap<String, Optional<String>> parseIfWwwFormData(ByteSource body, @Nullable String contentType) {
        if (contentType != null) {
            try {
                MediaType mediaType = MediaType.parse(contentType);
                if (MediaType.FORM_DATA.withoutParameters().equals(mediaType.withoutParameters())) {
                    Charset charset = mediaType.charset().or(DEFAULT_FORM_DATA_CHARSET);
                    String queryString = body.asCharSource(charset).read();
                    List<Map.Entry<String, String>> params = URLEncodedUtils.parse(queryString, charset);
                    if (params != null) {
                        Multimap<String, Optional<String>> mm = ArrayListMultimap.create();
                        params.stream()
                                .map(p -> new AbstractMap.SimpleImmutableEntry<>(p.getKey(), Optional.ofNullable(p.getValue())))
                                .forEach(p -> mm.put(p.getKey(), p.getValue()));
                        return mm;
                    } else {
                        return ImmutableMultimap.of();
                    }
                }
            } catch (RuntimeException | IOException ignore) {
            }
        }
        return null;
    }

    int rateBodySameness(ByteSource entryBody, @Nullable String entryContentType, ByteSource requestBody, @Nullable String requestContentType) {
        if (entryBody.sizeIfKnown().equals(requestBody.sizeIfKnown())) {
            if (entryBody.sizeIfKnown().isPresent() && entryBody.sizeIfKnown().get().equals(0L)) {
                return increment;
            }
        }
        @Nullable Multimap<String, Optional<String>> entryParams = parseIfWwwFormData(entryBody, entryContentType);
        if (entryParams != null) { // then it's form data
            @Nullable Multimap<String, Optional<String>> requestParams = parseIfWwwFormData(requestBody, requestContentType);
            if (requestParams != null) {
                return rateQuerySameness(entryParams, requestParams);
            }
        }
        try {
            return entryBody.contentEquals(requestBody) ? increment : 0;
        } catch (IOException e) {
            return 0;
        }
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
