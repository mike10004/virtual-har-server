package io.github.mike10004.vhs;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class ParsedEntryTest {
    @Test
    void create() {
        fail("not yet implemented");
    }

    @Test
    void create1() {
        fail("not yet implemented");
    }

    @Test
    void parseQuery() {
        Multimap<String, String> q = ParsedEntry.parseQuery(URI.create("http://example.com/hello"));
        assertNull(q);
        q = ParsedEntry.parseQuery(URI.create("http://example.com/hello?foo=bar"));
        assertEquals(ImmutableMultimap.of("foo", "bar"), q);
        q = ParsedEntry.parseQuery(URI.create("http://example.com/hello?"));
        assertEquals(ImmutableMultimap.of(), q);
    }

}