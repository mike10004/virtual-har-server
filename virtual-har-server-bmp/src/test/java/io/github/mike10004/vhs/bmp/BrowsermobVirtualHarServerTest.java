package io.github.mike10004.vhs.bmp;

import com.google.common.net.HostAndPort;
import de.sstoehr.harreader.HarReaderException;
import de.sstoehr.harreader.model.HarEntry;
import io.github.mike10004.vhs.EntryMatcher;
import io.github.mike10004.vhs.EntryMatcherFactory;
import io.github.mike10004.vhs.EntryParser;
import io.github.mike10004.vhs.HarBridgeEntryParser;
import io.github.mike10004.vhs.VirtualHarServer;
import io.github.mike10004.vhs.bmp.KeystoreGenerator.KeystoreType;
import io.github.mike10004.vhs.harbridge.sstoehr.SstoehrHarBridge;
import io.github.mike10004.vhs.testsupport.VirtualHarServerTestBase;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import net.lightbody.bmp.core.har.HarRequest;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContexts;
import org.junit.Before;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertEquals;

public class BrowsermobVirtualHarServerTest extends VirtualHarServerTestBase {

    private List<String> requests;
    private List<String> customValues;

    @Before
    public void setUp() throws IOException {
        customValues = Collections.synchronizedList(new ArrayList<>());
        requests = Collections.synchronizedList(new ArrayList<>());
    }

    private static final String CUSTOM_HEADER_NAME = "X-Virtual-Har-Server-Unit-Test";

    @Override
    protected VirtualHarServer createServer(int port, File harFile, EntryMatcherFactory entryMatcherFactory) throws IOException {
        return createServer(port, harFile, entryMatcherFactory, b -> {});
    }

    protected VirtualHarServer createServer(int port, File harFile, EntryMatcherFactory entryMatcherFactory, Consumer<? super BrowsermobVhsConfig.Builder> configBuilderModifier) throws IOException {
        List<HarEntry> entries;
        try {
            entries = new de.sstoehr.harreader.HarReader().readFromFile(harFile).getLog().getEntries();
        } catch (HarReaderException e) {
            throw new IOException(e);
        }
        EntryParser<HarEntry> parser = new HarBridgeEntryParser<>(new SstoehrHarBridge());
        EntryMatcher entryMatcher = entryMatcherFactory.createEntryMatcher(entries, parser);
        HarReplayManufacturer responseManufacturer = new HarReplayManufacturer(entryMatcher, Collections.emptyList()) {
            @Override
            public HttpResponse manufacture(HttpRequest originalRequest, HarRequest fullCapturedRequest) {
                requests.add(String.format("%s %s", fullCapturedRequest.getMethod(), fullCapturedRequest.getUrl()));
                return super.manufacture(originalRequest, fullCapturedRequest);
            }
        };
        Path scratchParent = temporaryFolder.getRoot().toPath();
        BmpResponseFilter responseFilter = new HeaderAddingFilter(CUSTOM_HEADER_NAME, () -> {
            String value = UUID.randomUUID().toString();
            customValues.add(value);
            return value;
        });
        BrowsermobVhsConfig.Builder configBuilder = BrowsermobVhsConfig.builder(responseManufacturer)
                .port(port)
                .proxyToClientResponseFilter(responseFilter)
                .scratchDirProvider(ScratchDirProvider.under(scratchParent));
        configBuilderModifier.accept(configBuilder);
        BrowsermobVhsConfig config = configBuilder.build();
        return new BrowsermobVirtualHarServer(config);
    }

    @Test
    @Override
    public void httpsTest() throws Exception {
        boolean clean = false;
        try {
            super.httpsTest();
            clean = true;
        } finally {
            System.out.format("%s requests handled%n", requests.size());
            requests.forEach(request -> {
                System.out.format("%s%n", request);
            });
            if (clean) {
                assertEquals("num requests", 1, requests.size());
                assertEquals("num responses", 1, customValues.size());
            }
        }
    }

    @Test
    public void httpsTest_pregeneratedCertificate() throws Exception {
        KeystoreGenerator keystoreGenerator = new KeystoreGenerator(KeystoreType.PKCS12);
        KeystoreData keystoreData = keystoreGenerator.generate();
        CertAwareClient client = new CertAwareClient(keystoreData);
        checkSelfSignedRequiresTrustConfig(client);
        ServerFactory serverFactory = (port, harFile, entryMatcherFactory) -> {
            return createServer(port, harFile, entryMatcherFactory, b -> {
                b.certificateAndKeySource(keystoreData.asCertificateAndKeySource());
            });
        };
        doHttpsTest(serverFactory, () -> client);
    }

    private void checkSelfSignedRequiresTrustConfig(ApacheRecordingClient client) throws Exception {
        try {
            client.collectResponses(Collections.singleton(URI.create("https://self-signed.badssl.com/")), null);
            throw new IllegalStateException("if we're here it means the client wrongly trusted an unknown self-signed certificate");
        } catch (javax.net.ssl.SSLHandshakeException ignore) {
        }
    }

    private static class CertAwareClient extends ApacheRecordingClient {

        private final KeystoreData keystoreData;

        public CertAwareClient(KeystoreData keystoreData) {
            super(false);
            this.keystoreData = requireNonNull(keystoreData);
        }

        @Override
        protected void configureHttpClientBuilder(HttpClientBuilder b, HostAndPort proxy) throws Exception {
            super.configureHttpClientBuilder(b, proxy);
            KeyStore keyStore = keystoreData.loadKeystore();
            SSLContext customSslContext = SSLContexts.custom()
                    .loadTrustMaterial(keyStore, null)
                    .build();
            b.setSSLContext(customSslContext);
            b.setSSLHostnameVerifier(blindHostnameVerifier());
        }
    }
}