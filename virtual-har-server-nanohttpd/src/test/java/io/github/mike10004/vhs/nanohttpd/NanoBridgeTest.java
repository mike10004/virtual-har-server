package io.github.mike10004.vhs.nanohttpd;

import com.google.common.collect.ImmutableMap;
import io.github.mike10004.nanochamp.repackaged.fi.iki.elonen.NanoHTTPD;
import io.github.mike10004.nanochamp.repackaged.fi.iki.elonen.NanoHTTPD.CookieHandler;
import io.github.mike10004.nanochamp.repackaged.fi.iki.elonen.NanoHTTPD.Method;
import io.github.mike10004.nanochamp.repackaged.fi.iki.elonen.NanoHTTPD.ResponseException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static io.github.mike10004.nanochamp.repackaged.fi.iki.elonen.NanoHTTPD.Method.GET;
import static org.junit.jupiter.api.Assertions.*;

class NanoBridgeTest {

    @Test
    void getRequestUrl_get() {
        NanoBridge bridge = new NanoBridge();
        String url = "http://www.example.com/";
        String actual = bridge.getRequestUrl(new FakeSession(Method.GET, url));
        assertEquals(url, actual, "url from GET session");
    }

    @Test
    void getRequestUrl_connect() {
        NanoBridge bridge = new NanoBridge();
        String url = "www.example.com:443";
        String actual = bridge.getRequestUrl(new FakeSession(Method.CONNECT, url));
        assertEquals(url, actual, "url from CONNECT session");
    }

    private static class FakeSession implements NanoHTTPD.IHTTPSession {

        private final NanoHTTPD.Method method;
        private final String url;

        public FakeSession(Method method, String url) {
            this.method = method;
            this.url = url;
        }

        @SuppressWarnings("RedundantThrows")
        @Override
        public void execute() throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public CookieHandler getCookies() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, String> getHeaders() {
            return ImmutableMap.of();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public Method getMethod() {
            return method;
        }

        @Override
        public Map<String, String> getParms() {
            return ImmutableMap.of();
        }

        @Override
        public Map<String, List<String>> getParameters() {
            return null;
        }

        @Override
        public String getQueryParameterString() {
            return null;
        }

        @Override
        public String getUri() {
            return url;
        }

        @SuppressWarnings("RedundantThrows")
        @Override
        public void parseBody(Map<String, String> map) throws IOException, ResponseException {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getRemoteIpAddress() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getRemoteHostName() {
            throw new UnsupportedOperationException();
        }

    }
}