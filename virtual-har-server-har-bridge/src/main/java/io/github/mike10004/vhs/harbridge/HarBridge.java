package io.github.mike10004.vhs.harbridge;

import com.google.common.io.ByteSource;
import com.google.common.net.MediaType;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

/**
 * Interface that defines the methods needed to support a HAR library.
 * @param <E> HAR entry class
 */
public interface HarBridge<E> {

    String getRequestMethod(E entry);

    String getRequestUrl(E entry);

    Stream<Map.Entry<String, String>> getRequestHeaders(E entry);

    @Nullable
    ByteSource getRequestPostData(E entry) throws IOException;

    int getResponseStatus(E entry);

    ResponseData getResponseData(ParsedRequest request, E entry) throws IOException;

    class MissingHarFieldException extends IllegalArgumentException {
        public MissingHarFieldException(String s) {
            super(s);
        }
    }

    class ResponseData {
        public final Stream<Map.Entry<String, String>> headers;
        public final MediaType contentType;
        public final ByteSource body;

        public ResponseData(@Nullable Stream<Entry<String, String>> headers, MediaType contentType, ByteSource body) {
            this.headers = headers == null ? Stream.empty() : headers;
            this.contentType = contentType == null ? MediaType.OCTET_STREAM : contentType;
            this.body = body == null ? ByteSource.empty() : body;
        }
    }
}
