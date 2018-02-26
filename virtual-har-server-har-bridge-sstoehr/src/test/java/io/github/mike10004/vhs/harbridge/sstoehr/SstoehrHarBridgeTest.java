package io.github.mike10004.vhs.harbridge.sstoehr;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.net.MediaType;
import de.sstoehr.harreader.model.*;
import io.github.mike10004.vhs.harbridge.HarBridge;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Disabled
class SstoehrHarBridgeTest {

    private HarBridge<HarEntry> bridge = new SstoehrHarBridge();
    private HarEntry entry;
    private Multimap<String, String> requestHeaders, responseHeaders;

    @BeforeEach
    private void thing() throws Exception {
        entry = new HarEntry();
        HarRequest request = new HarRequest();
        request.setMethod(HttpMethod.GET);
        request.setUrl("https://www.example.com/");
        HarResponse response = new HarResponse();
        MediaType contentType = MediaType.PLAIN_TEXT_UTF_8;
        //noinspection ConstantConditions
        byte[] responseBody = "hello".getBytes(contentType.charset().get());
        response.setBodySize((long) responseBody.length);
        responseHeaders = ArrayListMultimap.create();
//        response.setHeaders();
    }

    @Test
    void getResponseData() throws Exception {
        bridge.getResponseData(null, entry);
    }

    @Test
    void getRequestMethod() throws Exception {
        
    }

    @Test
    void getRequestUrl() throws Exception {
    }

    @Test
    void getRequestHeaders() throws Exception {
    }

    @Test
    void getResponseHeaders() throws Exception {
    }

    @Test
    void getRequestPostData() throws Exception {
    }

    @Test
    void getResponseBody() throws Exception {
    }

    @Test
    void getResponseContentType() throws Exception {
    }

    @Test
    void getResponseStatus() throws Exception {
    }
}