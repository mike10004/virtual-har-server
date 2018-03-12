package io.github.mike10004.vhs;

import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.common.net.MediaType;
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

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

    public static class BodyComparisonTest {

        @Test
        public void isSameBody_formDatas_sameOrder() throws Exception {
            ByteSource body1 = CharSource.wrap("foo=bar&baz=gaw").asByteSource(UTF_8);
            ByteSource body2 = CharSource.wrap("foo=bar&baz=gaw").asByteSource(UTF_8);
            String contentType = MediaType.FORM_DATA.withCharset(UTF_8).toString();
            boolean same = BasicHeuristic.isSameBody(body1, contentType, body2, contentType);
            assertTrue("same body when parameters are in same order", same);
        }

        @Test
        public void isSameBody_formDatas_reordered() throws Exception {
            ByteSource body1 = CharSource.wrap("foo=bar&baz=gaw").asByteSource(UTF_8);
            ByteSource body2 = CharSource.wrap("baz=gaw&foo=bar").asByteSource(UTF_8);
            String contentType = MediaType.FORM_DATA.withCharset(UTF_8).toString();
            boolean same = BasicHeuristic.isSameBody(body1, contentType, body2, contentType);
            assertTrue("same body when parameters are reordered", same);
        }

        @Test
        public void isSameBody_formData_totallyNotEqual() throws Exception {
            ByteSource body1 = CharSource.wrap("foo=bar&baz=gaw").asByteSource(UTF_8);
            ByteSource body2 = CharSource.wrap("three=little&pigs=wolf").asByteSource(UTF_8);
            String contentType = MediaType.FORM_DATA.withCharset(UTF_8).toString();
            boolean same = BasicHeuristic.isSameBody(body1, contentType, body2, contentType);
            assertFalse("not same body", same);
        }

        @Test
        public void isSameBody_formData_slightlyNotEqual() throws Exception {
            ByteSource body1 = CharSource.wrap("foo=bar&baz=gaw").asByteSource(UTF_8);
            ByteSource body2 = CharSource.wrap("foo=bar&foo=bar&baz=gaw").asByteSource(UTF_8);
            String contentType = MediaType.FORM_DATA.withCharset(UTF_8).toString();
            boolean same = BasicHeuristic.isSameBody(body1, contentType, body2, contentType);
            assertFalse("not same body", same);
        }

        private static final String CHARSET_IGNORE_REASON = "form data is expected to be all ascii, it seems, even though that makes more sense for URLs than it does for message bodies";

        @org.junit.Ignore(CHARSET_IGNORE_REASON)
        @Test
        public void isSameBody_formData_diffCharsets() throws Exception {
            ByteSource body1 = CharSource.wrap("foo=bar&baz=ö").asByteSource(UTF_8);
            ByteSource body2 = CharSource.wrap("foo=bar&baz=ö").asByteSource(ISO_8859_1);
            String contentType1 = MediaType.FORM_DATA.withCharset(UTF_8).toString();
            String contentType2 = MediaType.FORM_DATA.withCharset(ISO_8859_1).toString();
            boolean same = BasicHeuristic.isSameBody(body1, contentType1, body2, contentType2);
            assertTrue("same body when parameters are and different charsets used", same);
        }

        @org.junit.Ignore(CHARSET_IGNORE_REASON)
        @Test
        public void isSameBody_formData_diffCharsets_reordered() throws Exception {
            ByteSource body1 = CharSource.wrap("foo=bar&baz=ö").asByteSource(UTF_8);
            ByteSource body2 = CharSource.wrap("baz=ö&foo=bar").asByteSource(ISO_8859_1);
            String contentType1 = MediaType.FORM_DATA.withCharset(UTF_8).toString();
            String contentType2 = MediaType.FORM_DATA.withCharset(ISO_8859_1).toString();
            boolean same = BasicHeuristic.isSameBody(body1, contentType1, body2, contentType2);
            assertTrue("same body when parameters are and different charsets used", same);
        }

        @Test
        public void isSameBody_octetStreams_yes() throws Exception {
            ByteSource body = CharSource.wrap("hello, world").asByteSource(UTF_8);
            String contentType = MediaType.OCTET_STREAM.toString();
            boolean same = BasicHeuristic.isSameBody(body, contentType, body, contentType);
            assertTrue("same body", same);
        }

        @Test
        public void isSameBody_octetStreams_no() throws Exception {
            ByteSource body1 = CharSource.wrap("hello, world").asByteSource(UTF_8);
            ByteSource body2 = CharSource.wrap("world, hello").asByteSource(UTF_8);
            String contentType = MediaType.OCTET_STREAM.toString();
            boolean same = BasicHeuristic.isSameBody(body1, contentType, body2, contentType);
            assertFalse("same body", same);
        }

        @Test
        public void isSameBody_bothEmpty_contentTypeAbsent() throws Exception {
            assertTrue("same body (empty)", BasicHeuristic.isSameBody(ByteSource.empty(), null, ByteSource.empty(), null));
        }

        @Test
        public void isSameBody_bothEmpty_contentTypeFormData() throws Exception {
            String ct = MediaType.FORM_DATA.toString();
            assertTrue("same body (empty)", BasicHeuristic.isSameBody(ByteSource.empty(), ct, ByteSource.empty(), ct));
        }

        @Test
        public void isSameBody_bothEmpty_contentTypeOctetStream() throws Exception {
            String contentType = MediaType.OCTET_STREAM.toString();
            assertTrue("same body (empty)", BasicHeuristic.isSameBody(ByteSource.empty(), contentType, ByteSource.empty(), contentType));
        }

        @Test
        public void isSameBody_bothEmpty_contentTypeDifferent() throws Exception {
            assertTrue("same body (empty)", BasicHeuristic.isSameBody(ByteSource.empty(), MediaType.PNG.toString(), ByteSource.empty(), MediaType.JPEG.toString()));
        }
    }
}

