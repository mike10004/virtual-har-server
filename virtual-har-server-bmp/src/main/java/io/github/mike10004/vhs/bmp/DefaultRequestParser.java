package io.github.mike10004.vhs.bmp;

import io.github.mike10004.vhs.harbridge.ParsedRequest;
import io.netty.handler.codec.http.HttpRequest;
import net.lightbody.bmp.core.har.HarRequest;

public class DefaultRequestParser implements RequestParser {
    @Override
    public ParsedRequest parse(HttpRequest originalRequest, HarRequest fullCapturedRequest) {
        throw new UnsupportedOperationException("not yet impelmented");
    }
}
