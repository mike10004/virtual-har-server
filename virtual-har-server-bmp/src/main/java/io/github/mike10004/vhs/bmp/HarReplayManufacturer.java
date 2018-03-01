package io.github.mike10004.vhs.bmp;

import com.google.common.collect.ImmutableList;
import io.github.mike10004.vhs.EntryMatcher;
import io.github.mike10004.vhs.HttpRespondable;
import io.github.mike10004.vhs.ResponseInterceptor;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import net.lightbody.bmp.core.har.HarRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;

import static java.util.Objects.requireNonNull;

public class HarReplayManufacturer implements ResponseManufacturer {

    private static final Logger log = LoggerFactory.getLogger(HarReplayManufacturer.class);

    private final EntryMatcher entryMatcher;
    private final ImmutableList<ResponseInterceptor> responseInterceptors;
    private final RequestParser requestParser;
    private final Responder responder;

    public HarReplayManufacturer(EntryMatcher entryMatcher, Iterable<ResponseInterceptor> responseInterceptors) {
        this.entryMatcher = requireNonNull(entryMatcher);
        this.responseInterceptors = ImmutableList.copyOf(responseInterceptors);
        requestParser = new DefaultRequestParser();
        this.responder = new DefaultResponder();
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
        @Nullable HttpRespondable bestEntry = entryMatcher.findTopEntry(request);
        if (bestEntry != null) {
            for (ResponseInterceptor interceptor : responseInterceptors) {
                bestEntry = interceptor.intercept(request, bestEntry);
            }
            HttpResponse response = responder.toResponse(originalRequest, fullCapturedRequest, bestEntry);
            return response;
        }
        return responder.noResponseFound(originalRequest, fullCapturedRequest);
    }

}
