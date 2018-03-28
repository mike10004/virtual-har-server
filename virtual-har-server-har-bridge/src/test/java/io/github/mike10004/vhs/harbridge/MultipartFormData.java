package io.github.mike10004.vhs.harbridge;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteSource;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("Duplicates")
public class MultipartFormData {

    private static final int HTTP_ERROR_BAD_REQUEST = 400;
    private static final Charset DEFAULT_MULTIPART_FORM_DATA_ENCODING = StandardCharsets.UTF_8;
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
         * @throws RuntimeIOException
         */
        List<FormDataPart> decodeMultipartFormData(MediaType contentType, byte[] data) throws BadMultipartFormDataException, RuntimeIOException;
    }

    public static FormDataParser getParser() {
        return new NanohttpdFormDataParser();
    }

    static class FormDataPart {

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

    private static final Charset BOUNDARY_ENCODING = StandardCharsets.US_ASCII; // assume header value contains only ASCII

    static class NanohttpdFormDataParser implements FormDataParser {

        private static final int MAX_HEADER_SIZE = 1024;

        private static final String CONTENT_DISPOSITION_REGEX = "([ |\t]*Content-Disposition[ |\t]*:)(.*)";

        private static final Pattern CONTENT_DISPOSITION_PATTERN = Pattern.compile(CONTENT_DISPOSITION_REGEX, Pattern.CASE_INSENSITIVE);

        private static final String CONTENT_TYPE_REGEX = "([ |\t]*content-type[ |\t]*:)(.*)";

        private static final Pattern CONTENT_TYPE_PATTERN = Pattern.compile(CONTENT_TYPE_REGEX, Pattern.CASE_INSENSITIVE);

        /**
         * Decodes the Multipart Body data and put it into Key/Value pairs.
         */
        @Override
        public List<FormDataPart> decodeMultipartFormData(MediaType contentType, byte[] data) throws BadMultipartFormDataException {
            Charset parentTypeEncoding = contentType.charset().or(DEFAULT_MULTIPART_FORM_DATA_ENCODING);
            String boundary = getBoundaryOrDie(contentType);
            byte[] boundaryBytes = boundary.getBytes(BOUNDARY_ENCODING);
            ByteBuffer fbuf = ByteBuffer.wrap(data);
            int[] boundaryIdxs = getBoundaryPositions(fbuf, boundaryBytes);
            List<FormDataPart> parts = new ArrayList<>(boundaryIdxs.length);
            byte[] partHeaderBuff = new byte[MAX_HEADER_SIZE];
            for (int boundaryIdx = 0; boundaryIdx < boundaryIdxs.length - 1; boundaryIdx++) {
                Multimap<String, String> headers = ArrayListMultimap.create();
                fbuf.position(boundaryIdxs[boundaryIdx]);
                int len = (fbuf.remaining() < MAX_HEADER_SIZE) ? fbuf.remaining() : MAX_HEADER_SIZE;
                fbuf.get(partHeaderBuff, 0, len);
                MemoryBufferedReader in = new MemoryBufferedReader(partHeaderBuff, 0, len, parentTypeEncoding, len);
                int headerLines = 0;
                // First line is boundary string
                String mpline = in.readLine();
                headerLines++;
                if (mpline == null || !mpline.contains(boundary)) {
                    throw new MalformedMultipartFormDataException("BAD REQUEST: Content type is multipart/form-data but chunk does not start with boundary.");
                }

                // Parse the reset of the header lines
                mpline = in.readLine();
                headerLines++;
                @Nullable String partContentType = null;
                @Nullable ContentDisposition disposition = null;
                while (mpline != null && mpline.trim().length() > 0) {
                    Matcher matcher = CONTENT_DISPOSITION_PATTERN.matcher(mpline);
                    if (matcher.matches()) {
                        String attributeString = matcher.group(2);
                        headers.put(HttpHeaders.CONTENT_DISPOSITION, attributeString);
                        disposition = ContentDisposition.parse(attributeString);
                    }
                    matcher = CONTENT_TYPE_PATTERN.matcher(mpline);
                    if (matcher.matches()) {
                        partContentType = matcher.group(2).trim();
                        headers.put(HttpHeaders.CONTENT_TYPE, partContentType);
                    }
                    mpline = in.readLine();
                    headerLines++;
                }
                int partHeaderLength = 0;
                while (headerLines-- > 0) {
                    partHeaderLength = skipOverNewLine(partHeaderBuff, partHeaderLength);
                }
                // Read the part data
                if (partHeaderLength >= len - 4) {
                    throw new BadMultipartFormDataException("Multipart header size exceeds MAX_HEADER_SIZE.");
                }
                int partDataStart = boundaryIdxs[boundaryIdx] + partHeaderLength;
                int partDataEnd = boundaryIdxs[boundaryIdx + 1] - 4;

                fbuf.position(partDataStart);

                if (partContentType == null) {
                    partContentType = MediaType.FORM_DATA.toString();
                }
                // Read it into a file
                byte[] fileData = copyRegion(fbuf, partDataStart, partDataEnd - partDataStart);
                TypedContent file = TypedContent.identity(ByteSource.wrap(fileData), MediaType.parse(partContentType));
                parts.add(new FormDataPart(headers, disposition, file));
            }
            return parts;
        }
    }

    static class RuntimeIOException extends RuntimeException {
        public RuntimeIOException(String message, IOException cause) {
            super(message, cause);
        }
    }

    private static class MemoryBufferedReader extends BufferedReader {

        public MemoryBufferedReader(byte[] data, int offset, int len, Charset charset, int sz) {
            super(new InputStreamReader(new ByteArrayInputStream(data, offset, len), charset), sz);
        }

        @Override
        public String readLine() {
            try {
                return super.readLine();
            } catch (IOException e) {
                throw new RuntimeIOException("readLine() on memory buffer should not throw exception, but alas", e);
            }
        }
    }

    /**
     * Find the byte positions where multipart boundaries start. This reads
     * a large block at a time and uses a temporary buffer to optimize
     * (memory mapped) file access.
     */
    private static int[] getBoundaryPositions(ByteBuffer b, byte[] boundary) {
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

    private static int skipOverNewLine(byte[] partHeaderBuff, int index) {
        while (partHeaderBuff[index] != '\n') {
            index++;
        }
        return ++index;
    }

    /**
     * Copies a region of a buffer to a new byte array.
     */
    private static byte[] copyRegion(ByteBuffer srcBuffer, int offset, int len) {
        if (len > 0) {
            final ByteArrayOutputStream destBuffer = new ByteArrayOutputStream(256);
            ByteBuffer srcDuplicate = srcBuffer.duplicate();
            srcDuplicate.position(offset).limit(offset + len);
            WritableByteChannel channel = Channels.newChannel(destBuffer);
            try {
                channel.write(srcDuplicate.slice());
                destBuffer.flush();
            } catch (IOException e) {
                throw new RuntimeException("operation on memory-only buffers should not throw IOException, but alas", e);
            }
            return destBuffer.toByteArray();
        }
        return new byte[0];
    }

    // TODO seems like we could do this lazily with byte source slices
    @SuppressWarnings("unused")
    private static class ByteSourceSlicingFormDataParser {
        static List<ByteSource> split(ByteSource whole, byte[] boundaryBytes, int minNumBoundaries) throws RuntimeIOException, BadMultipartFormDataException {
            int[] boundaryIdxs;
            try {
                boundaryIdxs = getBoundaryPositions(ByteBuffer.wrap(whole.read()), boundaryBytes);
            } catch (IOException e) {
                throw new RuntimeIOException("failed to read from byte source", e);
            }
            return split(whole, boundaryIdxs, boundaryBytes.length, minNumBoundaries);
        }

        static List<ByteSource> split(ByteSource whole, int[] boundaryIdxs, int boundaryLength, int minNumBoundaries) throws RuntimeIOException,BadMultipartFormDataException {
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
