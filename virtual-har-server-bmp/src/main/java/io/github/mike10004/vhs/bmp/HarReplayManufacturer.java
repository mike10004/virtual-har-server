package io.github.mike10004.vhs.bmp;

import com.google.common.collect.ImmutableList;
import com.google.common.io.CharSource;
import com.google.common.net.MediaType;
import io.github.mike10004.vhs.repackaged.fi.iki.elonen.NanoHTTPD;
import io.github.mike10004.vhs.repackaged.fi.iki.elonen.NanoHTTPD.IHTTPSession;
import io.github.mike10004.vhs.repackaged.fi.iki.elonen.NanoHTTPD.Response;
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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static java.util.Objects.requireNonNull;

public class HarReplayManufacturer implements BmpResponseManufacturer, NanoResponseManufacturer {

    private static final Logger log = LoggerFactory.getLogger(HarReplayManufacturer.class);

    private final EntryMatcher entryMatcher;
    private final ImmutableList<ResponseInterceptor> responseInterceptors;
    private final HttpAssistant<BmpRequest, HttpResponse> bmpAssistant;
    private final HttpAssistant<NanoHTTPD.IHTTPSession, NanoHTTPD.Response> nanoAssistant;

    public HarReplayManufacturer(EntryMatcher entryMatcher, Iterable<ResponseInterceptor> responseInterceptors) {
        this(entryMatcher, responseInterceptors, new BmpHttpAssistant(), new NanoHttpAssistant());
    }

    public HarReplayManufacturer(EntryMatcher entryMatcher, Iterable<ResponseInterceptor> responseInterceptors, HttpAssistant<BmpRequest, HttpResponse> bmpAssistant, HttpAssistant<IHTTPSession, Response> nanoAssistant) {
        this.entryMatcher = requireNonNull(entryMatcher);
        this.responseInterceptors = ImmutableList.copyOf(responseInterceptors);
        this.bmpAssistant = requireNonNull(bmpAssistant);
        this.nanoAssistant = requireNonNull(nanoAssistant);
    }

    @Override
    public HttpResponse manufacture(HttpRequest originalRequest, HarRequest fullCapturedRequest) {
        BmpRequest bmpRequest = BmpRequest.of(originalRequest, fullCapturedRequest);
        return manufacture(bmpAssistant, bmpRequest);
    }

    private static final Charset OUTGOING_CHARSET = StandardCharsets.UTF_8;

    protected ImmutableHttpResponse createNotFoundResponse() {
        MediaType contentType = MediaType.PLAIN_TEXT_UTF_8.withCharset(OUTGOING_CHARSET);
        return ImmutableHttpResponse.builder(404)
                .content(contentType, CharSource.wrap("404 Not Found").asByteSource(OUTGOING_CHARSET))
                .build();
    }

    protected ImmutableHttpResponse createParsingFailedResponse() {
        MediaType contentType = MediaType.PLAIN_TEXT_UTF_8.withCharset(OUTGOING_CHARSET);
        return ImmutableHttpResponse.builder(500)
                .content(contentType, CharSource.wrap("500 Internal Server Error").asByteSource(OUTGOING_CHARSET))
                .build();
    }

    protected <Q, S> S manufacture(HttpAssistant<Q, S> assistant, Q incoming) {
        ParsedRequest request;
        try {
            request = assistant.parseRequest(incoming);
        } catch (IOException e) {
            log.error("failed to read from incoming request", e);
            ImmutableHttpResponse outgoing = createParsingFailedResponse();
            return assistant.constructResponse(incoming, outgoing);
        }
        @Nullable HttpRespondable bestEntry = entryMatcher.findTopEntry(request);
        if (bestEntry != null) {
            for (ResponseInterceptor interceptor : responseInterceptors) {
                bestEntry = interceptor.intercept(request, bestEntry);
            }
        }
        if (bestEntry == null) {
            return assistant.constructResponse(incoming, createNotFoundResponse());
        } else {
            try {
                return assistant.transformRespondable(incoming, bestEntry);
            } catch (IOException e) {
                log.warn("failed to construct response", e);
                return assistant.constructResponse(incoming, HttpAssistant.standardServerErrorResponse());
            }
        }
    }

    @Override
    public NanoHTTPD.Response manufacture(IHTTPSession session) {
        return manufacture(nanoAssistant, session);
    }

}
