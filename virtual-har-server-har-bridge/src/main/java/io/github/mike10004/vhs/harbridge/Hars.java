package io.github.mike10004.vhs.harbridge;

import com.google.common.base.CharMatcher;
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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Function;

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

    /**
     *
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
            log.warn("contentSize {} < bodySize {} but this violates HAR spec", contentSize, bodySize);
            return uncompressedReturnValue;
        }
        // invariant here: bodySize < contentSize, implying data must be compressed
        if (contentEncodingHeaderValue == null) {
            // if content-encoding header value is null, we'll assume gzip for now;
            // TODO try to determine compression type by sniffing data
            contentEncodingHeaderValue = HttpContentCodecs.CONTENT_ENCODING_GZIP;
        }
        List<String> encodings = HttpContentCodecs.parseEncodings(contentEncodingHeaderValue);
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
        Objects.requireNonNull(contentType, "content type must be non-null");
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
                charset = MediaType.parse(contentType).charset().or(direction.DEFAULT_CHARSET);
            } catch (RuntimeException ignore) {
            }
            return CharSource.wrap(text).asByteSource(charset);
        }
    }

}
