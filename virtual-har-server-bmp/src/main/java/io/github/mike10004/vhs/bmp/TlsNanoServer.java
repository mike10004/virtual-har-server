package io.github.mike10004.vhs.bmp;

import com.google.common.net.HostAndPort;
import io.github.mike10004.vhs.repackaged.fi.iki.elonen.NanoHTTPD;
import io.github.mike10004.vhs.repackaged.fi.iki.elonen.NanoHTTPD.ClientHandler;
import io.github.mike10004.vhs.repackaged.fi.iki.elonen.NanoHTTPD.IHTTPSession;
import io.github.mike10004.vhs.repackaged.fi.iki.elonen.NanoHTTPD.Response;
import io.github.mike10004.vhs.repackaged.fi.iki.elonen.NanoHTTPD.Response.Status;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

class TlsNanoServer implements TlsEndpoint {

    private static final Logger log = LoggerFactory.getLogger(TlsNanoServer.class);

    private final NanoHTTPD server;
    private final HostAndPort socketAddress;

    public TlsNanoServer(SSLServerSocketFactory sslServerSocketFactory) throws IOException {
        this(sslServerSocketFactory, null);
    }

    public TlsNanoServer(SSLServerSocketFactory sslServerSocketFactory, @Nullable String[] sslProtocols) throws IOException {
        this.server = new BarrierServer(findOpenPort());
        requireNonNull(sslServerSocketFactory, "sslServerSocketFactory");
        server.makeSecure(sslServerSocketFactory, sslProtocols);
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
        protected boolean useGzipWhenAccepted(Response r) {
            return false;
        }

        @Override
        protected ClientHandler createClientHandler(Socket finalAccept, InputStream inputStream) {
            return TlsNanoServer.this.createClientHandler(this, finalAccept, inputStream, () -> super.createClientHandler(finalAccept, inputStream));
        }
    }

    protected ClientHandler createClientHandler(NanoHTTPD server, Socket finalAccept, InputStream inputStream, Supplier<ClientHandler> superSupplier) {
        return superSupplier.get();
    }

}
