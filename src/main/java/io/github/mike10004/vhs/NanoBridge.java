package io.github.mike10004.vhs;

import com.google.common.io.ByteStreams;
import com.google.common.net.MediaType;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.stream.Stream;

public class NanoBridge implements HarBridge<NanoHTTPD.IHTTPSession> {
    @Override
    public String getRequestMethod(IHTTPSession entry) {
        return entry.getMethod().name();
    }

    @Override
    public String getRequestUrl(IHTTPSession entry) {
        return entry.getUri();
    }

    @Override
    public Stream<Map.Entry<String, String>> getRequestHeaders(IHTTPSession entry) {
        return entry.getHeaders().entrySet().stream();
    }

    @Override
    public Stream<Map.Entry<String, String>> getResponseHeaders(IHTTPSession entry) {
        throw new UnsupportedOperationException("NanoBridge does not support response parsing");
    }

    @Nullable
    @Override
    public byte[] getRequestPostData(IHTTPSession entry) throws IOException {
        byte[] body = null;
        switch (entry.getMethod()) {
            case POST:
            case PUT:
                try (InputStream in = getOrSubstituteEmpty(entry.getInputStream())) {
                    body = ByteStreams.toByteArray(in);
                }
        }
        return body;
    }

    @Nullable
    @Override
    public byte[] getResponseBody(IHTTPSession entry) {
        throw new UnsupportedOperationException("NanoBridge does not support response parsing");
    }

    @Override
    public MediaType getResponseContentType(IHTTPSession entry) {
        throw new UnsupportedOperationException("NanoBridge does not support response parsing");
    }

    @Override
    public int getResponseStatus(IHTTPSession entry) {
        throw new UnsupportedOperationException("NanoBridge does not support response parsing");
    }

    private static InputStream getOrSubstituteEmpty(InputStream in) {
        return in == null ? new ByteArrayInputStream(new byte[0]) : in;
    }
}
