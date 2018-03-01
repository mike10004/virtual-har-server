package io.github.mike10004.vhs.testsupport;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.net.HostAndPort;
import io.github.mike10004.vhs.BasicHeuristic;
import io.github.mike10004.vhs.EntryMatcherFactory;
import io.github.mike10004.vhs.HeuristicEntryMatcher;
import io.github.mike10004.vhs.VirtualHarServer;
import io.github.mike10004.vhs.VirtualHarServerControl;
import org.apache.http.HttpHost;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class VirtualHarServerTestBase {

    protected abstract VirtualHarServer createServer(int port, File harFile, EntryMatcherFactory entryMatcherFactory) throws IOException;

    private static final URI oneUri = URI.create("http://example.com/one"),
            twoUri = URI.create("http://example.com/two"),
            notFoundUri = URI.create("http://example.com/not-found");

    @Test
    void basicTest() throws Exception {
        Path temporaryDirectory = java.nio.file.Files.createTempDirectory("VirtualHarServerTest");
        try {
            File harFile = Tests.getReplayTest1HarFile(temporaryDirectory);
            EntryMatcherFactory entryMatcherFactory = HeuristicEntryMatcher.factory(new BasicHeuristic(), BasicHeuristic.DEFAULT_THRESHOLD_EXCLUSIVE);
            int port = Tests.findOpenPort();
            VirtualHarServer server = createServer(port, harFile, entryMatcherFactory);
            Multimap<URI, ResponseSummary> responses;
            List<URI> uris = Arrays.asList(
                    oneUri, twoUri, notFoundUri
            );
            try (VirtualHarServerControl ctrl = server.start()) {
                ApacheRecordingClient client = new ApacheRecordingClient(false);
                responses = client.collectResponses(uris, ctrl.getSocketAddress());
            }
            examineResponses(uris, responses);
        } finally {
            TemporaryFolder.deleteDirectory(temporaryDirectory);
        }
    }

    protected void examineResponses(List<URI> attempted, Multimap<URI, ResponseSummary> responses) {
        assertTrue(responses.keySet().containsAll(attempted), "attempted all urls");
        ResponseSummary oneResponse = responses.get(oneUri).iterator().next();
        System.out.format("'%s' fetched from %s%n", oneResponse.entity, oneUri);
        assertEquals("one", oneResponse.entity, "response to " + oneUri);
    }

    private static class ApacheRecordingClient {

        private final boolean transformHttpsToHttp;

        ApacheRecordingClient(boolean transformHttpsToHttp) {
            this.transformHttpsToHttp = transformHttpsToHttp;
        }

        private URI transformUri(URI uri) { // equivalent of switcheroo extension
            if (transformHttpsToHttp && "https".equals(uri.getScheme())) {
                try {
                    return new URIBuilder(uri).setScheme("http").build();
                } catch (URISyntaxException e) {
                    throw new IllegalStateException(e);
                }
            } else {
                return uri;
            }
        }

        public Multimap<URI, ResponseSummary> collectResponses(Iterable<URI> urisToGet, HostAndPort proxy) throws Exception {
            Multimap<URI, ResponseSummary> result = ArrayListMultimap.create();
            try (CloseableHttpClient client = HttpClients.custom()
                    .setProxy(new HttpHost(proxy.getHost(), proxy.getPort()))
                    .setRetryHandler(new DefaultHttpRequestRetryHandler(0, false))
                    .build()) {
                for (URI uri : urisToGet) {
                    System.out.format("fetching %s%n", uri);
                    HttpGet get = new HttpGet(transformUri(uri));
                    try (CloseableHttpResponse response = client.execute(get)) {
                        StatusLine statusLine = response.getStatusLine();
                        String entity = EntityUtils.toString(response.getEntity());
                        result.put(uri, new ResponseSummary(statusLine, entity));
                    }
                }
            }
            return result;
        }
    }

    private static class ResponseSummary {
        public final StatusLine statusLine;
        public final String entity;

        private ResponseSummary(StatusLine statusLine, String entity) {
            this.statusLine = statusLine;
            this.entity = entity;
        }
    }
}
