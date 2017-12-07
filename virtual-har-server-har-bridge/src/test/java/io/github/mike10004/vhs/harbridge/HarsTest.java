package io.github.mike10004.vhs.harbridge;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
}