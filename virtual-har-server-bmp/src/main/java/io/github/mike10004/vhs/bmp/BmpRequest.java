package io.github.mike10004.vhs.bmp;

import io.netty.handler.codec.http.HttpRequest;
import net.lightbody.bmp.core.har.HarRequest;

import static java.util.Objects.requireNonNull;

public final class BmpRequest {

    public final HttpRequest originalRequest;
    public final HarRequest fullRequestCapture;

    private BmpRequest(HttpRequest originalRequest, HarRequest fullRequestCapture) {
        this.originalRequest = requireNonNull(originalRequest);
        this.fullRequestCapture = requireNonNull(fullRequestCapture);
    }

    public static BmpRequest of(HttpRequest originalRequest, HarRequest fullCapturedRequest) {
        return new BmpRequest(originalRequest, fullCapturedRequest);
    }
}
