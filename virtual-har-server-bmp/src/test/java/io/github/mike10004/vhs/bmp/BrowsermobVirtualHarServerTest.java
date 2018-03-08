package io.github.mike10004.vhs.bmp;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.common.net.HostAndPort;
import com.machinepublishers.jbrowserdriver.JBrowserDriver;
import com.machinepublishers.jbrowserdriver.ProxyConfig;
import com.machinepublishers.jbrowserdriver.Settings;
import de.sstoehr.harreader.HarReaderException;
import de.sstoehr.harreader.model.HarEntry;
import io.github.mike10004.vhs.EntryMatcher;
import io.github.mike10004.vhs.EntryMatcherFactory;
import io.github.mike10004.vhs.EntryParser;
import io.github.mike10004.vhs.HarBridgeEntryParser;
import io.github.mike10004.vhs.VirtualHarServer;
import io.github.mike10004.vhs.VirtualHarServerControl;
import io.github.mike10004.vhs.bmp.KeystoreGenerator.KeystoreType;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import io.github.mike10004.vhs.harbridge.sstoehr.SstoehrHarBridge;
import io.github.mike10004.vhs.testsupport.VirtualHarServerTestBase;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import net.lightbody.bmp.core.har.HarRequest;
import net.lightbody.bmp.mitm.TrustSource;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContexts;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
    protected VirtualHarServer createServer(int port, File harFile, EntryMatcherFactory entryMatcherFactory, TestContext context) throws IOException {
        BrowsermobVhsConfig config = createServerConfig(port, harFile, entryMatcherFactory, context);
        return new BrowsermobVirtualHarServer(config);
    }

    protected BrowsermobVhsConfig createServerConfig(int port, File harFile, EntryMatcherFactory entryMatcherFactory, TestContext context) throws IOException {
        List<HarEntry> entries;
        try {
            entries = new de.sstoehr.harreader.HarReader().readFromFile(harFile).getLog().getEntries();
        } catch (HarReaderException e) {
            throw new IOException(e);
        }
        EntryParser<HarEntry> parser = new HarBridgeEntryParser<>(new SstoehrHarBridge());
        EntryMatcher entryMatcher = entryMatcherFactory.createEntryMatcher(entries, parser);
        HarReplayManufacturer responseManufacturer = new HarReplayManufacturer(entryMatcher, Collections.emptyList(), newLoggingResponseListener()) {
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
        TlsMode tlsMode = context.get(KEY_TLS_MODE);
        if (tlsMode == TlsMode.SUPPORT_REQUIRED || tlsMode == TlsMode.PREDEFINED_CERT_SUPPORT) {
            try {
                KeystoreData keystoreData = new KeystoreGenerator(KeystoreType.PKCS12).generate();
                SSLServerSocketFactory serverSocketFactory = NanohttpdTlsEndpointFactory.createSSLServerSocketFactory(keystoreData);
                TrustSource trustSource = NanohttpdTlsEndpointFactory.createTrustSource(keystoreData);
                configBuilder.tlsEndpointFactory(new NanohttpdTlsEndpointFactory(serverSocketFactory, trustSource, null));
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        }
        if (tlsMode == TlsMode.PREDEFINED_CERT_SUPPORT) {
            KeystoreData keystoreData = context.get(KEY_KEYSTORE_DATA);
            configBuilder.certificateAndKeySource(keystoreData.asCertificateAndKeySource());
        }
        return configBuilder.build();
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

    private static final String KEY_KEYSTORE_DATA = "keystoreData";

    @Test
    public void httpsTest_pregeneratedCertificate() throws Exception {
        KeystoreGenerator keystoreGenerator = new KeystoreGenerator(KeystoreType.PKCS12);
        KeystoreData keystoreData = keystoreGenerator.generate();
        CertAwareClient client = new CertAwareClient(keystoreData);
        checkSelfSignedRequiresTrustConfig(client);
        TestContext context = new TestContext();
        context.put(KEY_CLIENT_SUPPLIER, Suppliers.ofInstance(client));
        context.put(KEY_TLS_MODE, TlsMode.PREDEFINED_CERT_SUPPORT);
        context.put(KEY_KEYSTORE_DATA, keystoreData);
        doHttpsTest(context);
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

    private static class ErrorResponseNotice {

        public final ParsedRequest request;
        public final int status;

        private ErrorResponseNotice(ParsedRequest request, int status) {
            this.request = request;
            this.status = status;
        }

        @Override
        public String toString() {
            return "ErrorResponseNotice{" +
                    "request=" + request +
                    ", status=" + status +
                    '}';
        }
    }

    @Test
    public void javascriptRedirect() throws Exception {
        org.apache.http.client.utils.URLEncodedUtils.class.getName();
        org.littleshoot.proxy.impl.ClientToProxyConnection.class.getName();
        io.github.mike10004.vhs.harbridge.Hars.class.getName();
        File harFile = File.createTempFile("javascript-redirect", ".har", temporaryFolder.getRoot());
        Resources.asByteSource(getClass().getResource("/javascript-redirect.har")).copyTo(Files.asByteSink(harFile));
        URI startUrl = URI.create("https://www.redi123.com/");
        URI finalUrl = new URIBuilder(startUrl).setPath("/other.html").build();
        List<ErrorResponseNotice> errorNotices = new ArrayList<>();
        BmpResponseListener errorResponseAccumulator = new BmpResponseListener() {
            @Override
            public void respondingWithError(ParsedRequest request, int status) {
                System.out.format("responding %s to %s %s%n", status, request.method, request.url);
                errorNotices.add(new ErrorResponseNotice(request, status));
            }
        };
        HarReplayManufacturer manufacturer = BmpTests.createManufacturer(harFile, Collections.emptyList(), errorResponseAccumulator);
        KeystoreData keystoreData = new KeystoreGenerator(KeystoreType.PKCS12).generate();
        SSLServerSocketFactory serverSocketFactory = NanohttpdTlsEndpointFactory.createSSLServerSocketFactory(keystoreData);
        TrustSource trustSource = NanohttpdTlsEndpointFactory.createTrustSource(keystoreData);
        BrowsermobVhsConfig config = BrowsermobVhsConfig.builder(manufacturer)
                .scratchDirProvider(ScratchDirProvider.under(temporaryFolder.getRoot().toPath()))
                .tlsEndpointFactory(new NanohttpdTlsEndpointFactory(serverSocketFactory, trustSource, null))
                .build();
        VirtualHarServer server = new BrowsermobVirtualHarServer(config);
        String finalPageSource = null;
        try (VirtualHarServerControl ctrl = server.start()) {
            HostAndPort address = ctrl.getSocketAddress();
            ProxyConfig proxyConfig = new ProxyConfig(ProxyConfig.Type.HTTP, "localhost", address.getPort());
            Settings settings = Settings.builder()
                    .hostnameVerification(false)
                    .ssl("trustanything")
                    .proxy(proxyConfig)
                    .build();
            WebDriver driver = new JBrowserDriver(settings);
            try {
                driver.get(startUrl.toString());
                System.out.println(driver.getPageSource());
                try {
                    new WebDriverWait(driver, REDIRECT_WAIT_TIMEOUT_SECONDS).until(ExpectedConditions.urlToBe(finalUrl.toString()));
                    finalPageSource = driver.getPageSource();
                } catch (org.openqa.selenium.TimeoutException e) {
                    System.err.format("timed out while waiting for URL to change to %s%n", finalUrl);
                }
            } finally {
                driver.quit();
            }
        }
        System.out.format("final page source:%n%s%n", finalPageSource);
        errorNotices.forEach(System.out::println);
        assertNotNull("final page source", finalPageSource);
        assertTrue("redirect text", finalPageSource.contains(EXPECTED_FINAL_REDIRECT_TEXT));
        assertEquals("error notices", ImmutableList.of(), errorNotices);
    }

    private static final int REDIRECT_WAIT_TIMEOUT_SECONDS = 10; // JBrowserDriver can be a tad slow to execute JavaScript
    private static final String EXPECTED_FINAL_REDIRECT_TEXT = "This is the redirect destination page";

    private static BmpResponseListener newLoggingResponseListener() {
        return new BmpResponseListener() {
            @Override
            public void respondingWithError(ParsedRequest request, int status) {
                System.out.format("responding with status %s to %s %s", status, request.method, request.url);
            }
        };
    }

    @Test
    public void https_rejectUpstreamBadCertificate() {
        fail("not yet implemented");
    }
}