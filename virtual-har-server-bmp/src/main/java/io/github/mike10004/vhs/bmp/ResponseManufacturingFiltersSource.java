package io.github.mike10004.vhs.bmp;

import com.google.common.net.HostAndPort;
import com.google.common.net.HttpHeaders;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import net.lightbody.bmp.filters.HttpsAwareFiltersAdapter;
import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;
import org.littleshoot.proxy.impl.ProxyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

class ResponseManufacturingFiltersSource extends HttpFiltersSourceAdapter {

    private static final Logger log = LoggerFactory.getLogger(ResponseManufacturingFiltersSource.class);

    private final BmpResponseManufacturer responseManufacturer;
    private final HostAndPort httpsHostRewriteValue;

    public ResponseManufacturingFiltersSource(BmpResponseManufacturer responseManufacturer, HostAndPort httpsHostRewriteValue) {
        this.responseManufacturer = requireNonNull(responseManufacturer);
        this.httpsHostRewriteValue = requireNonNull(httpsHostRewriteValue);
    }

    @Override
    public HttpFilters filterRequest(HttpRequest originalRequest) {
        return doFilterRequest(originalRequest, null);
    }

    @Override
    public HttpFilters filterRequest(HttpRequest originalRequest, ChannelHandlerContext ctx) {
        return doFilterRequest(originalRequest, ctx);
    }

    private HttpFilters doFilterRequest(HttpRequest originalRequest, @Nullable ChannelHandlerContext ctx) {
        String newHostHeaderValue = rewriteHostHeader();
        if (ProxyUtils.isCONNECT(originalRequest)) {
            return new HostRewriteFilter(originalRequest, ctx, newHostHeaderValue);
        } else {
            return new ResponseManufacturingFilter(originalRequest, ctx, responseManufacturer);
        }
    }

    private static void replaceHost(HttpRequest request, String newHostHeaderValue) {
        log.debug("replacing host header {} -> {}", request.headers().get(HttpHeaders.HOST), newHostHeaderValue);
        request.headers().set(com.google.common.net.HttpHeaders.HOST, newHostHeaderValue);
    }

    private static class HostRewriteFilter extends HttpsAwareFiltersAdapter {

        private final String rewrittenHost;

        public HostRewriteFilter(HttpRequest originalRequest, ChannelHandlerContext ctx, String rewrittenHost) {
            super(originalRequest, ctx);
            this.rewrittenHost = requireNonNull(rewrittenHost);
        }

        @Override
        public HttpResponse clientToProxyRequest(HttpObject httpObject) {
            if (httpObject instanceof HttpRequest) {
                HttpRequest req = (HttpRequest) httpObject;
                System.out.format("%s = %s%n", "full URL", getFullUrl(req));
                System.out.format("%s = %s%n", "host and port", getHostAndPort(req));
                replaceHost(req, rewrittenHost);
            }
            System.out.format("%s = %s%n", "original url", getOriginalUrl());
            return super.clientToProxyRequest(httpObject);
        }
    }

    protected String rewriteHostHeader() {
        return httpsHostRewriteValue.toString();
    }

    @Override
    public int getMaximumRequestBufferSizeInBytes() {
        return 0;
    }

    @Override
    public int getMaximumResponseBufferSizeInBytes() {
        return 0;
    }
}
