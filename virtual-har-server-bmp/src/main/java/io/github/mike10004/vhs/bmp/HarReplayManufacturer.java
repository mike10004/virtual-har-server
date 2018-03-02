package io.github.mike10004.vhs.bmp;

import com.google.common.collect.ImmutableList;
import com.google.common.net.MediaType;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import io.github.mike10004.vhs.EntryMatcher;
import io.github.mike10004.vhs.EntryParser;
import io.github.mike10004.vhs.HarBridgeEntryParser;
import io.github.mike10004.vhs.HttpRespondable;
import io.github.mike10004.vhs.ResponseInterceptor;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import net.lightbody.bmp.core.har.HarRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static java.util.Objects.requireNonNull;

public class HarReplayManufacturer implements BmpResponseManufacturer, NanoResponseManufacturer {

    private static final Logger log = LoggerFactory.getLogger(HarReplayManufacturer.class);

    private final EntryMatcher entryMatcher;
    private final ImmutableList<ResponseInterceptor> responseInterceptors;
    private final BmpRequestParser requestParser;
    private final EntryParser<IHTTPSession> nanoRequestParser;
    private final Responder responder;

    public HarReplayManufacturer(EntryMatcher entryMatcher, Iterable<ResponseInterceptor> responseInterceptors) {
        this.entryMatcher = requireNonNull(entryMatcher);
        this.responseInterceptors = ImmutableList.copyOf(responseInterceptors);
        requestParser = new DefaultRequestParser();
        this.responder = new DefaultResponder();
        nanoRequestParser = new HarBridgeEntryParser<>(new NanoBridge());
    }

    @Override
    public HttpResponse manufacture(HttpRequest originalRequest, HarRequest fullCapturedRequest) {
        ParsedRequest request;
        try {
            request = requestParser.parse(originalRequest, fullCapturedRequest);
        } catch (IOException e) {
            log.error("failed to read from incoming request", e);
            return responder.requestParsingFailed(originalRequest, fullCapturedRequest);
        }
        HttpRespondable bestEntry = manufacture(request);
        if (bestEntry == null) {
            return responder.noResponseFound(originalRequest, fullCapturedRequest);
        } else {
            return responder.toResponse(originalRequest, fullCapturedRequest, bestEntry);
        }
    }

    @Nullable
    protected HttpRespondable manufacture(ParsedRequest request) {
        @Nullable HttpRespondable bestEntry = entryMatcher.findTopEntry(request);
        if (bestEntry != null) {
            for (ResponseInterceptor interceptor : responseInterceptors) {
                bestEntry = interceptor.intercept(request, bestEntry);
            }
        }
        return bestEntry;
    }

    @Override
    public NanoHTTPD.Response manufacture(IHTTPSession session) {
        ParsedRequest request;
        try {
            request = nanoRequestParser.parseRequest(session);
        } catch (IOException e) {
            log.warn("failed to parsed request", e);
            return createNanoErrorResponse();
        }
        HttpRespondable respondable = manufacture(request);
        if (respondable != null) {
            return createNanoResponse(respondable);
        } else {
            return creatNanoNotFoundResponse();
        }
    }

    protected NanoHTTPD.Response createNanoResponse(HttpRespondable response) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(256); // TODO set size from Content-Length header if present
        MediaType contentType;
        try {
            contentType = response.writeBody(baos);
        } catch (IOException e) {
            LoggerFactory.getLogger(NanoResponseManufacturer.class).error("could not buffer HTTP response data", e);
            return createNanoErrorResponse();
        }
        byte[] responseBody = baos.toByteArray();
        NanoHTTPD.Response.IStatus status = NanoHTTPD.Response.Status.lookup(response.getStatus());
        String mimeType = contentType.toString();
        NanoHTTPD.Response nanoResponse = NanoHTTPD.newFixedLengthResponse(status, mimeType, new ByteArrayInputStream(responseBody), responseBody.length);
        response.streamHeaders().forEach(header -> {
            nanoResponse.addHeader(header.getKey(), header.getValue());
        });
        return nanoResponse;
    }

    protected NanoHTTPD.Response createNanoErrorResponse() {
        return NanoHTTPD.newFixedLengthResponse(Status.INTERNAL_ERROR, "text/plain", "500 Internal Server Error");
    }

    protected NanoHTTPD.Response creatNanoNotFoundResponse() {
        return NanoHTTPD.newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "404 Not Found");
    }
}
