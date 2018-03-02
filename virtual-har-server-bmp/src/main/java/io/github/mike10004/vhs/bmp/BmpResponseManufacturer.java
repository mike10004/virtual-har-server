package io.github.mike10004.vhs.bmp;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import net.lightbody.bmp.core.har.HarRequest;

public interface BmpResponseManufacturer {

    HttpResponse manufacture(HttpRequest originalRequest, HarRequest fullCapturedRequest);

}
