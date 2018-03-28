package io.github.mike10004.vhs.harbridge;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteSource;
import com.google.common.net.MediaType;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings("Duplicates")
public class MultipartFormData {

    private static final int HTTP_ERROR_BAD_REQUEST = 400;
    static final Charset DEFAULT_MULTIPART_FORM_DATA_ENCODING = StandardCharsets.UTF_8;
    static final Charset URLENCODED_FORM_DATA_DEFAULT_ENCODING = StandardCharsets.UTF_8;
    private MultipartFormData() {}

    public interface FormDataParser {
        /**
         * Parse multipart/form-data.
         * See https://www.iana.org/assignments/media-types/multipart/form-data.
         * @param contentType content type (must have boundary parameter, should probably be {@code multipart/form-data}
         * @param data the data
         * @return
         * @throws BadMultipartFormDataException
         * @throws NanohttpdFormDataParser.RuntimeIOException
         */
        List<FormDataPart> decodeMultipartFormData(MediaType contentType, byte[] data) throws BadMultipartFormDataException, NanohttpdFormDataParser.RuntimeIOException;
    }

    public static FormDataParser getParser() {
        return new NanohttpdFormDataParser();
    }

    public static class FormDataPart {

        public final ImmutableMultimap<String, String> headers;

        @Nullable
        ContentDisposition contentDisposition;

        @Nullable
        public final TypedContent file;

        public FormDataPart(Multimap<String, String> headers, @Nullable ContentDisposition contentDisposition, @Nullable TypedContent file) {
            this.headers = ImmutableMultimap.copyOf(headers);
            this.contentDisposition = contentDisposition;
            this.file = file;
        }

        @Override
        public String toString() {
            return "FormDataPart{" +
                    "headers.size=" + headers.size() +
                    ", contentDisposition=" + quote(contentDisposition) +
                    ", file=" + file +
                    '}';
        }

        @Nullable
        private static String quote(@Nullable Object value) {
            if (value == null) {
                return null;
            }
            return String.format("\"%s\"", StringEscapeUtils.escapeJava(StringUtils.abbreviateMiddle(value.toString(), "[...]", 64)));
        }
    }

    static class BadMultipartFormDataException extends RuntimeException {

        public static final int STATUS_CODE = HTTP_ERROR_BAD_REQUEST;

        @SuppressWarnings("unused")
        public BadMultipartFormDataException(String message) {
            super(message);
        }

        @SuppressWarnings("unused")
        public BadMultipartFormDataException(String message, Throwable cause) {
            super(message, cause);
        }

        @SuppressWarnings("unused")
        public BadMultipartFormDataException(Throwable cause) {
            super(cause);
        }
    }

    static class MalformedMultipartFormDataException extends BadMultipartFormDataException {

        public MalformedMultipartFormDataException(String message) {
            super(message);
        }
    }

    static String getBoundaryOrDie(MediaType contentType) throws BadMultipartFormDataException {
        @Nullable String boundary = contentType.parameters().entries().stream()
                .filter(entry -> "boundary".equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst().orElse(null);
        if (boundary == null) {
            throw new BadMultipartFormDataException("'boundary' parameter not present in Content-Type: " + contentType.toString());
        }
        return boundary;
    }

    static final Charset BOUNDARY_ENCODING = StandardCharsets.US_ASCII; // assume header value contains only ASCII

    /**
     * Find the byte positions where multipart boundaries start. This reads
     * a large block at a time and uses a temporary buffer to optimize
     * (memory mapped) file access.
     */
    static int[] getBoundaryPositions(ByteBuffer b, byte[] boundary) {
        int[] res = new int[0];
        if (b.remaining() < boundary.length) {
            return res;
        }

        int search_window_pos = 0;
        byte[] search_window = new byte[4 * 1024 + boundary.length];

        int first_fill = (b.remaining() < search_window.length) ? b.remaining() : search_window.length;
        b.get(search_window, 0, first_fill);
        int new_bytes = first_fill - boundary.length;

        do {
            // Search the search_window
            for (int j = 0; j < new_bytes; j++) {
                for (int i = 0; i < boundary.length; i++) {
                    if (search_window[j + i] != boundary[i])
                        break;
                    if (i == boundary.length - 1) {
                        // Match found, add it to results
                        int[] new_res = new int[res.length + 1];
                        System.arraycopy(res, 0, new_res, 0, res.length);
                        new_res[res.length] = search_window_pos + j;
                        res = new_res;
                    }
                }
            }
            search_window_pos += new_bytes;

            // Copy the end of the buffer to the start
            System.arraycopy(search_window, search_window.length - boundary.length, search_window, 0, boundary.length);

            // Refill search_window
            new_bytes = search_window.length - boundary.length;
            new_bytes = (b.remaining() < new_bytes) ? b.remaining() : new_bytes;
            b.get(search_window, boundary.length, new_bytes);
        } while (new_bytes > 0);
        return res;
    }

    // TODO seems like we could do this lazily with byte source slices
    @SuppressWarnings("unused")
    private static class ByteSourceSlicingFormDataParser {
        static List<ByteSource> split(ByteSource whole, byte[] boundaryBytes, int minNumBoundaries) throws NanohttpdFormDataParser.RuntimeIOException, BadMultipartFormDataException {
            int[] boundaryIdxs;
            try {
                boundaryIdxs = getBoundaryPositions(ByteBuffer.wrap(whole.read()), boundaryBytes);
            } catch (IOException e) {
                throw new NanohttpdFormDataParser.RuntimeIOException("failed to read from byte source", e);
            }
            return split(whole, boundaryIdxs, boundaryBytes.length, minNumBoundaries);
        }

        static List<ByteSource> split(ByteSource whole, int[] boundaryIdxs, int boundaryLength, int minNumBoundaries) throws NanohttpdFormDataParser.RuntimeIOException,BadMultipartFormDataException {
            if (boundaryIdxs.length < minNumBoundaries) {
                throw new BadMultipartFormDataException("BAD REQUEST: Content type is multipart/form-data but contains less than two boundary strings.");
            }
            List<ByteSource> boundedParts = new ArrayList<>(boundaryIdxs.length - 1);
            for (int k = 0; k < boundaryIdxs.length - 1; k++) {
                int from = boundaryIdxs[k] + boundaryLength;
                int to = boundaryIdxs[k + 1];
                boundedParts.add(whole.slice(from, to - from));
            }
            return boundedParts;
        }
        private static byte[] INNER_DELIM = "\r\n".getBytes(StandardCharsets.US_ASCII);

    }
}
