package io.github.mike10004.vhs;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import fi.iki.elonen.NanoHTTPD;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

abstract class EntryParserTestBase<E> {

    protected abstract EntryParser<E> createParser();

    protected abstract E createEntryWithRequest(String method, String url, String...headers);

    @Test
    void create() throws Exception {
        String urlStr = "https://www.example.com/hello";
        E entry = createEntryWithRequest("GET", urlStr, "X-Something", "foo", "X-Something-Else", "bar");
        ParsedRequest parsed = createParser().parseRequest(entry);
        assertEquals(NanoHTTPD.Method.GET, parsed.method, "method");
        assertEquals(URI.create(urlStr), parsed.url, "url");
        assertEquals(null, parsed.query, "query");
        assertEquals(2, parsed.indexedHeaders.size());
        assertEquals("foo", parsed.indexedHeaders.get("x-something").iterator().next(), "header value");
    }

    @Test
    void parseQuery() {
        EntryParser<E> parser = createParser();
        Multimap<String, String> q = parser.parseQuery(URI.create("http://example.com/hello"));
        assertNull(q);
        q = parser.parseQuery(URI.create("http://example.com/hello?foo=bar"));
        assertEquals(ImmutableMultimap.of("foo", "bar"), q);
        q = parser.parseQuery(URI.create("http://example.com/hello?"));
        assertEquals(ImmutableMultimap.of(), q);
    }
}
