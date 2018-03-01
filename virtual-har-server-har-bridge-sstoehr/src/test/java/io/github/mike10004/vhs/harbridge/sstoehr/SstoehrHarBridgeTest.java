package io.github.mike10004.vhs.harbridge.sstoehr;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.net.MediaType;
import de.sstoehr.harreader.model.HarContent;
import de.sstoehr.harreader.model.HarEntry;
import de.sstoehr.harreader.model.HarRequest;
import de.sstoehr.harreader.model.HarResponse;
import de.sstoehr.harreader.model.HttpMethod;
import io.github.mike10004.vhs.harbridge.HarBridge;
import io.github.mike10004.vhs.harbridge.HarBridge.ResponseData;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SstoehrHarBridgeTest {

    private HarBridge<HarEntry> bridge = new SstoehrHarBridge();
    private HarEntry entry;
    private URI url = URI.create("https://www.example.com/");
    private MediaType contentType = MediaType.PLAIN_TEXT_UTF_8;
    private String responseText = "hello";
    @SuppressWarnings("ConstantConditions")
    private byte[] responseBody = responseText.getBytes(contentType.charset().get());

    @Before
    public void thing() throws Exception {
        entry = new HarEntry();
        HarRequest request = new HarRequest();
        request.setMethod(HttpMethod.GET);
        request.setUrl(url.toString());
        HarResponse response = new HarResponse();
        response.setStatus(200);
        response.setStatusText("OK");
        HarContent harContent = new HarContent();
        harContent.setMimeType(contentType.toString());
        harContent.setText(responseText);
        response.setBodySize((long) responseBody.length);
        response.setContent(harContent);
        entry.setRequest(request);
        entry.setResponse(response);
    }

    @Test
    public void getRequestMethod() throws Exception {
        assertEquals("method", HttpMethod.GET.name(), bridge.getRequestMethod(entry));
    }

    @Test
    public void getRequestUrl() throws Exception {
        assertEquals("url", url.toString(), bridge.getRequestUrl(entry));
    }

    @Test
    public void getRequestHeaders() throws Exception {
        assertEquals("request header count", 0L, bridge.getRequestHeaders(entry).count());
    }

    @Test
    public void getResponseData() throws Exception {
        ParsedRequest request = ParsedRequest.inMemory(io.github.mike10004.vhs.harbridge.HttpMethod.GET, URI.create("http://www.example.com/"), ImmutableMultimap.of(), ImmutableMultimap.of(), null);
        ResponseData responseData = bridge.getResponseData(request, entry);
        assertEquals("body size", responseBody.length, responseData.body.size());
        assertEquals(contentType, responseData.contentType);
        assertEquals(0, responseData.headers.count());
    }

    @Test
    public void getRequestPostData() throws Exception {
        assertNull(bridge.getRequestPostData(entry));
    }

    @Test
    public void getResponseStatus() throws Exception {
        assertEquals(200, bridge.getResponseStatus(entry));
    }
}