package io.github.mike10004.vhs.bmp;

import com.google.common.net.HostAndPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

class BrokenTlsEndpoint implements TlsEndpoint {

    private static final long LISTENING_THREAD_JOIN_TIMEOUT_MS = 1000;

    private static final Logger log = LoggerFactory.getLogger(BrokenTlsEndpoint.class);

    private final ServerSocket serverSocket;
    private final HostAndPort socketAddress;
    private final Thread thread;
    private final AtomicBoolean closed;

    public BrokenTlsEndpoint() throws IOException {
        serverSocket = new ServerSocket(0);
        socketAddress = HostAndPort.fromParts("localhost", serverSocket.getLocalPort());
        closed = new AtomicBoolean(false);
        thread = new Thread(() -> {
            while (!closed.get()) {
                try (Socket socket = serverSocket.accept()) {
                    socketAccepted(socket);
                } catch (IOException e) {
                    if (!isSocketClosedException(e)) {
                        log.info("failed to accept socket", e);
                    }
                }
            }
        });
        thread.start();
    }

    @SuppressWarnings("unused")
    protected void socketAccepted(Socket socket) {
        log.info("accepted socket; closing");
    }

    private static boolean isSocketClosedException(IOException e) {
        return e instanceof java.net.SocketException && "Socket closed".equals(e.getMessage());
    }

    @Override
    public HostAndPort getSocketAddress() {
        return socketAddress;
    }

    @Override
    public void close() throws IOException {
        closed.set(true);
        serverSocket.close();
        try {
            thread.join(LISTENING_THREAD_JOIN_TIMEOUT_MS);
        } catch (InterruptedException e) {
            log.info("thread join interrupted; this is OK and what we want", e);
        }
        try {
            thread.interrupt();
        } catch (RuntimeException e) {
            log.warn("thread interrupt error", e);
        }
    }
}
