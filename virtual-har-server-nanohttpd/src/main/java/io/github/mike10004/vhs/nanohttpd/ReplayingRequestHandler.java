package io.github.mike10004.vhs.nanohttpd;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import io.github.mike10004.vhs.EntryMatcher;
import io.github.mike10004.vhs.EntryParser;
import io.github.mike10004.vhs.HarBridgeEntryParser;
import io.github.mike10004.vhs.HttpRespondable;
import io.github.mike10004.vhs.ParsedRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;

public class ReplayingRequestHandler implements io.github.mike10004.nanochamp.server.NanoServer.RequestHandler {

    private static final Logger log = LoggerFactory.getLogger(ReplayingRequestHandler.class);
    private static final EntryParser<IHTTPSession> nanoParser = new HarBridgeEntryParser<>(new NanoBridge());

    private final EntryMatcher heuristic;
    private final ResponseManager responseManager;

    public ReplayingRequestHandler(EntryMatcher heuristic, ResponseManager responseManager) {
        this.heuristic = heuristic;
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
            NanoHTTPD.Response response = responseManager.toResponse(bestEntry);
            return response;
        }
        return null;
    }

    @SuppressWarnings("unused")
    @Nullable
    protected Response requestParsingFailed(IHTTPSession session) {
        return null;
    }
}
