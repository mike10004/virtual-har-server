package io.github.mike10004.vhs;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteSource;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.github.mike10004.vhs.harbridge.HarBridge;
import io.github.mike10004.vhs.harbridge.HarBridge.ResponseData;
import io.github.mike10004.vhs.harbridge.HttpMethod;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class HarBridgeEntryParserTest {

    @Test
    void parseResponse() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(false, "not yet implemented");
    }

    @Test
    void replaceContentLengthHeaderValue() throws Exception {
        String data = "hello, world";
        Charset charset = StandardCharsets.UTF_8;
        byte[] bytes = data.getBytes(charset);
        long originalContentLengthValue = bytes.length * 2;
        MediaType contentType = MediaType.PLAIN_TEXT_UTF_8.withCharset(charset);
        Map<String, String> originalHeaders = ImmutableMap.of(HttpHeaders.CONTENT_TYPE, contentType.toString(), HttpHeaders.CONTENT_LENGTH, String.valueOf(originalContentLengthValue));
        HttpRespondable respondable = HarBridgeEntryParser.constructRespondable(200, new ResponseData(originalHeaders.entrySet().stream(), contentType, ByteSource.wrap(bytes)));
        Multimap<String, String> headersMm = ArrayListMultimap.create();
        respondable.streamHeaders().forEach(h -> {
            headersMm.put(h.getKey(), h.getValue());
        });
        Map<String, Collection<String>> headers = headersMm.asMap();
        Collection<String> values = headers.get(HttpHeaders.CONTENT_LENGTH);
        System.out.format("%s: %s%n", HttpHeaders.CONTENT_LENGTH, values);
        assertEquals(1, values.size(), "num values");
        String finalContentLengthStr = values.iterator().next();
        assertNotNull(finalContentLengthStr, "final content length header not found");
        long finalContentLength = Long.parseLong(finalContentLengthStr);
        assertEquals(bytes.length, finalContentLength, "content length");
    }

    @Test
    void parseGetRequest() throws IOException {
        HarBridgeEntryParser<FakeHarEntry> parser = new HarBridgeEntryParser<>(new FakeHarBridge());
        URI url = URI.create("http://www.example.com/");
        FakeHarEntry something = FakeHarEntry.request("GET", url.toString());
        ParsedRequest request = parser.parseRequest(something);
        assertEquals(HttpMethod.GET, request.method, "method");
        assertEquals(url, request.url, "url");
    }

    @Test
    void parseConnectRequest() throws Exception {
        HarBridgeEntryParser<FakeHarEntry> parser = new HarBridgeEntryParser<>(new FakeHarBridge());
        URI url = new URI(null, null, "www.example.com", 443, null, null, null);
        FakeHarEntry something = FakeHarEntry.request("CONNECT", "www.example.com:443");
        ParsedRequest request = parser.parseRequest(something);
        assertEquals(HttpMethod.CONNECT, request.method, "method");
        assertNull(request.url.getScheme(), "scheme null");
        assertEquals(443, request.url.getPort(), "port 443");
        assertEquals("www.example.com", request.url.getHost(), "host");
        assertEquals(url, request.url, "url");
    }

    private static class FakeHarBridge implements HarBridge<FakeHarEntry> {

        @Override
        public String getRequestMethod(FakeHarEntry entry) {
            return entry.getRequestMethod();
        }

        @Override
        public String getRequestUrl(FakeHarEntry entry) {
            return entry.getRequestUrl();
        }

        @Override
        public Stream<Entry<String, String>> getRequestHeaders(FakeHarEntry entry) {
            return entry.getRequestHeaders();
        }

        @Nullable
        @Override
        public ByteSource getRequestPostData(FakeHarEntry entry) throws IOException {
            return entry.getRequestPostData();
        }

        @Override
        public int getResponseStatus(FakeHarEntry entry) {
            return entry.getResponseStatus();
        }

        @Override
        public ResponseData getResponseData(ParsedRequest request, FakeHarEntry entry) throws IOException {
            return new ResponseData(entry.getResponseHeaders(), entry.responseContentType, ByteSource.wrap(entry.getResponseBody() == null ? new byte[0] : entry.getResponseBody()));
        }
    }
    
    @SuppressWarnings("unused")
    private static class FakeHarEntry {

        private final String requestMethod, requestUrl;
        private final Collection<Map.Entry<String, String>> requestHeaders;
        private final ByteSource requestPostData;
        private final int responseStatus;
        private final Collection<Map.Entry<String, String>> responseHeaders;
        private final byte[] responseBody;
        private final MediaType responseContentType;

        public static FakeHarEntry request(String requestMethod, String requestUrl) {
            return request(requestMethod, requestUrl, Collections.emptyList(), null);
        }

        public static FakeHarEntry request(String requestMethod, String requestUrl, Collection<Entry<String, String>> requestHeaders, ByteSource requestPostData) {
            return new FakeHarEntry(requestMethod, requestUrl, requestHeaders, requestPostData, -1, null, null, null);
        }

        public FakeHarEntry(String requestMethod, String requestUrl, Collection<Entry<String, String>> requestHeaders, ByteSource requestPostData, int responseStatus, Collection<Entry<String, String>> responseHeaders, byte[] responseBody, MediaType responseContentType) {
            this.requestMethod = requestMethod;
            this.requestUrl = requestUrl;
            this.requestHeaders = requestHeaders;
            this.requestPostData = requestPostData;
            this.responseStatus = responseStatus;
            this.responseHeaders = responseHeaders;
            this.responseBody = responseBody;
            this.responseContentType = responseContentType;
        }

        public String getRequestMethod() {
            return requestMethod;
        }

        
        public String getRequestUrl() {
            return requestUrl;
        }

        
        public Stream<Entry<String, String>> getRequestHeaders() {
            return requestHeaders.stream();
        }

        
        public Stream<Entry<String, String>> getResponseHeaders() {
            return responseHeaders.stream();
        }

        public ByteSource getRequestPostData() throws IOException {
            return requestPostData;
        }

        
        public byte[] getResponseBody() throws IOException {
            return responseBody;
        }

        
        public MediaType getResponseContentType() {
            return responseContentType;
        }

        
        public int getResponseStatus() {
            return responseStatus;
        }
        
    }
}