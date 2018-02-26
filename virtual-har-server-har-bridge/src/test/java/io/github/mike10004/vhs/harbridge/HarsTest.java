package io.github.mike10004.vhs.harbridge;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteStreams;
import com.google.common.io.Resources;
import com.google.common.net.HttpHeaders;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.mike10004.vhs.harbridge.Hars.ResponseContentTranslation;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HarsTest {

    @Test
    void isBase64Encoded() {
        ImmutableMap.<Base64TestCase, Boolean>builder()
                .put(new Base64TestCase("text/plain", "abc", null, 3L), false)
                .put(new Base64TestCase("text/plain", "abc", null, null), false)
                .put(new Base64TestCase("text/plain", "abc", "base64", null), true)
                .put(new Base64TestCase("text/plain", "abc", "base64", 3L), true)
                .put(new Base64TestCase("application/octet-stream", "abc", null, null), true)
                .build().forEach((testCase, expected) -> {
            boolean actual = Hars.isBase64Encoded(testCase.contentType, testCase.text, testCase.encoding, testCase.size);
            assertEquals(expected, actual, () -> "expected " + expected + " for " + testCase);
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
                null, null).body.read();
        byte[] decompressed = gunzip(data);
        String decompressedText = new String(decompressed, StandardCharsets.UTF_8);
        assertEquals(text, decompressedText, "text");
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
    String getFirstHeaderValueFromNameValuePairs(JsonArray arrayOfNameValuePairs, String headerName) {
        return ImmutableList.copyOf(arrayOfNameValuePairs).stream()
                .map(JsonElement::getAsJsonObject)
                .filter(el -> headerName.equalsIgnoreCase(el.get("name").getAsString()))
                .map(el -> el.get("value").getAsString())
                .findFirst().orElse(null);
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
        String responseText = result.body.asCharSource(StandardCharsets.UTF_8).read();
        assertEquals(text, responseText, "text");

    }
}