package io.github.mike10004.vhs.harbridge;

import com.google.common.io.ByteSource;
import com.google.common.net.MediaType;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Interface representing response data.
 */
public interface HarResponseData {

    Stream<Map.Entry<String, String>> headers();
    MediaType getContentType();
    ByteSource getBody();

    static HarResponseData of(@Nullable Supplier<Stream<Map.Entry<String, String>>> headers, @Nullable MediaType contentType, @Nullable ByteSource body) {
        return new HarResponseDataImpl(headers, contentType, body);
    }

}
