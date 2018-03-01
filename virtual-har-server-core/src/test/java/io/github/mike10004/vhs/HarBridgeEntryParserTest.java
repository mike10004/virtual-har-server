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
import org.junit.Assume;
import org.junit.Test;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class HarBridgeEntryParserTest {

    @Test
    public void parseResponse() throws Exception {
        Assume.assumeTrue("not yet implemented", false);
    }

    @Test
    public void replaceContentLengthHeaderValue() throws Exception {
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
        assertEquals("num values", 1, values.size());
        String finalContentLengthStr = values.iterator().next();
        assertNotNull("final content length header not found", finalContentLengthStr);
        long finalContentLength = Long.parseLong(finalContentLengthStr);
        assertEquals("content length", bytes.length, finalContentLength);
    }

    @Test
    public void parseGetRequest() throws IOException {
        HarBridgeEntryParser<FakeHarEntry> parser = new HarBridgeEntryParser<>(new FakeHarBridge());
        URI url = URI.create("http://www.example.com/");
        FakeHarEntry something = FakeHarEntry.request("GET", url.toString());
        ParsedRequest request = parser.parseRequest(something);
        assertEquals("method", HttpMethod.GET, request.method);
        assertEquals("url", url, request.url);
    }

    @Test
    public void parseConnectRequest() throws Exception {
        HarBridgeEntryParser<FakeHarEntry> parser = new HarBridgeEntryParser<>(new FakeHarBridge());
        URI url = new URI(null, null, "www.example.com", 443, null, null, null);
        FakeHarEntry something = FakeHarEntry.request("CONNECT", "www.example.com:443");
        ParsedRequest request = parser.parseRequest(something);
        assertEquals("method", HttpMethod.CONNECT, request.method);
        assertNull("scheme null", request.url.getScheme());
        assertEquals("port 443", 443, request.url.getPort());
        assertEquals("host", "www.example.com", request.url.getHost());
        assertEquals("url", url, request.url);
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