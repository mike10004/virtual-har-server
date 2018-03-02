package io.github.mike10004.vhs.bmp;

import com.google.common.net.HostAndPort;

public interface TlsEndpoint extends java.io.Closeable {

    HostAndPort getSocketAddress();

}
