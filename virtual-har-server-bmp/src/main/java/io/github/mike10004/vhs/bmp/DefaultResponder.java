package io.github.mike10004.vhs.bmp;

import com.google.common.net.MediaType;
import io.github.mike10004.vhs.HttpRespondable;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import net.lightbody.bmp.core.har.HarRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Supplier;

class DefaultResponder implements Responder {

    private final static Logger log = LoggerFactory.getLogger(DefaultResponder.class);

    @Override
    public HttpResponse requestParsingFailed(HttpRequest originalRequest, HarRequest fullCapturedRequest) {
        throw new UnsupportedOperationException("DefaultResponder not yet implemented");
    }

    @Override
    public HttpResponse toResponse(HttpRequest originalRequest, HarRequest fullCapturedRequest, HttpRespondable respondable) {
        try {
            HttpResponseStatus status = HttpResponseStatus.valueOf(respondable.getStatus());
            ByteArrayOutputStream baos = new ByteArrayOutputStream(maybeGetLength(respondable, 256));
            byte[] responseData;
            respondable.writeBody(baos);
            responseData = baos.toByteArray();
            ByteBuf content = Unpooled.wrappedBuffer(responseData);
            DefaultFullHttpResponse response = new DefaultFullHttpResponse(originalRequest.getProtocolVersion(), status, content);
            HttpHeaders headers = response.headers();
            respondable.streamHeaders().forEach(header -> {
                headers.add(header.getKey(), header.getValue());
            });
            return response;
        } catch (IOException e) {
            log.error("response construction failed", e);
            return responseConstructionFailed(originalRequest, fullCapturedRequest);
        }
    }

    @SuppressWarnings("SameParameterValue")
    protected int maybeGetLength(HttpRespondable respondable, int defaultValue) {
        return defaultValue;
    }

    private final Supplier<String> traceCodeSupplier = () -> UUID.randomUUID().toString();

    @Override
    public HttpResponse noResponseFound(HttpRequest originalRequest, HarRequest fullCapturedRequest) {
        Charset charset = StandardCharsets.UTF_8;
        MediaType contentType = MediaType.PLAIN_TEXT_UTF_8.withCharset(charset);
        String message = String.format("404 Not Found (%s)", traceCodeSupplier.get());
        return construct(originalRequest, HttpResponseStatus.NOT_FOUND, contentType, message.getBytes(charset));
    }

    protected HttpResponse construct(HttpRequest originalRequest, HttpResponseStatus status, MediaType contentType, byte[] responseData) {
        ByteBuf content = Unpooled.wrappedBuffer(responseData);
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(originalRequest.getProtocolVersion(), status, content);
        HttpHeaders headers = response.headers();
        headers.add(com.google.common.net.HttpHeaders.CONTENT_TYPE, contentType.toString());
        headers.add(com.google.common.net.HttpHeaders.CONTENT_LENGTH, String.valueOf(responseData.length));
        return response;
    }

    protected HttpResponse responseConstructionFailed(HttpRequest originalRequest, HarRequest fullCapturedRequest) {
        return construct(originalRequest, HttpResponseStatus.INTERNAL_SERVER_ERROR, MediaType.OCTET_STREAM, new byte[0]);
    }

}
