package io.github.mike10004.vhs.harbridge;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteSource;
import com.google.common.io.Resources;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringEscapeUtils;
import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

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
        String json = Resources.toString(getClass().getResource("/gzipped-response.json"), UTF_8);
        JsonObject response = new JsonParser().parse(json).getAsJsonObject();
        JsonObject content = response.getAsJsonObject("content");
        String contentEncodingHeaderValue = HarBridgeTests.getFirstHeaderValueFromNameValuePairs(response.getAsJsonArray("headers"), HttpHeaders.CONTENT_ENCODING);
        String text = content.get("text").getAsString();
        byte[] data = Hars.translateResponseContent(content.get("mimeType").getAsString(),
                text,
                response.get("bodySize").getAsLong(),
                content.get("size").getAsLong(),
                contentEncodingHeaderValue,
                null, null).asByteSource().read();
        String decompressedText = new String(data, UTF_8);
        assertEquals("text", text, decompressedText);
    }

    @Test
    public void getUncompressedContent() throws Exception {
        String HEX_BYTES = "E29C93"; // unicode check mark (not in ASCII or ISO-8859-1 charsets)
        byte[] bytes = BaseEncoding.base16().decode(HEX_BYTES);
        String text = new String(bytes, UTF_8);
        System.out.format("text: %s%n", text);
        MediaType contentType = MediaType.PLAIN_TEXT_UTF_8.withoutParameters();
        TypedContent translation = Hars.getUncompressedContent(contentType.toString(), text, null, null, null, null, null, StandardCharsets.ISO_8859_1);
        ByteSource data = translation.asByteSource();
        String hexActual = BaseEncoding.base16().encode(data.read());
        assertEquals("result", HEX_BYTES, hexActual);
    }

    @Test
    public void detectCharset() throws Exception {
        Table<String, Charset, Charset> testCases = ImmutableTable.<String, Charset, Charset>builder()
                .put("", US_ASCII, US_ASCII)
                .put("abcdef", US_ASCII, US_ASCII)
                .put("Fractions: " + decodeFromHex("BCBDBE", ISO_8859_1), US_ASCII, UTF_8)
                .put("abcdef", ISO_8859_1, ISO_8859_1)
                .put("abcdef", UTF_8, UTF_8)
                .put("abc \uD83C\uDCA1\uD83C\uDCA8\ud83c\udcd1\ud83c\udcd8\ud83c\udcd3", ISO_8859_1, UTF_8)
                .build();
        testCases.cellSet().forEach(cell -> {
            String text = cell.getRowKey();
            Charset charset = cell.getColumnKey();
            Charset expected = cell.getValue();
            Charset actual = Hars.detectCharset(text, charset);
            String description = String.format("\"%s\" with suggestion %s", StringEscapeUtils.escapeJava(text), charset);
            System.out.format("%s -> %s (expecting %s)%n", description, actual, expected);
            assertEquals(description, expected, actual);
        });
    }

    @SuppressWarnings("SameParameterValue")
    private static String decodeFromHex(String hexEncodedBytes, Charset charset) {
        byte[] bytes = BaseEncoding.base16().decode(hexEncodedBytes.toUpperCase());
        return new String(bytes, charset);
    }
}