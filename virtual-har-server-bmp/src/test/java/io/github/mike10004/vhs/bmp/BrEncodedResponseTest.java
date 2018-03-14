package io.github.mike10004.vhs.bmp;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.common.net.HostAndPort;
import de.sstoehr.harreader.HarReader;
import de.sstoehr.harreader.model.Har;
import io.github.bonigarcia.wdm.ChromeDriverManager;
import io.github.mike10004.vhs.VirtualHarServer;
import io.github.mike10004.vhs.VirtualHarServerControl;
import io.github.mike10004.vhs.harbridge.HttpContentCodec;
import io.github.mike10004.vhs.harbridge.HttpContentCodecs;
import org.apache.http.HttpHeaders;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BrEncodedResponseTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass
    public static void setupWebdriver() {
        ChromeDriverManager.getInstance().version("2.36").setup();
    }

    private interface Client {
        String fetch(HostAndPort proxyAddress, URI url) throws Exception;
    }

    @Test
    public void seeBrEncodedResponse_chrome() throws Exception {
        seeBrEncodedResponse((proxyAddress, url) -> {
            Map<String, String> env = new HashMap<>();
            ChromeDriverService service = new ChromeDriverService.Builder()
                    .usingAnyFreePort()
                    .withEnvironment(env)
                    .build();
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--proxy-server=" + proxyAddress);
            WebDriver driver = new ChromeDriver(service, options);
            try {
                driver.get(url.toString());
                new java.util.concurrent.CountDownLatch(1).await();
                return driver.getPageSource();
            } finally {
                driver.quit();
            }
        });
    }

    @Test
    public void seeBrEncodedResponse_jre() throws Exception {
        seeBrEncodedResponse(((proxyAddress, url) -> {
            byte[] rawBytes;
            HttpURLConnection conn = (HttpURLConnection) url.toURL().openConnection(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyAddress.getHost(), proxyAddress.getPort())));
            try {
                conn.setRequestProperty(HttpHeaders.ACCEPT_ENCODING, "br");
                try (InputStream in = conn.getInputStream()) {
                    rawBytes = ByteStreams.toByteArray(in);
                }
            } finally {
                conn.disconnect();
            }
            HttpContentCodec brotliCodec = HttpContentCodecs.getCodec(HttpContentCodecs.CONTENT_ENCODING_BROTLI);
            checkState(brotliCodec != null);
            byte[] decompressed = brotliCodec.decompress(rawBytes);
            return new String(decompressed, StandardCharsets.UTF_8);
        }));
    }

    private void seeBrEncodedResponse(Client client) throws Exception {
        File harFile = temporaryFolder.newFile();
        Resources.asByteSource(getClass().getResource("/single-entry-br-encoding.har")).copyTo(Files.asByteSink(harFile));
        Har har = new HarReader().readFromFile(harFile);
        URI url = new URI("http://www.example.com/served-with-br-encoding");
        String expectedText = har.getLog().getEntries().get(0).getResponse().getContent().getText();

        BmpResponseListener responseListener = (request, status) -> {
            System.out.format("ERROR RESPONSE %s to %s%n", status, request);
        };
        BmpResponseManufacturer responseManufacturer = BmpTests.createManufacturer(harFile, ImmutableList.of(), responseListener);
        BrowsermobVhsConfig vhsConfig = BrowsermobVhsConfig.builder(responseManufacturer)
                .scratchDirProvider(ScratchDirProvider.under(temporaryFolder.getRoot().toPath()))
                .build();
        VirtualHarServer harServer = new BrowsermobVirtualHarServer(vhsConfig);
        String actualText;
        try (VirtualHarServerControl ctrl = harServer.start()) {
            HostAndPort proxyAddress = ctrl.getSocketAddress();
            actualText = client.fetch(proxyAddress, url);
        }
        System.out.println("actual:");
        System.out.println(actualText);
        assertTrue("page source", actualText.contains(expectedText)); // browser wraps text in <html><body><pre>
    }
}
