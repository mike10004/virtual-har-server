package io.github.mike10004.vhs.bmp;

import com.google.common.net.HostAndPort;
import com.google.common.net.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface HostRewriter {

    void replaceHost(HttpRequest request);

    static HostRewriter from(HostAndPort replacement, boolean useLoopbackAddressInsteadOfLocalhost) {
        if (useLoopbackAddressInsteadOfLocalhost && "localhost".equals(replacement.getHost())) {
            replacement = HostAndPort.fromParts("127.0.0.1", replacement.getPort());
        }
        String rewrittenHost = replacement.toString();
        return new HostRewriter() {

            private final Logger log = LoggerFactory.getLogger(HostRewriter.class.getName() + ".from.HostAndPort");

            @Override
            public void replaceHost(HttpRequest request) {
                log.debug("replacing URI and host header {} -> {}", request.headers().get(HttpHeaders.HOST), rewrittenHost);
                request.setUri(rewrittenHost);
                request.headers().set(com.google.common.net.HttpHeaders.HOST, rewrittenHost);
            }
        };
    }
}
