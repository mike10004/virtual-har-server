package io.github.mike10004.vhs.harbridge;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.io.Resources;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.mike10004.vhs.harbridge.Hars.ResponseContentTranslation;
import org.junit.Test;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class HarsTest {

    @Test
    public void isBase64Encoded() {
        ImmutableMap.<Base64TestCase, Boolean>builder()
                .put(new Base64TestCase("text/plain", "abc", null, 3L), false)
                .put(new Base64TestCase("text/plain", "abc", null, null), false)
                .put(new Base64TestCase("text/plain", "abc", "base64", null), true)
                .put(new Base64TestCase("text/plain", "abc", "base64", 3L), true)
                .put(new Base64TestCase("application/octet-stream", "abc", null, null), true)
                .build().forEach((testCase, expected) -> {
            boolean actual = Hars.isBase64Encoded(testCase.contentType, testCase.text, testCase.encoding, testCase.size);
            assertEquals("expected " + expected + " for " + testCase, expected, actual);
        });
    }

    private static class Base64TestCase {
        String contentType, text, encoding;
        Long size;

        public Base64TestCase(String contentType, String text, String encoding, Long size) {
            this.contentType = contentType;
            this.text = text;
            this.encoding = encoding;
            this.size = size;
        }

        @Override
        public String toString() {
            return "Base64TestCase{" +
                    "contentType='" + contentType + '\'' +
                    ", text='" + text + '\'' +
                    ", encoding='" + encoding + '\'' +
                    ", size=" + size +
                    '}';
        }
    }

    @Test
    public void testGzippedHtml() throws Exception {
        String json = Resources.toString(getClass().getResource("/gzipped-response.json"), StandardCharsets.UTF_8);
        JsonObject response = new JsonParser().parse(json).getAsJsonObject();
        JsonObject content = response.getAsJsonObject("content");
        String contentEncodingHeaderValue = getFirstHeaderValueFromNameValuePairs(response.getAsJsonArray("headers"), HttpHeaders.CONTENT_ENCODING);
        String text = content.get("text").getAsString();
        ParsedRequest request = buildRequest(HttpContentCodecs.CONTENT_ENCODING_GZIP);
        byte[] data = Hars.translateResponseContent(request, content.get("mimeType").getAsString(),
                text,
                response.get("bodySize").getAsLong(),
                content.get("size").getAsLong(),
                contentEncodingHeaderValue,
                null, null).getBodyOrNull().read();
        byte[] decompressed = gunzip(data);
        String decompressedText = new String(decompressed, StandardCharsets.UTF_8);
        assertEquals("text", text, decompressedText);
    }

    private static ParsedRequest buildRequest(@Nullable String acceptEncodingHeaderValue) {
        Multimap<String, String> headers = ArrayListMultimap.create();
        if (acceptEncodingHeaderValue != null) {
            headers.put(HttpHeaders.ACCEPT_ENCODING, acceptEncodingHeaderValue);
        }
        return ParsedRequest.inMemory(HttpMethod.GET, URI.create("http://www.example.com/"), ImmutableMultimap.of(), headers, null);
    }

    private static byte[] gunzip(byte[] compressed) throws IOException {
        try (GZIPInputStream gzin = new GZIPInputStream(new ByteArrayInputStream(compressed), compressed.length * 2)) {
            return ByteStreams.toByteArray(gzin);
        }
    }

    @Nullable
    private static String getFirstHeaderValueFromNameValuePairs(JsonArray arrayOfNameValuePairs, String headerName) {
        return ImmutableList.copyOf(arrayOfNameValuePairs).stream()
                .map(JsonElement::getAsJsonObject)
                .filter(el -> headerName.equalsIgnoreCase(el.get("name").getAsString()))
                .map(el -> el.get("value").getAsString())
                .findFirst().orElse(null);
    }

    private void test_canServeOriginalResponseContentEncoding(boolean expected, String contentEncoding, String acceptEncoding) {
        List<String> contentEncodings = Hars.parseContentEncodings(contentEncoding);
        boolean actual = Hars.canServeOriginalResponseContentEncoding(contentEncodings, acceptEncoding);
        assertEquals(String.format("expect %s for Content-Encoding: %s with Accept-Encoding: %s", expected, contentEncoding, acceptEncoding), expected, actual);
    }

    @Test
    public void canServeOriginalResponseContentEncoding() {
        test_canServeOriginalResponseContentEncoding(true, "gzip", "gzip");
        test_canServeOriginalResponseContentEncoding(true, "gzip", "deflate, gzip;q=1.0, *;q=0.5");
        test_canServeOriginalResponseContentEncoding(true, "gzip", "gzip, deflate, br");
        test_canServeOriginalResponseContentEncoding(true, "gzip, br", "gzip, deflate, br");
        test_canServeOriginalResponseContentEncoding(false, "gzip", null);
        test_canServeOriginalResponseContentEncoding(false, "gzip", "");
        test_canServeOriginalResponseContentEncoding(false, "gzip", "deflate;q=1.0, gzip;q=0.0, *;q=0.5");
        test_canServeOriginalResponseContentEncoding(true, null, null);
        test_canServeOriginalResponseContentEncoding(true, null, "");
        test_canServeOriginalResponseContentEncoding(true, "", null);
        test_canServeOriginalResponseContentEncoding(true, "", "");
        test_canServeOriginalResponseContentEncoding(true, "identity", null);
        test_canServeOriginalResponseContentEncoding(true, null, "gzip, deflate, br");
        test_canServeOriginalResponseContentEncoding(true, "", "gzip, deflate, br");
        test_canServeOriginalResponseContentEncoding(true, "identity", "gzip, deflate, br");
    }

    @Test
    public void testResponseEncodedButAcceptEncodingDisallows() throws Exception {
        String json = Resources.toString(getClass().getResource("/gzipped-response.json"), StandardCharsets.UTF_8);
        JsonObject response = new JsonParser().parse(json).getAsJsonObject();
        JsonObject content = response.getAsJsonObject("content");
        String contentEncodingHeaderValue = getFirstHeaderValueFromNameValuePairs(response.getAsJsonArray("headers"), HttpHeaders.CONTENT_ENCODING);
        String text = content.get("text").getAsString();
        ParsedRequest request = buildRequest(null);
        ResponseContentTranslation result = Hars.translateResponseContent(request, content.get("mimeType").getAsString(),
                text,
                response.get("bodySize").getAsLong(),
                content.get("size").getAsLong(),
                contentEncodingHeaderValue,
                null, null);
        String responseText = result.getBodyOrNull().asCharSource(StandardCharsets.UTF_8).read();
        assertEquals("text", text, responseText);

    }

    @Test
    public void modifyContentEncodingHeaderInBrotliEncodedResponse() throws Exception {
        ParsedRequest request = buildRequest("br");
        String text = "hello, world";
        ResponseContentTranslation translation = Hars.translateResponseContent(request, "text/plain", text, (long) text.length(), (long) text.length(), HttpContentCodecs.CONTENT_ENCODING_BROTLI, null, null);
        String actual = translation.getBodyOrNull().asCharSource(StandardCharsets.US_ASCII).read();
        assertEquals("decoded", text, actual);
        Map.Entry<String, String> originalHeader = new AbstractMap.SimpleImmutableEntry<>(HttpHeaders.CONTENT_ENCODING, HttpContentCodecs.CONTENT_ENCODING_BROTLI);
        Map.Entry<String, String> modifiedHeader = translation.headerMap.apply(originalHeader);
        assertEquals("name", originalHeader.getKey(), modifiedHeader.getKey());
        assertEquals("value", HttpContentCodecs.CONTENT_ENCODING_IDENTITY, modifiedHeader.getValue());
    }

    @Test
    public void getUncompressedContent() throws Exception {
        String HEX_BYTES = "E29C93";
        byte[] bytes = BaseEncoding.base16().decode(HEX_BYTES);
        String text = new String(bytes, StandardCharsets.UTF_8);
        System.out.format("text: %s%n", text);
        MediaType contentType = MediaType.PLAIN_TEXT_UTF_8.withoutParameters();
        ResponseContentTranslation translation = Hars.getUncompressedContent(contentType.toString(), text, null, null, null, Hars.MessageDirection.RESPONSE);
        ByteSource data = translation.getBodyOrNull();
        assertNotNull("data", data);
        String hexActual = BaseEncoding.base16().encode(data.read());
        assertEquals("result", HEX_BYTES, hexActual);
    }
}