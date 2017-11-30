package io.github.mike10004.vhs.nanohttpd;

import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.gson.GsonBuilder;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response;
import io.github.mike10004.nanochamp.server.NanoControl;
import io.github.mike10004.nanochamp.server.NanoResponse;
import io.github.mike10004.nanochamp.server.NanoServer;
import io.github.mike10004.nanochamp.server.NanoServer.ResponseProvider;
import io.github.mike10004.nanochamp.server.NanoServer.ServiceRequest;
import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.core.har.HarEntry;
import net.lightbody.bmp.core.har.HarNameValuePair;
import net.lightbody.bmp.core.har.HarRequest;
import net.lightbody.bmp.proxy.CaptureType;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class HarMaker {

    public static class EntrySpec {
        public final RequestSpec request;
        public final Duration preResponseDelay;
        public final NanoHTTPD.Response response;
        public final Duration postResponseDelay;

        public EntrySpec(RequestSpec request, NanoHTTPD.Response response) {
            this(request, Duration.ofMillis(50), response, Duration.ofMillis(50));
        }

        public EntrySpec(RequestSpec request, Duration preResponseDelay, Response response, Duration postResponseDelay) {
            this.request = request;
            this.preResponseDelay = preResponseDelay;
            this.response = response;
            this.postResponseDelay = postResponseDelay;
        }

        private void provideBody(HttpRequestBase request) {
            if ("POST".equalsIgnoreCase(this.request.method) || "PUT".equalsIgnoreCase(this.request.method)) {
                if (this.request.body != null) {
                    ((HttpEntityEnclosingRequestBase) request).setEntity(new ByteArrayEntity(this.request.body));
                }
            }
        }

        public HttpUriRequest toHttpUriRequest(HostAndPort localSocket, UUID id) throws URISyntaxException {
            URI uri = new URIBuilder(request.url)
                    .setHost(localSocket.getHost())
                    .setPort(localSocket.getPort())
                    .setScheme("http").build();
            HttpRequestBase base;
            switch (request.method.toUpperCase()) {
                case "GET":
                    base = new HttpGet(uri);
                    break;
                case "POST":
                    base = new HttpPost(uri);
                    break;
                case "DELETE":
                    base = new HttpDelete(uri);
                    break;
                case "PUT":
                    base = new HttpPut(uri);
                    break;
                default:
                    throw new UnsupportedOperationException("not supported: " + request.method);
            }
            provideBody(base);
            this.request.headers.forEach(base::addHeader);
            base.addHeader(HEADER_ID, id.toString());
            return base;
        }
    }

    public void produceHarFile(List<EntrySpec> specs, File outputHarFile) throws IOException, URISyntaxException {
        produceHar(specs).writeTo(outputHarFile);
    }

    public Har produceHar(List<EntrySpec> specs) throws IOException, URISyntaxException {
        Map<UUID, EntrySpec> specsWithIds = new LinkedHashMap<>();
        specs.forEach(spec -> {
            specsWithIds.put(UUID.randomUUID(), spec);
        });
        AtomicInteger responsesSent = new AtomicInteger(0), requestsSent = new AtomicInteger(0);
        NanoServer server = NanoServer.builder().handle(new ResponseProvider() {
            @Nullable
            @Override
            public Response serve(ServiceRequest request) {
                String id = request.headers.apply(HEADER_ID);
                checkArgument(id != null, "expect every request to have %s header", HEADER_ID);
                EntrySpec spec = specsWithIds.get(UUID.fromString(id));
                checkState(spec != null, "no spec for id %s", id);
                Uninterruptibles.sleepUninterruptibly(spec.preResponseDelay.toMillis(), TimeUnit.MILLISECONDS);
                responsesSent.incrementAndGet();
                return spec.response;
            }
        })
                .httpdFactory(new UnadulteratedNanoHttpdFactory())
                .build();
        try (NanoControl control = server.startServer()) {
            BrowserMobProxy proxy = new BrowserMobProxyServer();
            proxy.enableHarCaptureTypes(EnumSet.allOf(CaptureType.class));
            proxy.newHar();
            proxy.start();
            try (CloseableHttpClient client = HttpClients.custom()
                    .setProxy(new HttpHost("localhost", proxy.getPort()))
                    .build()) {
                for (Map.Entry<UUID, EntrySpec> specWithId : specsWithIds.entrySet()) {
                    EntrySpec spec = specWithId.getValue();
                    HttpUriRequest httpUriRequest = spec.toHttpUriRequest(control.getSocketAddress(), specWithId.getKey());
                    try (CloseableHttpResponse response = client.execute(httpUriRequest)) {
                        EntityUtils.consume(response.getEntity());
                        requestsSent.incrementAndGet();
                    }
                    Uninterruptibles.sleepUninterruptibly(spec.postResponseDelay.toMillis(), TimeUnit.MILLISECONDS);
                }
            } finally {
                proxy.stop();
            }
            checkState(responsesSent.get() == requestsSent.get(), "responses/requests diff: %s != %s", responsesSent.get(), requestsSent.get());
            Har har = proxy.getHar();
            cleanTraffic(har, specsWithIds);
            return har;
        }
    }

    private static final String HEADER_ID = "X-Virtual-Har-Server-Id";

    private HarNameValuePair removeHeaderByName(HarRequest request, String headerName) {
        List<HarNameValuePair> headers = request.getHeaders();
        int index = -1;
        for (int i = 0; i < headers.size(); i++) {
            if (headerName.equalsIgnoreCase(headers.get(i).getName())) {
                index = i;
            }
        }
        if (index == -1) {
            throw new IllegalArgumentException("no header by name " + headerName);
        }
        HarNameValuePair header = headers.get(index);
        headers.remove(index);
        return header;
    }

    private void cleanTraffic(Har har, Map<UUID, EntrySpec> specs) throws IOException {
        Objects.requireNonNull(har, "har");
        Objects.requireNonNull(har.getLog(), "har.log");
        Objects.requireNonNull(har.getLog().getEntries(), "har.log.entries");
        List<HarEntry> entries = har.getLog().getEntries();
        for (HarEntry entry : entries) {
            HarRequest request = entry.getRequest();
            HarNameValuePair idHeader = removeHeaderByName(request, HEADER_ID);
            String id = idHeader.getValue();
            checkState(id != null, "expect each request to have header %s", HEADER_ID);
            URI realUri = specs.get(UUID.fromString(id)).request.url;
            request.setUrl(realUri.toString());
        }
    }

    static class HarMakerTest {
        @Test
        void makeHar() throws Exception {
            List<EntrySpec> specs = Arrays.asList(
                    new EntrySpec(RequestSpec.get(URI.create("http://example.com/one")), NanoResponse.status(200).plainTextUtf8("one")),
                    new EntrySpec(RequestSpec.get(URI.create("http://example.com/two")), NanoResponse.status(200).plainTextUtf8("two"))
            );
            HarMaker maker = new HarMaker();
            Har har = maker.produceHar(specs);
            assertEquals(specs.size(), har.getLog().getEntries().size(), "num entries");
            new GsonBuilder().setPrettyPrinting().create().toJson(har, System.out);
            System.out.println();
        }
    }
}
