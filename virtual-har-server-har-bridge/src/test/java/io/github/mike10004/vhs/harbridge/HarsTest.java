package io.github.mike10004.vhs.harbridge;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.common.io.Resources;
import com.google.common.net.HttpHeaders;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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
        String contentEncodingHeaderValue = ImmutableList.copyOf(response.getAsJsonArray("headers")).stream()
                .map(JsonElement::getAsJsonObject)
                .filter(el -> HttpHeaders.CONTENT_ENCODING.equalsIgnoreCase(el.get("name").getAsString()))
                .map(el -> el.get("value").getAsString())
                .findFirst().orElse(null);
        String text = content.get("text").getAsString();
        byte[] data = Hars.translateResponseContent(content.get("mimeType").getAsString(),
                text,
                response.get("bodySize").getAsLong(),
                content.get("size").getAsLong(),
                contentEncodingHeaderValue,
                null, null);
        byte[] decompressed = gunzip(data);
        String decompressedText = new String(decompressed, StandardCharsets.UTF_8);
        assertEquals(text, decompressedText, "text");
    }

    private static byte[] gunzip(byte[] compressed) throws IOException {
        try (GZIPInputStream gzin = new GZIPInputStream(new ByteArrayInputStream(compressed), compressed.length * 2)) {
            return ByteStreams.toByteArray(gzin);
        }
    }
}