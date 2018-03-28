package io.github.mike10004.vhs.harbridge;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteSource;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;

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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class NanohttpdFormDataParser implements MultipartFormData.FormDataParser {

    private static final int MAX_HEADER_SIZE = 1024;

    private static final String CONTENT_DISPOSITION_REGEX = "([ |\t]*Content-Disposition[ |\t]*:)(.*)";

    private static final Pattern CONTENT_DISPOSITION_PATTERN = Pattern.compile(CONTENT_DISPOSITION_REGEX, Pattern.CASE_INSENSITIVE);

    private static final String CONTENT_TYPE_REGEX = "([ |\t]*content-type[ |\t]*:)(.*)";

    private static final Pattern CONTENT_TYPE_PATTERN = Pattern.compile(CONTENT_TYPE_REGEX, Pattern.CASE_INSENSITIVE);

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

    /**
     * Decodes the Multipart Body data and put it into Key/Value pairs.
     */
    @Override
    public List<MultipartFormData.FormDataPart> decodeMultipartFormData(MediaType contentType, byte[] data) throws MultipartFormData.BadMultipartFormDataException {
        Charset parentTypeEncoding = contentType.charset().or(MultipartFormData.DEFAULT_MULTIPART_FORM_DATA_ENCODING);
        String boundary = MultipartFormData.getBoundaryOrDie(contentType);
        byte[] boundaryBytes = boundary.getBytes(MultipartFormData.BOUNDARY_ENCODING);
        ByteBuffer fbuf = ByteBuffer.wrap(data);
        int[] boundaryIdxs = MultipartFormData.getBoundaryPositions(fbuf, boundaryBytes);
        List<MultipartFormData.FormDataPart> parts = new ArrayList<>(boundaryIdxs.length);
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
                throw new MultipartFormData.MalformedMultipartFormDataException("BAD REQUEST: Content type is multipart/form-data but chunk does not start with boundary.");
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
                throw new MultipartFormData.BadMultipartFormDataException("Multipart header size exceeds MAX_HEADER_SIZE.");
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
            parts.add(new MultipartFormData.FormDataPart(headers, disposition, file));
        }
        return parts;
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
}
