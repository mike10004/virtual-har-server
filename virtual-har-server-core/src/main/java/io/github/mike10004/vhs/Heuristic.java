package io.github.mike10004.vhs;

public interface Heuristic {

    int rate(ParsedRequest entryRequest, ParsedRequest request);

}
