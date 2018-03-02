package io.github.mike10004.vhs.bmp;

import com.google.common.net.HostAndPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

class BrokenTlsEndpoint implements TlsEndpoint {

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
                try (Socket ignore = serverSocket.accept()) {
                    log.info("accepted socket; closing");
                } catch (IOException e) {
                    if (!isSocketClosedException(e)) {
                        log.info("failed to accept socket", e);
                    }
                }
            }
        });
        thread.start();
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
            thread.join(1000);
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
