package io.github.mike10004.vhs;

import io.github.mike10004.vhs.HttpRespondable;
import io.github.mike10004.vhs.harbridge.ParsedRequest;

public interface ResponseInterceptor {

    HttpRespondable intercept(ParsedRequest request, HttpRespondable respondable);

}
