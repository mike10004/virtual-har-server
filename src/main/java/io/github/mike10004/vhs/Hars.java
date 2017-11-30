package io.github.mike10004.vhs;

import com.google.common.net.MediaType;
import com.google.common.primitives.Ints;

import javax.annotation.Nullable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Hars {
    public enum MessageDirection {
        REQUEST, RESPONSE;

        public final Charset DEFAULT_CHARSET = DEFAULT_HTTP_CHARSET;
    }

    @Nullable
    static byte[] translate(String contentType, @Nullable String text, @Nullable Long length,
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
        boolean base64 = Encoding.isBase64Encoded(contentType, text, encoding, length);
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
