package io.github.mike10004.vhs;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class HarBridgeEntryParserTest {

    @Test
    void parseResponse() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(false, "not yet implemented");
    }

    @Test
    public void replaceContentLengthHeaderValue() throws Exception {
        String data = "hello, world";
        Charset charset = StandardCharsets.UTF_8;
        byte[] bytes = data.getBytes(charset);
        long originalContentLengthValue = bytes.length * 2;
        MediaType contentType = MediaType.PLAIN_TEXT_UTF_8.withCharset(charset);
        Map<String, String> originalHeaders = ImmutableMap.of(HttpHeaders.CONTENT_TYPE, contentType.toString(), HttpHeaders.CONTENT_LENGTH, String.valueOf(originalContentLengthValue));
        HttpRespondable respondable = HarBridgeEntryParser.constructRespondable(200, bytes, () -> originalHeaders.entrySet().stream(), contentType);
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

}