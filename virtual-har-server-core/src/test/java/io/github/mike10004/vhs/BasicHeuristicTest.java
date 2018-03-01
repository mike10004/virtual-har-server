package io.github.mike10004.vhs;

import com.opencsv.CSVReader;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(Enclosed.class)
public class BasicHeuristicTest {

    private interface ParameterSource<T> {
        @SuppressWarnings("unused")
        List<T> produce() throws Exception ;
    }

    private static class CsvSource implements ParameterSource<Object[]> {
        public final String[] rows;


        public CsvSource(String...rows) {
            this.rows = rows;
        }

        @Override
        public List<Object[]> produce() throws IOException {
            String csv = String.join(System.lineSeparator(), rows);
            try (CSVReader reader = new CSVReader(new StringReader(csv))) {
                return reader.readAll().stream().map(sarray -> {
                    for (int i = 0; i < sarray.length; i++) {
                        if (sarray[i] != null) {
                            sarray[i] = sarray[i].trim();
                        }
                    }
                    Object[] copy = new Object[sarray.length];
                    System.arraycopy(sarray, 0, copy, 0, sarray.length);
                    return copy;
                }).collect(Collectors.toList());
            }
        }
    }

    @RunWith(Parameterized.class)
    public static class RateZeroTest {

        private final String method, url;

        public RateZeroTest(String method, String url) {
            this.method = method;
            this.url = url;
        }

        @Parameters
        public static List<Object[]> params() throws IOException {
            return new CsvSource(
                    "GET, http://wrongdomain.com/correct/path",
                    "GET, http://rightdomain.com/wrong/path",
                    "POST, http://rightdomain.com/correct/path",
                    "PATCH, http://rightdomain.com/correct/path",
                    "DELETE, http://rightdomain.com/correct/path",
                    "OPTIONS, http://rightdomain.com/correct/path",
                    "HEAD, http://rightdomain.com/correct/path",
                    "PUT, http://rightdomain.com/correct/path"
            ).produce();
        }

        @Test
        public void rate_zero() {
            BasicHeuristic h = new BasicHeuristic();
            ParsedRequest entryRequest = Tests.createRequest(method, url);
            ParsedRequest request = Tests.createRequest("GET", "http://rightdomain.com/correct/path");
            int rating = h.rate(entryRequest, request);
            assertEquals("rating should be zero", 0, rating);
        }
    }

    @RunWith(Parameterized.class)
    public static class RateAboveDefaultThresholdTest {

        private final String method, url;

        public RateAboveDefaultThresholdTest(String method, String url) {
            this.method = method;
            this.url = url;
        }

        @Parameters
        public static List<Object[]> params() throws Exception {
            return new CsvSource(
                    "GET, http://rightdomain.com/correct/path",
                    "GET, https://rightdomain.com/correct/path",
                    "GET, http://rightdomain.com/correct/path",
                    "GET, http://rightdomain.com/correct/path?foo=bar",
                    "GET, http://rightdomain.com/correct/path?"
            ).produce();
        }

        @Test
        public void rate_aboveDefaultThreshold() {
            BasicHeuristic h = new BasicHeuristic();
            ParsedRequest entryRequest = Tests.createRequest(method, url);
            ParsedRequest request = Tests.createRequest("GET", "http://rightdomain.com/correct/path");
            int rating = h.rate(entryRequest, request);
            assertTrue("expected rating above default threshold: " + rating, rating > BasicHeuristic.DEFAULT_THRESHOLD_EXCLUSIVE);
        }
    }

}

