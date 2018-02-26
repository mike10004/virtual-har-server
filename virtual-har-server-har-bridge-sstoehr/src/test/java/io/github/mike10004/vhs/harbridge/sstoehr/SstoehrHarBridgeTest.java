package io.github.mike10004.vhs.harbridge.sstoehr;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.net.MediaType;
import de.sstoehr.harreader.model.*;
import io.github.mike10004.vhs.harbridge.HarBridge;

import io.github.mike10004.vhs.harbridge.HarBridge.ResponseData;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class SstoehrHarBridgeTest {

    private HarBridge<HarEntry> bridge = new SstoehrHarBridge();
    private HarEntry entry;
    private URI url = URI.create("https://www.example.com/");
    private MediaType contentType = MediaType.PLAIN_TEXT_UTF_8;
    private String responseText = "hello";
    @SuppressWarnings("ConstantConditions")
    private byte[] responseBody = responseText.getBytes(contentType.charset().get());

    @BeforeEach
    private void thing() throws Exception {
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
    void getRequestMethod() throws Exception {
        assertEquals(HttpMethod.GET.name(), bridge.getRequestMethod(entry), "method");
    }

    @Test
    void getRequestUrl() throws Exception {
        assertEquals(url.toString(), bridge.getRequestUrl(entry), "url");
    }

    @Test
    void getRequestHeaders() throws Exception {
        assertEquals(0L, bridge.getRequestHeaders(entry).count(), "request header count");
    }

    @Test
    void getResponseData() throws Exception {
        ParsedRequest request = ParsedRequest.inMemory(io.github.mike10004.vhs.harbridge.HttpMethod.GET, URI.create("http://www.example.com/"), ImmutableMultimap.of(), ImmutableMultimap.of(), null);
        ResponseData responseData = bridge.getResponseData(request, entry);
        assertEquals(responseBody.length, responseData.body.size(), "body size");
        assertEquals(contentType, responseData.contentType);
        assertEquals(0, responseData.headers.count());
    }

    @Test
    void getRequestPostData() throws Exception {
        assertNull(bridge.getRequestPostData(entry));
    }

    @Test
    void getResponseStatus() throws Exception {
        assertEquals(200, bridge.getResponseStatus(entry));
    }
}