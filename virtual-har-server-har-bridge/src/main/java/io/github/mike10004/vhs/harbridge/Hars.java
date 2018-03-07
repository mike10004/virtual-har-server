package io.github.mike10004.vhs.harbridge;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.google.common.primitives.Ints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Static utility methods relating to HAR data.
 */
public class Hars {

    private static final Logger log = LoggerFactory.getLogger(Hars.class);

    private static final CharMatcher BASE_64_ALPHABET = CharMatcher.inRange('A', 'Z')
            .or(CharMatcher.inRange('a', 'z'))
            .or(CharMatcher.inRange('0', '9'))
            .or(CharMatcher.anyOf("/+="));

    static boolean isAllBase64Alphabet(String text) {
        return BASE_64_ALPHABET.matchesAllOf(text);
    }

    /**
     * Determines (to the extent possible) whether some HAR content is base64-encoded.
     * Designed for arguments taken from a HAR content or post-data object.
     * @param contentType the content type (required)
     * @param text the text
     * @param encoding the encoding field (of HAR content)
     * @param size the size field
     * @return true if the text represents base-64-encoded data
     */
    public static boolean isBase64Encoded(String contentType, String text, @Nullable String encoding, @Nullable Long size) {
        if (!Encoding.isTextLike(contentType)) {
            return true;
        }
        if ("base64".equalsIgnoreCase(encoding)) {
            return true;
        }
        if (text.length() == 0) {
            return true; // because it won't matter, nothing will be decoded
        }
        if (!isAllBase64Alphabet(text)) {
            return false;
        }
        if (size != null) {
            /*
             * There are cases where the text is not base64-encoded and the content length
             * in bytes is greater than the text length, because some text characters encode
             * as more than one byte. But if the text length is greater than the reported byte
             * length, it's either an incorrectly-reported length value or malformed text, and
             * in either case we assume it's base64-encoding.
             */
            if (text.length() > size) {
                return true;
            }
        }
        return false;
    }

    /*
     * TODO figure out what the implications of using utf8 here would be
     *
     * I hate this, but it's likely the default encoding a user agent would select
     * if none was specified for a byte stream coming across the wire.
     */
    private static final Charset DEFAULT_HTML_CHARSET = StandardCharsets.ISO_8859_1;

    private enum MessageDirection {
        REQUEST, RESPONSE;

        public final Charset DEFAULT_CHARSET = DEFAULT_HTML_CHARSET;
    }

    /**
     * Encodes HAR response content data or request post-data as bytes.
     * If the data is base-64-encoded, then this just decodes it.
     * If the data is a string, then this encodes that in the charset specified by the content-type.
     * @param contentType the content type (required)
     * @param text the content or POST-data text
     * @param length the content length or size field
     * @param harContentEncoding the encoding field (from HAR content object)
     * @param comment the comment
     * @return a byte array containing encoded
     */
    @Nullable
    public static ByteSource translateRequestContent(String contentType,
                                          @Nullable String text,
                                          @Nullable Long length,
                                          @Nullable String harContentEncoding,
                                          @SuppressWarnings("unused") @Nullable String comment) {
        MessageDirection direction = MessageDirection.REQUEST;
        return getUncompressedContent(contentType, text, length, harContentEncoding, comment, direction);
    }

    public static class ResponseContentTranslation {
        public final ByteSource body;
        public final Function<Map.Entry<String, String>, Map.Entry<String, String>> headerMap;

        public ResponseContentTranslation(ByteSource body, Function<Entry<String, String>, Entry<String, String>> headerMap) {
            this.body = body;
            this.headerMap = headerMap;
        }

        public static ResponseContentTranslation withIdentityHeaderMap(ByteSource body) {
            return new ResponseContentTranslation(body, Function.identity());
        }

        public static Builder builder(ByteSource byteSource) {
            return new Builder(byteSource);
        }

        public static class Builder {
            private final ByteSource body;
            private Function<Map.Entry<String, String>, Map.Entry<String, String>> headerMap;

