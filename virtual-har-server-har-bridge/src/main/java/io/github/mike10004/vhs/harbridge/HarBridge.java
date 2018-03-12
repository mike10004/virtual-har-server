package io.github.mike10004.vhs.harbridge;

import com.google.common.io.ByteSource;
import com.google.common.net.MediaType;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Interface that defines the methods needed to support a HAR library.
 * @param <E> HAR entry class
 */
public interface HarBridge<E> {

    /**
     * Content type value substituted when no actual content-type value is contained
     * in the HAR content object.
     */
    MediaType CONTENT_TYPE_SUBSTITUTE_VALUE = MediaType.OCTET_STREAM;

    /**
     * Gets the request method as a string.
     * @param entry the HAR entry
     * @return the request method
     */
    String getRequestMethod(E entry);

    /**
     * Gets the URL the request was sent to, as a string.
     * @param entry the HAR entry
     * @return the request URL
     */
    String getRequestUrl(E entry);

    /**
     * Streams the request headers.
     * @param entry the HAR entry
     * @return a stream of the request headers
     */
    Stream<Map.Entry<String, String>> getRequestHeaders(E entry);

    @Nullable
    ByteSource getRequestPostData(E entry) throws IOException;

    /**
     * Gets the HTTP response status code. Usually this is a value from 100 to 599.
     * In some degenerate cases, such as a HAR collected from a user agent where
     * requests were blocked by the client (as by an ad blocker), the response
     * status may not have been set and might be a value like -1 or 0.
     * @param entry the HAR entry
     * @return the response status
     */
    int getResponseStatus(E entry);

    /**
     * Gets an object representing the HAR response data.
     * @param request the request
     * @param entry the HAR entry
     * @return the response data
     * @throws IOException on I/O error
     */
    ResponseData getResponseData(ParsedRequest request, E entry) throws IOException;

    /**
     * Class representing response data.
     */
    class ResponseData {

        private final Supplier<Stream<Map.Entry<String, String>>> headers;
        public final MediaType contentType;
        public final ByteSource body;

        /**
         *
         * @param headers headers; if null, empty stream will be used
         * @param contentType content-type; if null, {@code application/octet-stream} will be used
         * @param body body; if null, empty byte source will be used
         */
        public ResponseData(@Nullable Supplier<Stream<Entry<String, String>>> headers, @Nullable MediaType contentType, @Nullable ByteSource body) {
            this.headers = headers == null ? Stream::empty : headers;
            this.contentType = contentType == null ? CONTENT_TYPE_SUBSTITUTE_VALUE : contentType;
            this.body = body == null ? ByteSource.empty() : body;
        }

        public Stream<Map.Entry<String, String>> headers() {
            return headers.get();
        }
    }
}
