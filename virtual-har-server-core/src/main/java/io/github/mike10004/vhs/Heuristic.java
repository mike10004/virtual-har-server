package io.github.mike10004.vhs;

import io.github.mike10004.vhs.harbridge.ParsedRequest;

public interface Heuristic {

    int rate(ParsedRequest entryRequest, ParsedRequest request);

}