            public Builder(ByteSource body) {
                this.body = body;
                headerMap = Function.identity();
            }

            public Builder applyMap(Function<Map.Entry<String, String>, Map.Entry<String, String>> map) {
                this.headerMap = this.headerMap.andThen(map);
                return this;
            }

            private static final Function<Map.Entry<String, String>, Map.Entry<String, String>> SET_CONTENT_ENCODING_HEADER_TO_IDENTITY = entry -> {
                if (HttpHeaders.CONTENT_ENCODING.equalsIgnoreCase(entry.getKey())) {
                    String value = entry.getValue();
                    if (value == null || value.isEmpty() || value.equalsIgnoreCase("identity")) {
                        return entry;
                    }
                    return new SimpleImmutableEntry<>(entry.getKey(), "identity");
                }
                return entry;
            };

            public Builder setContentEncodingHeaderToIdentity() {
                return applyMap(SET_CONTENT_ENCODING_HEADER_TO_IDENTITY);
            }

            public ResponseContentTranslation build() {
                return new ResponseContentTranslation(body, headerMap);
            }
        }
    }

    private static final Splitter QUALITY_VALUE_SPLITTER = Splitter.on(';').trimResults().omitEmptyStrings();

    static class WeightedEncoding {

        public final String encoding;
        public final BigDecimal weight;

        public WeightedEncoding(String encoding, BigDecimal weight) {
            this.encoding = requireNonNull(encoding);
            this.weight = requireNonNull(weight);
        }

        /**
         * Parses a string like `gzip;q=1.0` into an instance.
         * @param token the string token
         * @return the weighted encoding instance
         */
        public static WeightedEncoding parse(String token) {
            token = Strings.nullToEmpty(token).trim();
            if (token.isEmpty()) {
                throw new IllegalArgumentException("string has no usable content");
            }
            Iterator<String> it = QUALITY_VALUE_SPLITTER.split(token).iterator();
            String encoding = it.next();
            @Nullable BigDecimal qValue = null;
            if (it.hasNext()) {
                String qStr = it.next();
                qValue = parseQualityValue(qStr);
            }
            if (qValue == null) {
                qValue = BigDecimal.ONE; // "When not present, the default value is 1." <-- https://developer.mozilla.org/en-US/docs/Glossary/Quality_values
            }
            return new WeightedEncoding(encoding, qValue);
        }

        @Nullable
        static BigDecimal parseQualityValue(@Nullable String qStr) {
            if (qStr != null) {
                String[] parts = qStr.split("\\s*=\\s*", 2);
                if (parts.length == 2) {
                    String valueStr = parts[1];
                    return valueOf(valueStr);
                }
            }
            return null;
        }

        private static BigDecimal valueOf(String decimal) {
            if ("1.0".equals(decimal) || decimal.matches("^1(\\.0+)?$")) {
                return BigDecimal.ONE;
            }
            if ("0".equals(decimal) || "0.0".equals(decimal) || decimal.matches("^0(\\.0+)?$")) {
                return BigDecimal.ZERO;
            }
            return new BigDecimal(decimal);
        }

        public boolean isPositive() {
            return weight.compareTo(BigDecimal.ZERO) > 0;
        }

        public AcceptDecision accepts(String encoding) {
            if (this.encoding.equals(encoding)) {
                return isPositive() ? AcceptDecision.ACCEPT : AcceptDecision.REJECT;
            }
            return AcceptDecision.INDETERMINATE;
        }
    }

    enum AcceptDecision {
        ACCEPT, REJECT, INDETERMINATE
    }

    @VisibleForTesting
    static ImmutableList<WeightedEncoding> parseAcceptedEncodings(@Nullable String acceptEncodingHeaderValue) {
        acceptEncodingHeaderValue = Strings.nullToEmpty(acceptEncodingHeaderValue).trim();
        if (acceptEncodingHeaderValue.isEmpty()) {
            return ImmutableList.of();
        }
        List<String> weightedEncodings = HttpContentCodecs.parseEncodings(acceptEncodingHeaderValue);
        return weightedEncodings.stream()
                .map(WeightedEncoding::parse)
                .collect(ImmutableList.toImmutableList());

    }

