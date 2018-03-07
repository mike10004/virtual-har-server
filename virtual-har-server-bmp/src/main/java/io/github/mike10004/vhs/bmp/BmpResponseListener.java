package io.github.mike10004.vhs.bmp;

import io.github.mike10004.vhs.harbridge.ParsedRequest;

public interface BmpResponseListener {

    void respondingWithError(ParsedRequest request, int status);

}
