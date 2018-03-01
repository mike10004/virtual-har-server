package io.github.mike10004.vhs.bmp;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

public class NanohttpdUpstreamBarrier implements UpstreamBarrier {

    private final NanoHTTPD server;

    public NanohttpdUpstreamBarrier() throws IOException {
        this(new BarrierServer(findOpenPort()));
    }

    protected NanohttpdUpstreamBarrier(NanoHTTPD server) throws IOException {
        this.server = server;
        server.start();
    }

    @Override
    public InetSocketAddress getSocketAddress() {
        return new InetSocketAddress("127.0.0.1", server.getListeningPort());
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

    private static class BarrierServer extends NanoHTTPD {

        public BarrierServer(int port) {
            super(port);

        }

        @Override
        public Response serve(IHTTPSession session) {
            return NanoHTTPD.newFixedLengthResponse(Status.INTERNAL_ERROR, "application/octet-stream", new ByteArrayInputStream(new byte[0]), 0);
        }
    }
}