    /**
     * Determines whether the unencoded (i.e. uncompressed) response is to be encoded using the
     * original response encoding, based on whether the new client accepts it. This ignores some
     * parts of the HTTP spec, like how to respond if the client explicitly rejects the 'identity' encoding,
     * which would be very unlikely to occur in practice. For each response content encoding in the given
     * list, we check that the client's accept-encoding specification explicitly accepts it or that
     * it is covered under a wildcard acceptance.
     * @param acceptEncodingHeaderValue the client's Accept-Encoding header value
     * @param parsedResponseContentEncodings the encodings originally applied to the content in the response captured in the HAR
     * @return true iff the client specifies that it can accept the encodings of the original response
     */
    @VisibleForTesting
    static boolean canServeOriginalResponseContentEncoding(List<String> parsedResponseContentEncodings, @Nullable String acceptEncodingHeaderValue) {
        List<WeightedEncoding> acceptsWeighted = parseAcceptedEncodings(acceptEncodingHeaderValue);
        return canServeOriginalResponseContentEncoding(parsedResponseContentEncodings, acceptsWeighted);
    }

    @VisibleForTesting
    static boolean canServeOriginalResponseContentEncoding(List<String> parsedResponseContentEncodingsList, List<WeightedEncoding> acceptsWeighted) {
        Set<String> parsedResponseContentEncodings = ImmutableSet.copyOf(parsedResponseContentEncodingsList);
        if (parsedResponseContentEncodings.isEmpty() || onlyContains(parsedResponseContentEncodings, HttpContentCodecs.CONTENT_ENCODING_IDENTITY)) {
            return true;
        }
        if (acceptsWeighted.isEmpty()) {
            return false;
        }
        for (String encoding : parsedResponseContentEncodings) {
            if (HttpContentCodecs.CONTENT_ENCODING_IDENTITY.equals(encoding)) {
                continue;
            }
            boolean canServeSingle = canServeResponseContentEncoding(encoding, acceptsWeighted);
            if (!canServeSingle) {
                return false;
            }
        }
        // if we're here, we know the client can accept each content encoding specified
        return true;
    }

    static boolean canServeResponseContentEncoding(String encoding, List<WeightedEncoding> acceptsWeighted) {
        WeightedEncoding star = null;
        for (WeightedEncoding we : acceptsWeighted) {
            AcceptDecision decision = we.accepts(encoding);
            if (decision == AcceptDecision.ACCEPT) {
                return true;
            }
            if (decision == AcceptDecision.REJECT) {
                return false;
            }
            if ("*".equals(we.encoding)) {
                star = we;
            }
        }
        // invariant: `encoding` never mentioned in list of weighted encodings
        if (star != null) {
            return star.isPositive();
        }
        return false;
    }

    static ImmutableList<String> parseContentEncodings(@Nullable String contentEncodingHeaderValue) {
        return HttpContentCodecs.parseEncodings(contentEncodingHeaderValue);
    }

    static <T, U extends T> boolean onlyContains(Set<T> set, U element) {
        return set.size() == 1 && set.contains(element);
    }

