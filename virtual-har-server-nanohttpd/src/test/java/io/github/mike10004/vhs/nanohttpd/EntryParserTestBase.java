package io.github.mike10004.vhs.nanohttpd;

import io.github.mike10004.vhs.EntryParser;
import io.github.mike10004.vhs.harbridge.HttpMethod;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import org.junit.jupiter.api.Assertions;
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
        Assertions.assertEquals(HttpMethod.GET, parsed.method, "method");
        assertEquals(URI.create(urlStr), parsed.url, "url");
        assertEquals(null, parsed.query, "query");
        assertEquals(2, parsed.indexedHeaders.size());
        assertEquals("foo", parsed.indexedHeaders.get("x-something").iterator().next(), "header value");
    }

}
