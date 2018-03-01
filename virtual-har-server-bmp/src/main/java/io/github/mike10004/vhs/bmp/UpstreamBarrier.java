package io.github.mike10004.vhs.bmp;

import java.net.InetSocketAddress;

interface UpstreamBarrier extends java.io.Closeable {

    InetSocketAddress getSocketAddress();

}

