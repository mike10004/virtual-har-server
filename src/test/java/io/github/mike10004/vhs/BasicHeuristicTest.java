package io.github.mike10004.vhs;

import com.google.common.collect.ImmutableMultimap;
import fi.iki.elonen.NanoHTTPD;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicHeuristicTest {

    @ParameterizedTest
    @CsvSource({
            "GET, http://wrongdomain.com/correct/path",
            "GET, http://rightdomain.com/wrong/path",
            "POST, http://rightdomain.com/correct/path",
            "PATCH, http://rightdomain.com/correct/path",
            "DELETE, http://rightdomain.com/correct/path",
            "OPTIONS, http://rightdomain.com/correct/path",
            "HEAD, http://rightdomain.com/correct/path",
            "PUT, http://rightdomain.com/correct/path",
    })
    void rate_zero(String method, String url) {
        BasicHeuristic h = new BasicHeuristic();
        ParsedRequest entryRequest = createRequest(method, url);
        ParsedRequest request = createRequest("GET", "http://rightdomain.com/correct/path");
        int rating = h.rate(entryRequest, request);
        assertEquals(0, rating, "rating should be zero");
    }

    @ParameterizedTest
    @CsvSource({
            "GET, http://rightdomain.com/correct/path",
            "GET, https://rightdomain.com/correct/path",
            "GET, http://rightdomain.com/correct/path",
            "GET, http://rightdomain.com/correct/path?foo=bar",
            "GET, http://rightdomain.com/correct/path?",
    })
    void rate_aboveDefaultThreshold(String method, String url) {
        BasicHeuristic h = new BasicHeuristic();
        ParsedRequest entryRequest = createRequest(method, url);
        ParsedRequest request = createRequest("GET", "http://rightdomain.com/correct/path");
        int rating = h.rate(entryRequest, request);
        assertTrue(rating > BasicHeuristic.DEFAULT_THRESHOLD_EXCLUSIVE, () -> "expected rating above default threshold: " + rating);
    }

    protected ParsedRequest createRequest(String method, String url) {
        return new ParsedRequest.MemoryRequest(NanoHTTPD.Method.valueOf(method), URI.create(url), null, ImmutableMultimap.of(), null);
    }

}