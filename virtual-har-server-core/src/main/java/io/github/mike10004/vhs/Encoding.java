package io.github.mike10004.vhs;

import com.google.common.collect.ImmutableSet;
import com.google.common.net.MediaType;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.nio.charset.Charset;
import java.util.Set;

public class Encoding {

    private static final Logger log = LoggerFactory.getLogger(Hars.class);

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

    public static boolean isTextLike(@Nullable String contentType) {
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
