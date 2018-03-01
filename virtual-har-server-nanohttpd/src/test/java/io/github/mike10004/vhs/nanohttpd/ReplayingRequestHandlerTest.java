package io.github.mike10004.vhs.nanohttpd;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.common.net.HostAndPort;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import de.sstoehr.harreader.model.HarEntry;
import io.github.mike10004.nanochamp.repackaged.fi.iki.elonen.NanoHTTPD;
import io.github.mike10004.nanochamp.repackaged.fi.iki.elonen.NanoHTTPD.IHTTPSession;
import io.github.mike10004.nanochamp.repackaged.fi.iki.elonen.NanoHTTPD.Response;
import io.github.mike10004.nanochamp.server.NanoControl;
import io.github.mike10004.nanochamp.server.NanoResponse;
import io.github.mike10004.nanochamp.server.NanoServer;
import io.github.mike10004.vhs.BasicHeuristic;
import io.github.mike10004.vhs.EntryMatcher;
import io.github.mike10004.vhs.EntryParser;
import io.github.mike10004.vhs.HarBridgeEntryParser;
import io.github.mike10004.vhs.HeuristicEntryMatcher;
import io.github.mike10004.vhs.harbridge.sstoehr.SstoehrHarBridge;
import io.github.mike10004.vhs.nanohttpd.HarMaker.EntrySpec;
import io.github.mike10004.vhs.testsupport.Tests;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

public class ReplayingRequestHandlerTest {

    private static final boolean debug = false;

    final static ImmutableList<EntrySpec> specs = ImmutableList.of(
            new EntrySpec(RequestSpec.get(URI.create("http://example.com/one")), NanoResponse.status(200).plainTextUtf8("one")),
            new EntrySpec(RequestSpec.get(URI.create("http://example.com/two")), NanoResponse.status(200).plainTextUtf8("two")),
            new EntrySpec(RequestSpec.get(URI.create("http://example.com/three.jpg")), NanoResponse.status(200).jpeg(loadResource("/cat.jpg")))
    );

