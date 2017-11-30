package io.github.mike10004.vhs;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;

public class ReplayingRequestHandler implements io.github.mike10004.nanochamp.server.NanoServer.RequestHandler {

    private static final Logger log = LoggerFactory.getLogger(ReplayingRequestHandler.class);

    private final EntryMatcher heuristic;
    private final ResponseManager responseManager;
    private final RequestParser<NanoHTTPD.IHTTPSession> nanoParser;

    public ReplayingRequestHandler(EntryMatcher heuristic, ResponseManager responseManager) {
        this.heuristic = heuristic;
        this.responseManager = responseManager;
        nanoParser = new RequestParser<>(new NanoBridge());
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

    @Nullable
    protected Response requestParsingFailed(IHTTPSession session) {
        return null;
    }
}
