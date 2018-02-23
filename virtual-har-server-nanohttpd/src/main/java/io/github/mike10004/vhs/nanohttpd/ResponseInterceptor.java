package io.github.mike10004.vhs.nanohttpd;

import io.github.mike10004.vhs.HttpRespondable;
import io.github.mike10004.vhs.ParsedRequest;

public interface ResponseInterceptor {

    HttpRespondable intercept(ParsedRequest request, HttpRespondable respondable);

}
