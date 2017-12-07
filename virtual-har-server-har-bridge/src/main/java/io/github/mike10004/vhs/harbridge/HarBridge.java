package io.github.mike10004.vhs.harbridge;

import com.google.common.net.MediaType;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 *
 * @param <E> HAR entry class
 */
public interface HarBridge<E> {

    String getRequestMethod(E entry);
    String getRequestUrl(E entry);
    Stream<Map.Entry<String, String>> getRequestHeaders(E entry);
    Stream<Map.Entry<String, String>> getResponseHeaders(E entry);
    @Nullable byte[] getRequestPostData(E entry) throws IOException;
    byte[] getResponseBody(E entry) throws IOException;
    MediaType getResponseContentType(E entry);
    int getResponseStatus(E entry);

    class MissingHarFieldException extends IllegalArgumentException {
        public MissingHarFieldException(String s) {
            super(s);
        }
    }
}
