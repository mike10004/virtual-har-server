package io.github.mike10004.vhs.bmp;

import io.netty.handler.codec.http.HttpResponse;

public interface BmpResponseFilter {
    void filter(HttpResponse response);
}
