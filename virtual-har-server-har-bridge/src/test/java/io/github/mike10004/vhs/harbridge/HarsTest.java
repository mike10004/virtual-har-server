package io.github.mike10004.vhs.harbridge;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteSource;
import com.google.common.io.Resources;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jayway.jsonpath.JsonPath;
import org.apache.commons.text.StringEscapeUtils;
import org.junit.Test;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    private static String getHeaderValue(Object doc, String headerName) {
        @SuppressWarnings("unchecked")
        String value = ((List<String>)JsonPath.read(doc, "$.log.entries[0].response.headers[?(@.name == '" + headerName + "')].value")).get(0);
        return value;
    }

    @Test
    public void getUncompressedContent_brotliEncodedTextInBase64() throws Exception {
        System.out.format("%n%n getUncompressedContent_brotliEncodedTextInBase64%n");
        String expectedContent = Resources.toString(getClass().getResource("/expected-response-body.json"), StandardCharsets.UTF_8);
        System.out.println(expectedContent);
        getUncompressedContent_brotli(getClass().getResource("/har-with-encoded-brotli-entry.json"), expectedContent);
    }

    @Nullable
    private static <T> T readJson(Object doc, String jsonPath) {
        try {
            return JsonPath.read(doc, jsonPath);
        } catch (com.jayway.jsonpath.PathNotFoundException ignore) {
            return null;
        }
    }

    @Test
    public void getUncompressedContent_brotliEncodedTextInText() throws Exception {
        System.out.format("%n%n getUncompressedContent_brotliEncodedTextInText%n");
        getUncompressedContent_brotli(getClass().getResource("/har-with-decoded-brotli-entry.json"), null);
    }

    private static void describe(String format, Object value) {
        if (value instanceof String) {
            value = String.format("\"%s\"", StringEscapeUtils.escapeJava(value.toString()));
        }
        if (value != null) {
            System.out.print(value.getClass().getSimpleName());
            System.out.print(" ");
        } else {
            System.out.print("Object ");
        }
        System.out.format(format, value);
    }

    @SuppressWarnings("UnusedReturnValue")
    private TypedContent getUncompressedContent_brotli(URL resource, @Nullable String expectedContent) throws IOException {
        String json = Resources.toString(resource, StandardCharsets.UTF_8);
        com.jayway.jsonpath.Configuration jsonPathConfig = com.jayway.jsonpath.Configuration.defaultConfiguration();
        @SuppressWarnings("unchecked")
        Map<String, Object> doc = (Map<String, Object>) jsonPathConfig.jsonProvider().parse(json);
        String contentType = JsonPath.read(doc, "$.log.entries[0].response.content.mimeType");
        String text = JsonPath.read(doc, "$.log.entries[0].response.content.text");
        String contentEncodingHeaderValue = getHeaderValue(doc, "Content-Encoding");
        String harContentEncoding = readJson(doc, "$.log.entries[0].response.content.encoding");
        Number bodySize = JsonPath.read(doc, "$.log.entries[0].response.bodySize");
        Number contentSize = JsonPath.read(doc, "$.log.entries[0].response.content.size");
        String contentLengthHeaderValue = getHeaderValue(doc, "Content-Length");
        String comment = readJson(doc, "$.log.entries[0].response.content.comment");
        describe("contentType = %s;%n", contentType);
        describe("text = %s;%n", text);
        describe("contentEncodingHeaderValue = %s;%n", contentEncodingHeaderValue);
        describe("harContentEncoding = %s;%n", harContentEncoding);
        describe("bodySize = %s;%n", bodySize);
        describe("contentSize = %s;%n", contentSize);
        describe("comment = %s;%n", comment);
        describe("contentLengthHeaderValue = %s;%n", contentLengthHeaderValue);
        checkState("br".equals(contentEncodingHeaderValue), "precondition: encoding = br");
        checkState(bodySize != null, "precondition: bodySize != null");
        checkState(contentSize != null, "precondition: contentSize != null");
        TypedContent typedContent = Hars.translateResponseContent(contentType, text, bodySize.longValue(), contentSize.longValue(),
                contentEncodingHeaderValue, harContentEncoding, comment);
        System.out.format("actual: %s%n", typedContent);
        System.out.format("content text base64 length: %d%n", text.length());
        if ("base64".equalsIgnoreCase(harContentEncoding)) {
            System.out.format("content bytes length: %d%n", BaseEncoding.base64().decode(text).length);
        }
        if (expectedContent == null) {
            expectedContent = text;
        }
        System.out.format("expected decoded length: %d%n", expectedContent.length());
        String actualContent = typedContent.asByteSource().asCharSource(MediaType.parse(contentType).charset().or(StandardCharsets.ISO_8859_1)).read();
        assertEquals("uncompressed content", expectedContent, actualContent);
        return typedContent;
    }

    @Test
    public void sizeOfBase64DecodedByteSourceIsKnown() throws Exception {
        byte[] bytes = "hello, world".getBytes(StandardCharsets.US_ASCII);
        String base64 = BaseEncoding.base64().encode(bytes);
        ByteSource decodingSource = Hars.base64DecodingSource(base64);
        Optional<Long> sizeIfKnown = decodingSource.sizeIfKnown().toJavaUtil();
        System.out.format("%s size: %s%n", decodingSource, sizeIfKnown);
        assertTrue("size known", sizeIfKnown.isPresent());
        assertEquals("size", bytes.length, sizeIfKnown.get().longValue());
    }

    @Test
    public void isBase64Encoded_json() throws IOException {
        String contentType = "application/json";
        String text = new Gson().toJson(ImmutableMap.of("apples", 1156, "peaches", 4689236, "pumpkin", 3275175, "pie", 1235856));
        String harContentEncoding = null;
        Long bodySize = text.length() / 2L;
        //noinspection ConstantConditions
        assertFalse("json text isBase64Encoded", Hars.isBase64Encoded(contentType, text, harContentEncoding, bodySize));
    }

    @Test
    public void isBase64Encoded_base64()  throws Exception {
        String contentType = "application/json";
        byte[] bytes = new byte[290];
        new Random(getClass().getName().hashCode()).nextBytes(bytes);
        String text = BaseEncoding.base64().encode(bytes);
        String harContentEncoding = "base64";
        Long bodySize = (long) bytes.length;
        assertTrue("text isBase64Encoded", Hars.isBase64Encoded(contentType, text, harContentEncoding, bodySize));
    }
}