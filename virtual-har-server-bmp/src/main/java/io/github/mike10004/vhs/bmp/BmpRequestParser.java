package io.github.mike10004.vhs.bmp;

import io.github.mike10004.vhs.harbridge.ParsedRequest;
import io.netty.handler.codec.http.HttpRequest;
import net.lightbody.bmp.core.har.HarRequest;

import java.io.IOException;

public interface BmpRequestParser {

    ParsedRequest parse(HttpRequest originalRequest, HarRequest fullCapturedRequest) throws IOException;

}
