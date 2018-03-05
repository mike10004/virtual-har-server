package io.github.mike10004.vhs.bmp;

import com.google.common.collect.ImmutableMap;
import io.github.mike10004.vhs.repackaged.fi.iki.elonen.NanoHTTPD;
import io.github.mike10004.vhs.repackaged.fi.iki.elonen.NanoHTTPD.CookieHandler;
import io.github.mike10004.vhs.repackaged.fi.iki.elonen.NanoHTTPD.Method;
import io.github.mike10004.vhs.repackaged.fi.iki.elonen.NanoHTTPD.ResponseException;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class NanoBridgeTest {

    @Test
    public void getRequestUrl_get() {
        NanoBridge bridge = new NanoBridge();
        String url = "http://www.example.com/";
        String actual = bridge.getRequestUrl(new FakeSession(Method.GET, url));
        assertEquals("url from GET session", url, actual);
    }

    @Test
    public void getRequestUrl_connect() {
        NanoBridge bridge = new NanoBridge();
        String url = "www.example.com:443";
        String actual = bridge.getRequestUrl(new FakeSession(Method.CONNECT, url));
        assertEquals("url from CONNECT session", url, actual);
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

        @SuppressWarnings("deprecation")
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