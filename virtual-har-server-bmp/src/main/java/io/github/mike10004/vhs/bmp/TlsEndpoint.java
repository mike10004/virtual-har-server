package io.github.mike10004.vhs.bmp;

import com.google.common.net.HostAndPort;

import java.io.IOException;

/**
 * Interface that provides access to the socket address where TLS
 * connections should be forwarded.
 */
public interface TlsEndpoint extends java.io.Closeable {

    HostAndPort getSocketAddress();

    TrustConfig getTrustConfig();

    static TlsEndpoint createDefault() throws IOException {
        return new BrokenTlsEndpoint();
    }
}
