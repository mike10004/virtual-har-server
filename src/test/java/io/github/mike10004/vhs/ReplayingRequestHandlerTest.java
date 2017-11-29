package io.github.mike10004.vhs;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.google.common.net.HostAndPort;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import io.github.mike10004.nanochamp.server.NanoControl;
import io.github.mike10004.nanochamp.server.NanoResponse;
import io.github.mike10004.nanochamp.server.NanoServer;
import io.github.mike10004.vhs.HarMaker.EntrySpec;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReplayingRequestHandlerTest {

    final static ImmutableList<EntrySpec> specs = ImmutableList.of(
            new EntrySpec(RequestSpec.get(URI.create("http://example.com/one")), NanoResponse.status(200).plainTextUtf8("one")),
            new EntrySpec(RequestSpec.get(URI.create("http://example.com/two")), NanoResponse.status(200).plainTextUtf8("two"))
    );

    @Test
    void serve() throws Exception {
        File harFile = new File(getClass().getResource("/replay-test-1.har").toURI());
        try (Reader reader = Files.asCharSource(harFile, UTF_8).openStream()) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(new JsonParser().parse(reader), System.out);
        }
        System.out.println();
        Heuristic heuristic = BasicHeuristic.factory().createHeuristic(harFile);
        List<RequestSpec> requestsToMake = specs.stream().map(s -> s.request).collect(Collectors.toList());
        requestsToMake.add(RequestSpec.get(URI.create("http://example.com/three-not-found")));        ReplayingRequestHandler requestHandler = new ReplayingRequestHandler(heuristic, ResponseManager.createDefault());
        int NOT_FOUND_CODE = 404;
        NanoServer server = NanoServer.builder()
                .session(requestHandler)
                .httpdFactory(new UnadulteratedNanoHttpdFactory())
                .build();
        Multimap<RequestSpec, ImmutableHttpResponse> interactions = ArrayListMultimap.create();
        try (NanoControl proxyControl = server.startServer()) {
            HostAndPort proxyAddress = proxyControl.getSocketAddress();
            HttpClientBuilder clientBuilder = HttpClients.custom()
                    .setProxy(new HttpHost(proxyAddress.getHost(), proxyAddress.getPort(), "http"));
            try (CloseableHttpClient client = clientBuilder.build()) {
                for (RequestSpec requestSpec : requestsToMake) {
                    HttpUriRequest request = new HttpGet(requestSpec.url);
                    System.out.format("request: %s%n", request);
                    try (CloseableHttpResponse response = client.execute(request)) {
                        ImmutableHttpResponse capturedResponse = ImmutableHttpResponse.builder(response.getStatusLine().getStatusCode())
                                .addHeaders(Stream.of(response.getAllHeaders()).map(header -> {
                                    return new SimpleImmutableEntry<>(header.getName(), header.getValue());
                                })).content(toByteSource(response.getEntity()))
                                .build();
                        System.out.format("response: %s%n", response);
                        System.out.format("response content: %s%n", StringUtils.abbreviate(stringifyContent(capturedResponse), 128));
                        interactions.put(requestSpec, capturedResponse);
                    }
                }
            }
        }
        assertEquals(requestsToMake.size(), interactions.size(), "num interactions == num specs");
        interactions.forEach((request, response) -> {
            @Nullable EntrySpec entry = findEntrySpec(specs, request);
            if (entry != null) {
                assertEquals(entry.response.getStatus().getRequestStatus(), response.status, "response.status");
            } else {
                assertEquals(NOT_FOUND_CODE, response.status, "response.status for unspec'd request");
            }
        });
    }

    @Nullable
    private EntrySpec findEntrySpec(Collection<EntrySpec> entries, RequestSpec request) {
        return entries.stream().filter(entry -> request == entry.request).findFirst().orElse(null);
    }

    private String stringifyContent(ImmutableHttpResponse capturedResponse) throws IOException {
        String ctype = capturedResponse.getFirstHeaderValue(HttpHeaders.CONTENT_TYPE);
        if (ctype != null && MediaType.parse(ctype).is(MediaType.ANY_TEXT_TYPE)) {
            return capturedResponse.getContentAsChars().read();
        } else {
            return Base64.getEncoder().encodeToString(capturedResponse.getContentAsBytes().read());
        }
    }

    static ByteSource toByteSource(HttpEntity entity) throws IOException {
        byte[] bytes = EntityUtils.toByteArray(entity);
        return ByteSource.wrap(bytes); // for debugging, so we can look at the bytes here
    }
}