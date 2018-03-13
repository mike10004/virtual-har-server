package io.github.mike10004.vhs.harbridge;

import com.google.common.collect.ImmutableSet;
import com.google.common.net.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.nio.charset.Charset;

class Encoding {

    private static final Logger log = LoggerFactory.getLogger(Encoding.class);

    private Encoding() {}

    public static boolean isTextLike(@Nullable String contentType) {
        if (contentType == null) {
            return false;
        }
        MediaType mime = null;
        try {
            mime = MediaType.parse(contentType).withoutParameters();
        } catch (IllegalArgumentException e) {
            log.debug("failed to parse mime type from {}", contentType);
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

    private static final ImmutableSet<MediaType> TEXT_LIKE_TYPES = ImmutableSet.<MediaType>builder()
            .add(MediaType.JAVASCRIPT_UTF_8.withoutParameters())
            .add(MediaType.JSON_UTF_8.withoutParameters())
            .add(MediaType.CSS_UTF_8.withoutParameters())
            .build();
}
