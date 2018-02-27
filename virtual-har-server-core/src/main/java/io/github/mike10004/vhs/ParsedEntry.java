package io.github.mike10004.vhs;

import io.github.mike10004.vhs.harbridge.ParsedRequest;

import java.io.IOException;
import java.util.Objects;

public class ParsedEntry {

    public final ParsedRequest request;

    public final HttpRespondableCreator responseCreator;

    public ParsedEntry(ParsedRequest request, HttpRespondableCreator responseCreator) {
        this.responseCreator = Objects.requireNonNull(responseCreator);
        this.request = Objects.requireNonNull(request);
    }

    public interface HttpRespondableCreator {
        HttpRespondable createRespondable(ParsedRequest request) throws IOException;
    }
}
