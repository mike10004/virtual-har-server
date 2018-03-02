package io.github.mike10004.vhs.bmp;

import com.google.common.net.HostAndPort;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ServerSocket;

public class TlsNanoServer implements TlsEndpoint {

    private static final Logger log = LoggerFactory.getLogger(TlsNanoServer.class);

    private final NanoHTTPD server;
    private final HostAndPort socketAddress;

    public TlsNanoServer(@Nullable SSLServerSocketFactory sslServerSocketFactory) throws IOException {
        this.server = new BarrierServer(findOpenPort());
        if (sslServerSocketFactory != null) {
            server.makeSecure(sslServerSocketFactory, null);
        }
        server.start();
        socketAddress = HostAndPort.fromParts("localhost", server.getListeningPort());
    }

    @Override
    public HostAndPort getSocketAddress() {
        return socketAddress;
    }

    @Override
    public void close() throws IOException {
        server.stop();
    }

    private static int findOpenPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Nullable
    protected Response respond(IHTTPSession session) {
        log.debug("{} {}", session.getMethod(), StringUtils.abbreviate(session.getUri(), 128));
        return produceDefaultErrorResponse(session);
    }

    @SuppressWarnings("unused")
    protected Response produceDefaultErrorResponse(IHTTPSession session) {
        return NanoHTTPD.newFixedLengthResponse(Status.INTERNAL_ERROR, "application/octet-stream", new ByteArrayInputStream(new byte[0]), 0);
    }

    private class BarrierServer extends NanoHTTPD {

        public BarrierServer(int port) {
            super(port);

        }

        @Override
        public Response serve(IHTTPSession session) {
            return respond(session);
        }

        @Override
        public ServerSocketFactory getServerSocketFactory() {
            log.debug("somebody asked for server socket factory");
            return super.getServerSocketFactory();
        }

        @Override
        protected boolean useGzipWhenAccepted(Response r) {
            return false;
        }
    }
}
