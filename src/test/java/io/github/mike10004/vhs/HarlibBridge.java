package io.github.mike10004.vhs;

import com.google.common.collect.Multimap;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.google.common.primitives.Ints;
import edu.umass.cs.benchlab.har.HarContent;
import edu.umass.cs.benchlab.har.HarPostData;
import edu.umass.cs.benchlab.har.HarPostDataParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

public class HarlibBridge {
    private static final Logger log = LoggerFactory.getLogger(Hars.class);

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

        public final Charset DEFAULT_CHARSET = DEFAULT_HTTP_CHARSET;
    }

    @Nullable
    static byte[] translate(String contentType, @Nullable HarPostDataParams params, @Nullable String text, @Nullable Long length,
                            @SuppressWarnings("SameParameterValue") @Nullable String encoding,
                            @SuppressWarnings("unused") @Nullable String comment, MessageDirection direction) {
        if (params != null && !params.getPostDataParams().isEmpty()) {
            // application/x-www-form-urlencoded
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
        boolean base64 = Hars.isBase64Encoded(contentType, text, encoding, length);
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

    static final Charset DEFAULT_HTTP_CHARSET = StandardCharsets.ISO_8859_1; // I hate this

}
