package io.github.mike10004.vhs.nanohttpd;

import com.google.common.collect.ImmutableList;
import io.github.mike10004.nanochamp.repackaged.fi.iki.elonen.NanoHTTPD;
import io.github.mike10004.nanochamp.repackaged.fi.iki.elonen.NanoHTTPD.IHTTPSession;
import io.github.mike10004.nanochamp.repackaged.fi.iki.elonen.NanoHTTPD.Response;
import io.github.mike10004.vhs.EntryMatcher;
import io.github.mike10004.vhs.EntryParser;
import io.github.mike10004.vhs.HarBridgeEntryParser;
import io.github.mike10004.vhs.HttpRespondable;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;

public class ReplayingRequestHandler implements io.github.mike10004.nanochamp.server.NanoServer.RequestHandler {

    private static final Logger log = LoggerFactory.getLogger(ReplayingRequestHandler.class);
    private static final EntryParser<IHTTPSession> nanoParser = new HarBridgeEntryParser<>(new NanoBridge());

    private final EntryMatcher heuristic;
    private final ImmutableList<ResponseInterceptor> responseInterceptors;
    private final ResponseManager responseManager;

    public ReplayingRequestHandler(EntryMatcher heuristic, ResponseManager responseManager) {
        this(heuristic, ImmutableList.of(), responseManager);
    }

    public ReplayingRequestHandler(EntryMatcher heuristic, Iterable<ResponseInterceptor> responseInterceptors, ResponseManager responseManager) {
        this.heuristic = heuristic;
        this.responseInterceptors = ImmutableList.copyOf(responseInterceptors);
        this.responseManager = responseManager;
    }

    @Nullable
    @Override
    public Response serve(IHTTPSession session) {
        ParsedRequest request;
        try {
            request = nanoParser.parseRequest(session);
        } catch (IOException e) {
            log.error("failed to read from incoming request", e);
            return requestParsingFailed(session);
        }
        @Nullable HttpRespondable bestEntry = heuristic.findTopEntry(request);
        if (bestEntry != null) {
            bestEntry = filterThroughInterceptors(request, bestEntry);
            NanoHTTPD.Response response = responseManager.toResponse(bestEntry);
            return response;
        }
        return null;
    }

    protected HttpRespondable filterThroughInterceptors(ParsedRequest request, HttpRespondable respondable) {
        for (ResponseInterceptor interceptor : responseInterceptors) {
            respondable = interceptor.intercept(request, respondable);
        }
        return respondable;
    }

    @SuppressWarnings("unused")
    @Nullable
    protected Response requestParsingFailed(IHTTPSession session) {
        return null;
    }
}
