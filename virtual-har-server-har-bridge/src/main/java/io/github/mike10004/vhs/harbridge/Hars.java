package io.github.mike10004.vhs.harbridge;

import com.google.common.base.CharMatcher;
import com.google.common.net.MediaType;
import com.google.common.primitives.Ints;

import javax.annotation.Nullable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * Static utility methods relating to HAR data.
 */
public class Hars {

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
    static boolean isBase64Encoded(String contentType, String text, @Nullable String encoding, @Nullable Long size) {
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
     * Encodes HAR content or post-data as bytes. If the data is base-64-encoded, then this
     * just decodes it. If the data is a string, then this encodes that in the charset
     * specified by the content-type.
     * @param contentType the content type (required)
     * @param text the content or POST-data text
     * @param length the content length or size field
     * @param encoding the encoding field (from HAR content object)
     * @param comment the comment
     * @param direction the message direction (HTTP request or response)
     * @return a byte array containing encoded
     */
    @Nullable
    public static byte[] translate(String contentType, @Nullable String text, @Nullable Long length,
                            @SuppressWarnings("SameParameterValue") @Nullable String encoding,
                            @SuppressWarnings("unused") @Nullable String comment, MessageDirection direction) {
        if (text == null) {
            return null;
        }
        Objects.requireNonNull(contentType, "content type must be non-null");
        if (length != null) {
            //noinspection ResultOfMethodCallIgnored
            Ints.checkedCast(length); // argument check
        }
        boolean base64 = isBase64Encoded(contentType, text, encoding, length);
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