    /**
     * Translates the content of a captured response in a HAR to a format
     * usable for transmitting as a new HTTP response.
     * @param request the new HTTP request for which the HAR response is to be reconstructed
     * @param contentType content MIME type
     * @param text data
     * @param bodySize Size of the received response body in bytes.
     *                 Set to zero in case of responses coming from the cache (304).
     *                 Set to -1 if the info is not available.
     * @param contentSize Length of the returned content in bytes.
     *                    Should be equal to response.bodySize if there is no compression
     *                    and bigger when the content has been compressed.
     * @param contentEncodingHeaderValue value of the Content-Encoding header
     * @param harContentEncoding value of the HAR content "encoding" field
     * @param comment HAR content comment
     * @return translated data
     */
    public static ResponseContentTranslation translateResponseContent(ParsedRequest request,
                                                      String contentType,
                                                      @Nullable String text,
                                                      @Nullable Long bodySize,
                                                      @Nullable Long contentSize,
                                                      @Nullable String contentEncodingHeaderValue,
                                                      @Nullable String harContentEncoding,
                                                      @SuppressWarnings("unused") @Nullable String comment) {
        MessageDirection direction = MessageDirection.RESPONSE;
        @Nullable ByteSource uncompressed = getUncompressedContent(contentType, text, bodySize, harContentEncoding, comment, direction);
        if (uncompressed == null) {
            return ResponseContentTranslation.withIdentityHeaderMap(null);
        }
        ResponseContentTranslation uncompressedReturnValue = ResponseContentTranslation.builder(uncompressed).setContentEncodingHeaderToIdentity().build();
        if (bodySize == null || contentSize == null || Objects.equals(bodySize, contentSize)) {
            return uncompressedReturnValue;
        }
        if (contentSize.longValue() < bodySize.longValue()) {
            log.debug("contentSize {} < bodySize {}; this either violates HAR spec or compression added to byte count", contentSize, bodySize);
        }
        if (contentEncodingHeaderValue == null) {
            // if content-encoding header value is null, we'll assume gzip for now;
            // TODO try to determine compression type by sniffing data
            contentEncodingHeaderValue = HttpContentCodecs.CONTENT_ENCODING_GZIP;
        }
        String acceptEncodingHeaderValue = request.getFirstHeaderValue(HttpHeaders.ACCEPT_ENCODING);
        List<String> encodings = parseContentEncodings(contentEncodingHeaderValue);
        if (!canServeOriginalResponseContentEncoding(encodings, acceptEncodingHeaderValue)) {
            return uncompressedReturnValue;
        }
        // TODO delay buffering of the data by composing compressing byte sinks into a byte source
        byte[] data;
        try {
            data = uncompressed.read();
        } catch (IOException e) {
            log.warn("failed to read uncompressed data; returning as-is", e);
            return uncompressedReturnValue;
        }
        boolean anyChanges = false;
        for (String encoding : encodings) {
            HttpContentCodec compressor = HttpContentCodecs.getCodec(encoding);
            if (compressor == null) {
                log.warn("failed to compress data because {} is not supported; this will likely flummox some user agents", encoding);
                return uncompressedReturnValue;
            }
            try {
                data = compressor.compress(data);
                anyChanges = true;
            } catch (IOException e) {
                log.warn("failed to compress data with " + encoding + "; returning uncompressed", e);
                return uncompressedReturnValue;
            }
        }
        return ResponseContentTranslation.builder(anyChanges ? ByteSource.wrap(data) : uncompressed).build();
    }

    /**
     * Gets the uncompressed, unencoded data that constitutes the body of a response captured
     * in a HAR entry, given the various HAR response and HAR content fields that describe it.
     * @return a byte source that supplies a stream of the response body as unencoded, uncompressed bytes
     */
    @Nullable
    private static ByteSource getUncompressedContent(String contentType,
                                                     @Nullable String text,
                                                     @Nullable Long length,
                                                     @Nullable String harContentEncoding,
                                                     @SuppressWarnings("unused") @Nullable String comment,
                                                     MessageDirection direction) {
        if (text == null) {
            return null;
        }
        requireNonNull(contentType, "content type must be non-null");
        if (length != null) {
            //noinspection ResultOfMethodCallIgnored
            Ints.checkedCast(length); // argument check
        }
        boolean base64 = isBase64Encoded(contentType, text, harContentEncoding, length);
        if (base64) {
            return BaseEncoding.base64().decodingSource(CharSource.wrap(text));
        } else {
            Charset charset = direction.DEFAULT_CHARSET;
            try {
                charset = MediaType.parse(contentType).charset().or(charset);
            } catch (RuntimeException ignore) {
            }
            return CharSource.wrap(text).asByteSource(charset);
        }
    }

}
