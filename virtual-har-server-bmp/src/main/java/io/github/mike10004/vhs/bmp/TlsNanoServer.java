package io.github.mike10004.vhs.bmp;

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
import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;

public class TlsNanoServer implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(TlsNanoServer.class);

    private final NanoHTTPD server;
    private final int listeningPort;

    public TlsNanoServer(@Nullable SSLServerSocketFactory sslServerSocketFactory) throws IOException {
        this.server = new BarrierServer(findOpenPort());
        if (sslServerSocketFactory != null) {
            server.makeSecure(sslServerSocketFactory, null);
        }
        server.start();
        listeningPort = server.getListeningPort();
    }

    public int getListeningPort() {
        return listeningPort;
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
            log.info("somebody asked for server socket factory");
            return super.getServerSocketFactory();
        }


    }
}
