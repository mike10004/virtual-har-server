package io.github.mike10004.vhs.bmp;

import com.google.common.net.HostAndPort;

/**
 * Interface that provides access to the socket address where TLS
 * connections should be forwarded.
 */
public interface TlsEndpoint extends java.io.Closeable {

    HostAndPort getSocketAddress();

}
