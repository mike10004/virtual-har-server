package io.github.mike10004.vhs.bmp;

import io.github.mike10004.vhs.HttpRespondable;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import net.lightbody.bmp.core.har.HarRequest;

interface Responder {
    HttpResponse requestParsingFailed(HttpRequest originalRequest, HarRequest fullCapturedRequest);

    HttpResponse toResponse(HttpRequest originalRequest, HarRequest fullCapturedRequest, HttpRespondable respondable);

    HttpResponse noResponseFound(HttpRequest originalRequest, HarRequest fullCapturedRequest);

}