    @SuppressWarnings("SameParameterValue")
    private static byte[] loadResource(String resourcePath) {
        try {
            return Resources.toByteArray(ReplayingRequestHandlerTest.class.getResource(resourcePath));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void dumpHar(File harFile) throws IOException {
        try (Reader reader = Files.asCharSource(harFile, UTF_8).openStream()) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            JsonElement json = (new JsonParser().parse(reader));
            abbreviate(json);
            gson.toJson(json, System.out);
        }
        System.out.println();
    }

    @Test
    public void serve() throws Exception {
        File harFile = Tests.getReplayTest1HarFile(FileUtils.getTempDirectory().toPath());
        if (debug) dumpHar(harFile);
        List<de.sstoehr.harreader.model.HarEntry> entries = new de.sstoehr.harreader.HarReader().readFromFile(harFile).getLog().getEntries();
        EntryParser<HarEntry> parser = new HarBridgeEntryParser<>(new SstoehrHarBridge());
        EntryMatcher heuristic = HeuristicEntryMatcher.factory(new BasicHeuristic(), BasicHeuristic.DEFAULT_THRESHOLD_EXCLUSIVE).createEntryMatcher(entries, parser);
        List<RequestSpec> requestsToMake = specs.stream().map(s -> s.request).collect(Collectors.toList());
        requestsToMake.add(RequestSpec.get(URI.create("http://example.com/three-not-found")));
        AtomicInteger counter = new AtomicInteger(0);
        ReplayingRequestHandler requestHandler = new ReplayingRequestHandler(heuristic, ResponseManager.identity()) {
            @Nullable
            @Override
            public Response serve(IHTTPSession session) {
                NanoHTTPD.Response response = super.serve(session);
                counter.incrementAndGet();
                if (response != null) {
                    System.out.format("response %d: %s %s %s%n", counter.get(), response.getStatus(), response.getRequestMethod(), session.getUri());
                } else {
                    System.out.format("response %d: no response created by ReplayingRequestHandler%n", counter.get());
                }
                return response;
            }
        };
        int NOT_FOUND_CODE = 404;
        NanoServer server = NanoServer.builder()
                .session(requestHandler)
                .httpdFactory(new UnadulteratedNanoHttpdFactory())
                .build();
        Multimap<RequestSpec, ImmutableHttpResponse> interactions = ArrayListMultimap.create();
        try (NanoControl proxyControl = server.startServer()) {
            HostAndPort proxyAddress = proxyControl.getSocketAddress();
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", proxyAddress.getPort()));
            for (int i = 0; i < requestsToMake.size(); i++) {
                RequestSpec requestSpec = requestsToMake.get(i);
                System.out.format("request %d: %s%n", i + 1, requestSpec.url);
                ImmutableHttpResponse capturedResponse = fetch(proxy, requestSpec.url);
                System.out.format("response content: %s%n", StringUtils.abbreviate(stringifyContent(capturedResponse), 128));
                interactions.put(requestSpec, capturedResponse);
            }
        }
        assertEquals("num interactions == num specs", requestsToMake.size(), interactions.size());
        interactions.forEach((request, response) -> {
            @Nullable EntrySpec entry = findEntrySpec(specs, request);
            if (entry != null) {
                assertEquals("response.status", entry.response.getStatus().getRequestStatus(), response.status);
            } else {
                assertEquals("response.status for unspec'd request", NOT_FOUND_CODE, response.status);
            }
        });
    }

    private static boolean isErrorCode(int statuscode) {
        return statuscode / 100 >= 4;
    }

    private static ImmutableHttpResponse fetch(Proxy proxy, URI url) throws IOException {
        checkArgument("http".equals(url.getScheme()) || "https".equals(url.getScheme()));
        HttpURLConnection conn = (HttpURLConnection) url.toURL().openConnection(proxy);
        try {
            int status = conn.getResponseCode();
            ImmutableHttpResponse.Builder b = ImmutableHttpResponse.builder(status);
            conn.getHeaderFields().forEach((name, values) -> {
                if (name != null) {
                    b.addHeaders(values.stream().filter(Objects::nonNull)
                            .map(value -> new SimpleImmutableEntry<>(name, value)));
                }
            });
            byte[] data;
            try (InputStream stream = isErrorCode(status) ? conn.getErrorStream() : conn.getInputStream()) {
                data = ByteStreams.toByteArray(stream);
            }
            b.content(ByteSource.wrap(data));
            return b.build();
        } finally {
            conn.disconnect();
        }
    }

    @Nullable
    private EntrySpec findEntrySpec(Collection<EntrySpec> entries, RequestSpec request) {
        return entries.stream().filter(entry -> request == entry.request).findFirst().orElse(null);
    }

    private static final Charset CHARSET_OF_RESPONSE_TEXT_IN_HAR = StandardCharsets.US_ASCII;

    private static String stringifyContent(ImmutableHttpResponse capturedResponse) throws IOException {
        String ctype = capturedResponse.getFirstHeaderValue(HttpHeaders.CONTENT_TYPE);
        if (ctype != null && MediaType.parse(ctype).is(MediaType.ANY_TEXT_TYPE)) {
            return capturedResponse.getContentAsChars(CHARSET_OF_RESPONSE_TEXT_IN_HAR).read();
        } else {
            return Base64.getEncoder().encodeToString(capturedResponse.getContentAsBytes().read());
        }
    }

    private static JsonPrimitive abbreviatePrimitive(JsonPrimitive primitive) {
        if (primitive.isString()) {
            return new JsonPrimitive(StringUtils.abbreviateMiddle(primitive.getAsString(), "...", 64));
        } else {
            return primitive;
        }
    }

    private static void abbreviate(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            Set<Entry<String, JsonElement>> entries = new HashSet<>(object.entrySet());
            for (Entry<String, JsonElement> entry : entries) {
                if (entry.getValue().isJsonPrimitive()) {
                    object.add(entry.getKey(), abbreviatePrimitive(entry.getValue().getAsJsonPrimitive()));
                } else {
                    abbreviate(entry.getValue());
                }
            }
        }
        if (element.isJsonArray()) {
            JsonArray object = element.getAsJsonArray();
            for (int i = 0; i < object.size(); i++) {
                if (object.get(i).isJsonPrimitive()) {
                    object.set(i, abbreviatePrimitive(object.get(i).getAsJsonPrimitive()));
                } else {
                    abbreviate(object.get(i));
                }
            }
        }
    }

}