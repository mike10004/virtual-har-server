package io.github.mike10004.vhs.nanohttpd;

import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import io.github.mike10004.nanochamp.repackaged.fi.iki.elonen.NanoHTTPD.IHTTPSession;
import io.github.mike10004.vhs.harbridge.HarBridge;
import io.github.mike10004.vhs.harbridge.ParsedRequest;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.stream.Stream;

public class NanoBridge implements HarBridge<IHTTPSession> {

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

    @Nullable
    @Override
    public ByteSource getRequestPostData(IHTTPSession entry) throws IOException {
        switch (entry.getMethod()) {
            case POST:
            case PUT:
                try (InputStream in = getOrSubstituteEmpty(entry.getInputStream())) {
                    return ByteSource.wrap(ByteStreams.toByteArray(in));
                }
        }
        return null;
    }

    @Override
    public int getResponseStatus(IHTTPSession entry) {
        throw new UnsupportedOperationException("NanoBridge does not support response parsing");
    }

    private static InputStream getOrSubstituteEmpty(InputStream in) {
        return in == null ? new ByteArrayInputStream(new byte[0]) : in;
    }

    @Override
    public ResponseData getResponseData(ParsedRequest request, IHTTPSession entry) throws IOException {
        throw new UnsupportedOperationException("NanoBridge does not support response parsing");
    }
}
