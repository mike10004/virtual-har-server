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

public class HarReplayManufacturer implements ResponseManufacturer {

    private static final Logger log = LoggerFactory.getLogger(HarReplayManufacturer.class);

    private final EntryMatcher heuristic;
    private final ImmutableList<ResponseInterceptor> responseInterceptors;
    private final RequestParser requestParser;

    public HarReplayManufacturer(EntryMatcher heuristic, ImmutableList<ResponseInterceptor> responseInterceptors) {
        this.heuristic = heuristic;
        this.responseInterceptors = responseInterceptors;
        requestParser = new DefaultRequestParser();
    }

    @Override
    public HttpResponse manufacture(HttpRequest originalRequest, HarRequest fullCapturedRequest) {
        ParsedRequest request;
        try {
            request = requestParser.parse(originalRequest, fullCapturedRequest);
        } catch (IOException e) {
            log.error("failed to read from incoming request", e);
            return requestParsingFailed(originalRequest, fullCapturedRequest);
        }
        @Nullable HttpRespondable bestEntry = heuristic.findTopEntry(request);
        if (bestEntry != null) {
            for (ResponseInterceptor interceptor : responseInterceptors) {
                bestEntry = interceptor.intercept(request, bestEntry);
            }
            HttpResponse response = toResponse(bestEntry);
            return response;
        }
        return noResponseFound(originalRequest, fullCapturedRequest);
    }

    protected HttpResponse requestParsingFailed(HttpRequest originalRequest, HarRequest fullCapturedRequest) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    protected HttpResponse toResponse(HttpRespondable respondable) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    protected HttpResponse noResponseFound(HttpRequest originalRequest, HarRequest fullCapturedRequest) {
        throw new UnsupportedOperationException("not yet implemented");
    }
}
