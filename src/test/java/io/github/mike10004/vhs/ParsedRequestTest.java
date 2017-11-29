package io.github.mike10004.vhs;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import edu.umass.cs.benchlab.har.HarHeader;
import edu.umass.cs.benchlab.har.HarHeaders;
import edu.umass.cs.benchlab.har.HarRequest;
import fi.iki.elonen.NanoHTTPD;
import org.apache.http.HttpVersion;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class ParsedRequestTest {
    @Test
    void create() {
        HarHeaders headers = new HarHeaders();
        headers.addHeader(new HarHeader("X-Something", "foo"));
        headers.addHeader(new HarHeader("X-Something-Else", "bar"));
        String urlStr = "https://www.example.com/hello";
        HarRequest request = new HarRequest("GET", urlStr, HttpVersion.HTTP_1_1.toString(), null, headers, null, -1, 0);
        ParsedRequest parsed = ParsedRequest.create(request);
        assertEquals(NanoHTTPD.Method.GET, parsed.method, "method");
        assertEquals(URI.create(urlStr), parsed.url, "url");
        assertEquals(null, parsed.query, "query");
        assertEquals(2, parsed.indexedHeaders.size());
        assertEquals("foo", parsed.indexedHeaders.get("x-something").iterator().next(), "header value");
    }

    @Test
    void parseQuery() {
        Multimap<String, String> q = ParsedRequest.parseQuery(URI.create("http://example.com/hello"));
        assertNull(q);
        q = ParsedRequest.parseQuery(URI.create("http://example.com/hello?foo=bar"));
        assertEquals(ImmutableMultimap.of("foo", "bar"), q);
        q = ParsedRequest.parseQuery(URI.create("http://example.com/hello?"));
        assertEquals(ImmutableMultimap.of(), q);
    }

}