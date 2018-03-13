package io.github.mike10004.vhs.bmp;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteSource;
import com.google.common.net.MediaType;
import io.github.mike10004.vhs.HttpRespondable;
import io.github.mike10004.vhs.harbridge.Hars;
import io.github.mike10004.vhs.harbridge.HttpMethod;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import io.github.mike10004.vhs.repackaged.org.apache.http.NameValuePair;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import net.lightbody.bmp.core.har.HarNameValuePair;
import net.lightbody.bmp.core.har.HarPostData;
import net.lightbody.bmp.core.har.HarPostDataParam;
import net.lightbody.bmp.core.har.HarRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class BmpHttpAssistant implements HttpAssistant<BmpRequest, HttpResponse> {

    private static final Logger log = LoggerFactory.getLogger(BmpHttpAssistant.class);

    @Override
    public ParsedRequest parseRequest(BmpRequest incomingRequest) throws IOException {
        return parse(incomingRequest.originalRequest, incomingRequest.fullRequestCapture);
    }

    @Override
    public HttpResponse transformRespondable(BmpRequest incomingRequest, HttpRespondable respondable) throws IOException {
        return toResponse(incomingRequest.originalRequest, incomingRequest.fullRequestCapture, respondable);
    }

    @Override
    public HttpResponse constructResponse(BmpRequest incomingRequest, ImmutableHttpResponse httpResponse) {
        byte[] body;
        try {
            body = httpResponse.getDataSource().read();
        }  catch (IOException e) {
            log.warn("failed to reconstitute response", e);
            body = new byte[0];
        }
        return construct(incomingRequest.originalRequest, HttpResponseStatus.valueOf(httpResponse.status),
                httpResponse.getContentType(), body);
    }

    @SuppressWarnings("unused")
    protected ParsedRequest parse(HttpRequest originalRequest, HarRequest fullCapturedRequest) throws IOException {
        HttpMethod method = HttpMethod.valueOf(fullCapturedRequest.getMethod());
        URI url = URI.create(fullCapturedRequest.getUrl());
        Multimap<String, String> headers = toMultimap(fullCapturedRequest.getHeaders());
        @Nullable Multimap<String, Optional<String>> query = null;
        List<HarNameValuePair> queryParams = fullCapturedRequest.getQueryString();
        if (queryParams != null) {
            query = toMultimapOfOptionals(queryParams);
        }
        @Nullable byte[] body = null;
        HarPostData postData = fullCapturedRequest.getPostData();
        if (postData != null) {
            body = toBytes(postData, fullCapturedRequest.getBodySize());
        }
        return ParsedRequest.inMemory(method, url, query, headers, body);
    }

    @Nullable
    protected byte[] toBytes(HarPostData postData, @Nullable Long requestBodySize) throws IOException {
        if (postData != null) {
            List<HarPostDataParam> params = postData.getParams();
            List<NameValuePair> pairs = null;
            if (params != null) {
                pairs = params.stream().map(p -> NameValuePair.of(p.getName(), p.getValue())).collect(Collectors.toList());
            }
            String contentType = postData.getMimeType();
            String postDataText = postData.getText();
            @Nullable String postDataComment = postData.getComment();
            ByteSource byteSource = Hars.getRequestPostData(pairs, contentType, postDataText, requestBodySize, postDataComment);
            if (byteSource != null) {
                return byteSource.read();
            }
        }
        return null;
    }

    protected Multimap<String, Optional<String>> toMultimapOfOptionals(Iterable<? extends HarNameValuePair> nameValuePairs) {
        Multimap<String, Optional<String>> mm = ArrayListMultimap.create();
        nameValuePairs.forEach(pair -> {
            mm.put(pair.getName(), Optional.ofNullable(pair.getValue()));
        });
        return mm;
    }

    protected Multimap<String, String> toMultimap(Iterable<? extends HarNameValuePair> nameValuePairs) {
        Multimap<String, String> mm = ArrayListMultimap.create();
        nameValuePairs.forEach(pair -> {
            mm.put(pair.getName(), pair.getValue());
        });
        return mm;
    }

    protected HttpResponse toResponse(HttpRequest originalRequest,
                                      @SuppressWarnings("unused") HarRequest fullCapturedRequest,
                                      HttpRespondable respondable) throws IOException {
        HttpResponseStatus status = HttpResponseStatus.valueOf(respondable.getStatus());
        ByteArrayOutputStream baos = new ByteArrayOutputStream(maybeGetLength(respondable, 256));
        byte[] responseData;
        respondable.writeBody(baos);
        responseData = baos.toByteArray();
        return constructResponse(originalRequest, status, respondable.streamHeaders(), responseData);
    }

    protected HttpResponse constructResponse(HttpRequest originalRequest, HttpResponseStatus status, Stream<? extends Entry<String, String>> headerStream, byte[] responseData) {
        ByteBuf content = Unpooled.wrappedBuffer(responseData);
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(originalRequest.getProtocolVersion(), status, content);
        HttpHeaders headers = response.headers();
        headerStream.forEach(header -> {
            headers.add(header.getKey(), header.getValue());
        });
        return response;
    }

    @SuppressWarnings({"SameParameterValue", "unused"})
    protected int maybeGetLength(HttpRespondable respondable, int defaultValue) {
        return defaultValue; // TODO get length from Content-Length
    }

    protected HttpResponse construct(HttpRequest originalRequest, HttpResponseStatus status, @Nullable MediaType contentType, byte[] responseData) {
        if (contentType == null) {
            contentType = MediaType.OCTET_STREAM;
        }
        ByteBuf content = Unpooled.wrappedBuffer(responseData);
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(originalRequest.getProtocolVersion(), status, content);
        HttpHeaders headers = response.headers();
        headers.add(com.google.common.net.HttpHeaders.CONTENT_TYPE, contentType.toString());
        headers.add(com.google.common.net.HttpHeaders.CONTENT_LENGTH, String.valueOf(responseData.length));
        return response;
    }

}
