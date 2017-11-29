package io.github.mike10004.vhs;

import edu.umass.cs.benchlab.har.HarEntry;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import io.github.mike10004.nanochamp.server.NanoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;

public class ReplayingRequestHandler implements io.github.mike10004.nanochamp.server.NanoServer.RequestHandler {

    private static final Logger log = LoggerFactory.getLogger(ReplayingRequestHandler.class);

    private final Heuristic heuristic;
    private final ResponseManager responseManager;

    public ReplayingRequestHandler(Heuristic heuristic, ResponseManager responseManager) {
        this.heuristic = heuristic;
        this.responseManager = responseManager;
    }

    @Nullable
    @Override
    public Response serve(IHTTPSession session) {
        ParsedRequest request;
        try {
            request = ParsedRequest.parseNanoRequest(session);
        } catch (IOException e) {
            log.error("failed to read from incoming request", e);
            return NanoResponse.status(500).plainTextUtf8("Internal server error");
        }
        @Nullable HarEntry bestEntry = heuristic.findTopEntry(request);
        if (bestEntry != null) {
            NanoHTTPD.Response response = responseManager.toResponse(bestEntry.getResponse());
            return response;
        }
        return null;
    }
}
