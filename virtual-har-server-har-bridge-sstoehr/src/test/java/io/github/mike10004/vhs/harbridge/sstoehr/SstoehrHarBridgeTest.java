package io.github.mike10004.vhs.harbridge.sstoehr;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.io.ByteSource;
import com.google.common.net.MediaType;
import com.google.gson.Gson;
import de.sstoehr.harreader.model.HarContent;
import de.sstoehr.harreader.model.HarEntry;
import de.sstoehr.harreader.model.HarRequest;
import de.sstoehr.harreader.model.HarResponse;
import de.sstoehr.harreader.model.HttpMethod;
import io.github.mike10004.vhs.harbridge.HarBridge;
import io.github.mike10004.vhs.harbridge.HarBridge.ResponseData;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
        assertEquals(0, responseData.headers().count());
    }

    @Test
    public void getRequestPostData() throws Exception {
        assertNull(bridge.getRequestPostData(entry));
    }

    @Test
    public void getResponseStatus() throws Exception {
        assertEquals(200, bridge.getResponseStatus(entry));
    }

    @Test
    public void getRequestPostData_params() throws Exception {
        String json = "{\n" +
                "  \"method\": \"POST\",\n" +
                "  \"url\": \"https://www.example.com/hello/world?foo=438989901\",\n" +
                "  \"httpVersion\": \"HTTP/1.1\",\n" +
                "  \"headers\": [\n" +
                "    {\n" +
                "      \"name\": \"Host\",\n" +
                "      \"value\": \"www.example.com\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"Connection\",\n" +
                "      \"value\": \"keep-alive\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"User-Agent\",\n" +
                "      \"value\": \"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"content-type\",\n" +
                "      \"value\": \"application/x-www-form-urlencoded;charset\\u003dUTF-8\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"queryString\": [\n" +
                "    {\n" +
                "      \"name\": \"csrfToken\",\n" +
                "      \"value\": \"ajax:123456789034567890\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"postData\": {\n" +
                "    \"mimeType\": \"application/x-www-form-urlencoded;charset\\u003dUTF-8\",\n" +
                "    \"params\": [\n" +
                "      {\n" +
                "        \"name\": \"plist\",\n" +
                "        \"value\": \"eeny/meeny/miny/mo\",\n" +
                "        \"comment\": \"\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"comment\": \"\"\n" +
                "  },\n" +
                "  \"headersSize\": 993,\n" +
                "  \"bodySize\": 1996,\n" +
                "  \"comment\": \"\"\n" +
                "}";
        HarEntry harEntry = new HarEntry();
        HarRequest request = new Gson().fromJson(json, HarRequest.class);
        harEntry.setRequest(request);
        ByteSource postData = new SstoehrHarBridge().getRequestPostData(harEntry);
        assertNotNull("post data", postData);
        HttpEntity entity = makeEntity(postData, MediaType.FORM_DATA.withCharset(StandardCharsets.UTF_8));
        List<NameValuePair> params = URLEncodedUtils.parse(entity);
        assertEquals("num params", 1, params.size());
        NameValuePair param = params.iterator().next();
        assertEquals("name", "plist", param.getName());
        assertEquals("value", "eeny/meeny/miny/mo", param.getValue());
    }

    private static HttpEntity makeEntity(ByteSource byteSource, MediaType mediaType) throws IOException {
        ContentType contentType = ContentType.create(mediaType.withoutParameters().toString(), mediaType.charset().orNull());
        HttpEntity entity = new ByteArrayEntity(byteSource.read(), contentType);
        return entity;
    }

    @Test
    public void getContenttype_absent() throws Exception {
        HarBridge<HarEntry> bridge = new SstoehrHarBridge();
        HarEntry entry = new HarEntry();
        HarResponse response = new HarResponse();
        HarContent content = new HarContent();
        response.setContent(content);
        entry.setResponse(response);
        ParsedRequest request = ParsedRequest.inMemory(io.github.mike10004.vhs.harbridge.HttpMethod.GET, URI.create("https://www.example.com/"), null, ImmutableMultimap.of(), null);
        ResponseData actual = bridge.getResponseData(request, entry);
        assertEquals("body", 0, actual.body.read().length);
        assertEquals("content-type", HarBridge.CONTENT_TYPE_SUBSTITUTE_VALUE, actual.contentType);
        assertEquals("", 0, actual.headers().count());

    }
}