package io.github.mike10004.vhs;

import io.github.mike10004.vhs.harbridge.ParsedRequest;

import java.util.Objects;

public class ParsedEntry {

    public final HttpRespondable response;
    public final ParsedRequest request;

    public ParsedEntry(ParsedRequest request, HttpRespondable response) {
        this.response = Objects.requireNonNull(response);
        this.request = Objects.requireNonNull(request);
    }

    public HttpRespondable getResponse() {
        return response;
    }
}
