package io.github.mike10004.vhs.bmp;

import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.google.common.primitives.Ints;
import io.github.mike10004.vhs.repackaged.fi.iki.elonen.NanoHTTPD;
import io.github.mike10004.vhs.repackaged.fi.iki.elonen.NanoHTTPD.IHTTPSession;
import io.github.mike10004.vhs.repackaged.fi.iki.elonen.NanoHTTPD.Response;
import io.github.mike10004.vhs.repackaged.fi.iki.elonen.NanoHTTPD.Response.Status;
import io.github.mike10004.vhs.HarBridgeEntryParser;
import io.github.mike10004.vhs.HttpRespondable;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;

public class NanoHttpAssistant implements HttpAssistant<IHTTPSession, Response> {

    private static final Logger log = LoggerFactory.getLogger(NanoHttpAssistant.class);
    private static final int MAX_RESPONSE_BUFFER_LEN = 1024 * 1024 * 1024;
    private static final int MIN_RESPONSE_BUFFER_LEN = 16;

    private final HarBridgeEntryParser<IHTTPSession> entryParser = new HarBridgeEntryParser<>(new NanoBridge());

    @Override
    public ParsedRequest parseRequest(IHTTPSession incomingRequest) throws IOException {
        return entryParser.parseRequest(incomingRequest);
    }

    @Override
    public Response transformRespondable(IHTTPSession incomingRequest, HttpRespondable respondable) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(getResponseBufferLength(respondable.streamHeaders(), 256)); // TODO set size from Content-Length header if present
        MediaType contentType = respondable.writeBody(baos);
        byte[] responseBody = baos.toByteArray();
        NanoHTTPD.Response.IStatus status = NanoHTTPD.Response.Status.lookup(respondable.getStatus());
        String mimeType = contentType.toString();
        NanoHTTPD.Response nanoResponse = NanoHTTPD.newFixedLengthResponse(status, mimeType, new ByteArrayInputStream(responseBody), responseBody.length);
        respondable.streamHeaders().forEach(header -> {
            nanoResponse.addHeader(header.getKey(), header.getValue());
        });
        return nanoResponse;
    }

    @Override
    public Response constructResponse(IHTTPSession incomingRequest, ImmutableHttpResponse response) {
        byte[] body;
        try {
            body = response.getDataSource().read();
        }  catch (IOException e) {
            log.warn("failed to reconstitute response", e);
            body = new byte[0];
        }
        return newResponse(response.status, response.getContentType(), body);
    }

    private static NanoHTTPD.Response newResponse(int status, @Nullable MediaType contentType, byte[] data) {
        if (contentType == null) {
            contentType = MediaType.OCTET_STREAM;
        }
        return NanoHTTPD.newFixedLengthResponse(Status.lookup(status), contentType.toString(), new ByteArrayInputStream(data), data.length);
    }

    @SuppressWarnings("SameParameterValue")
    protected int getResponseBufferLength(Stream<? extends Map.Entry<String, String>> responseHeaders, int defaultLength) throws IOException {
        Long value = responseHeaders.filter(header -> HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(header.getKey()))
                .map(Map.Entry::getValue)
                .map(Long::valueOf)
                .findFirst().orElse(null);
        if (value == null) {
            return defaultLength;
        }
        if (value.longValue() < MIN_RESPONSE_BUFFER_LEN) {
            return MIN_RESPONSE_BUFFER_LEN;
        }
        if (value.longValue() > MAX_RESPONSE_BUFFER_LEN) {
            return MAX_RESPONSE_BUFFER_LEN;
        }
        return Ints.checkedCast(value.longValue());
    }
}
