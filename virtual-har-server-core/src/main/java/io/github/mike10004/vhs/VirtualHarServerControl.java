package io.github.mike10004.vhs;

import com.google.common.net.HostAndPort;

public interface VirtualHarServerControl extends java.io.Closeable {

    HostAndPort getSocketAddress();

}
