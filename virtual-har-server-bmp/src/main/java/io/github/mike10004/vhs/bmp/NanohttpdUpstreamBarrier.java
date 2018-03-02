package io.github.mike10004.vhs.bmp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.IOException;
import java.net.InetSocketAddress;

public class NanohttpdUpstreamBarrier extends TlsNanoServer implements UpstreamBarrier {

    private static final Logger log = LoggerFactory.getLogger(NanohttpdUpstreamBarrier.class);

    public NanohttpdUpstreamBarrier(@Nullable SSLServerSocketFactory sslServerSocketFactory) throws IOException {
        super(sslServerSocketFactory);
    }

    @Override
    public InetSocketAddress getSocketAddress() {
        return new InetSocketAddress("127.0.0.1", getListeningPort());
    }

}
