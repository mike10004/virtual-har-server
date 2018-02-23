package io.github.mike10004.vhs.harbridge;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.net.MediaType;
import com.google.common.primitives.Ints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    public enum MessageDirection {
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
    public static byte[] translateRequestContent(String contentType,
                                          @Nullable String text,
                                          @Nullable Long length,
                                          @Nullable String harContentEncoding,
                                          @SuppressWarnings("unused") @Nullable String comment) {
        MessageDirection direction = MessageDirection.REQUEST;
        return translateContent(contentType, text, length, harContentEncoding, comment, direction);
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
    @Nullable
    public static byte[] translateResponseContent(String contentType,
                                          @Nullable String text,
                                          @Nullable Long bodySize,
                                          @Nullable Long contentSize,
                                          @Nullable String contentEncodingHeaderValue,
                                          @Nullable String harContentEncoding,
                                          @SuppressWarnings("unused") @Nullable String comment) {
        MessageDirection direction = MessageDirection.RESPONSE;
        @Nullable byte[] uncompressed = translateContent(contentType, text, bodySize, harContentEncoding, comment, direction);
        if (uncompressed == null) {
            return null;
        }
        if (bodySize == null || contentSize == null || Objects.equals(bodySize, contentSize)) {
            return uncompressed;
        }
        if (contentSize.longValue() < bodySize.longValue()) {
            log.warn("contentSize {} < bodySize {} but this violates HAR spec", contentSize, bodySize);
            return uncompressed;
        }
        // invariant here: bodySize < contentSize, implying data must be compressed
        if (contentEncodingHeaderValue == null) {
            // if content-encoding header value is null, we'll assume gzip for now;
            // TODO try to determine compression type by sniffing data
            contentEncodingHeaderValue = HttpContentCodecs.CONTENT_ENCODING_GZIP;
        }
        List<String> encodings = HttpContentCodecs.parseEncodings(contentEncodingHeaderValue);
        byte[] data = uncompressed;
        for (String encoding : encodings) {
            HttpContentCodec compressor = HttpContentCodecs.getCodec(encoding);
            if (compressor == null) {
                log.warn("failed to compress data because {} is not supported; this will likely flummox some user agents", encoding);
                return uncompressed;
            }
            try {
                data = compressor.compress(data);
            } catch (IOException e) {
                log.warn("failed to compress data with " + encoding + "; returning uncompressed", e);
                return uncompressed;
            }
        }
        return data;
    }

    @Nullable
    private static byte[] translateContent(String contentType,
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
            return Base64.getDecoder().decode(text);
        } else {
            Charset charset = direction.DEFAULT_CHARSET;
            try {
                charset = MediaType.parse(contentType).charset().or(direction.DEFAULT_CHARSET);
            } catch (RuntimeException ignore) {
            }
            return text.getBytes(charset);
        }
    }

}
