package io.github.mike10004.vhs;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.google.common.primitives.Ints;
import edu.umass.cs.benchlab.har.HarContent;
import edu.umass.cs.benchlab.har.HarPostData;
import edu.umass.cs.benchlab.har.HarPostDataParams;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;

public class Hars {

    private static final Logger log = LoggerFactory.getLogger(Hars.class);

    private Hars() {}

    @Nullable
    public static byte[] translate(HarContent content) {
        if (content == null) {
            return null;
        }
        Long length = content.getSize();
        return translate(content.getMimeType(), null, content.getText(), length, null, content.getComment(), MessageDirection.RESPONSE);
    }

    @Nullable
    public static byte[] translate(HarPostData content, Multimap<String, String> requestHeaders) {
        if (content == null) {
            return null;
        }
        @Nullable Long length = requestHeaders.get(HttpHeaders.CONTENT_LENGTH).stream().findFirst().map(Long::valueOf).orElse(null);
        return translate(content.getMimeType(), content.getParams(), content.getText(), length, null, content.getComment(), MessageDirection.REQUEST);
    }

    public enum MessageDirection {
        REQUEST, RESPONSE;

        public final Charset DEFAULT_CHARSET = INTERNET_DEFAULT_CHARSET;
    }

    @Nullable
    static byte[] translate(String contentType, @Nullable HarPostDataParams params, @Nullable String text, @Nullable Long length,
                            @SuppressWarnings("SameParameterValue") @Nullable String encoding,
                            @SuppressWarnings("unused") @Nullable String comment, MessageDirection direction) {
        if (params != null && !params.getPostDataParams().isEmpty()) {
            throw new UnsupportedOperationException("not yet implemented: params -> body translation");
        }
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

    static final Charset INTERNET_DEFAULT_CHARSET = StandardCharsets.ISO_8859_1; // I hate this

    private static final CharMatcher BASE_64_ALPHABET = CharMatcher.inRange('A', 'Z')
            .or(CharMatcher.inRange('a', 'z'))
            .or(CharMatcher.inRange('0', '9'))
            .or(CharMatcher.anyOf("/+="));

    static boolean isAllBase64Alphabet(String text) {
        return BASE_64_ALPHABET.matchesAllOf(text);
    }

    static boolean isBase64Encoded(String contentType, String text, @Nullable String encoding, @Nullable Long size) {
        if (!isTextLike(contentType)) {
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

    @Nullable
    public static Charset parseCharset(@Nullable String contentType) {
        if (contentType != null) {
            try {
                return MediaType.parse(contentType).charset().orNull();
            } catch (RuntimeException ignore) {
            }
        }
        return null;
    }

    static boolean isTextLike(@Nullable String contentType) {
        if (contentType == null) {
            return false;
        }
        MediaType mime = null;
        try {
            mime = MediaType.parse(contentType).withoutParameters();
        } catch (IllegalArgumentException e) {
            log.debug("failed to parse mime type from {}", StringUtils.abbreviate(contentType, 128));
        }
        if (mime != null) {
            if (mime.is(MediaType.ANY_TEXT_TYPE)) {
                return true;
            }
            if (TEXT_LIKE_TYPES.contains(mime)) {
                return true;
            }
        }
        return false;
    }

    private static final Set<MediaType> TEXT_LIKE_TYPES = ImmutableSet.of(MediaType.JAVASCRIPT_UTF_8.withoutParameters(), MediaType.JSON_UTF_8.withoutParameters(), MediaType.CSS_UTF_8);
}
