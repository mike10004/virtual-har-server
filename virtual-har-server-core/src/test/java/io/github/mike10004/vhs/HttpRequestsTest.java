package io.github.mike10004.vhs;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.*;

public class HttpRequestsTest {

    @Test
    public void parseQuery() {
        Multimap<String, String> q = HttpRequests.parseQuery(URI.create("http://example.com/hello"));
        assertNull(q);
        q = HttpRequests.parseQuery(URI.create("http://example.com/hello?foo=bar"));
        assertEquals(ImmutableMultimap.of("foo", "bar"), q);
        q = HttpRequests.parseQuery(URI.create("http://example.com/hello?"));
        assertEquals(ImmutableMultimap.of(), q);
    }

    @Test
    public void parseQuery_paramWithNoAssignment() {
        URI uri = URI.create("https://www.example.com/foo/bar?1234567890");
        Multimap<?, ?> query = HttpRequests.parseQuery(uri);
        assertNotNull(query);
        ImmutableMultimap.copyOf(query);
    }

}