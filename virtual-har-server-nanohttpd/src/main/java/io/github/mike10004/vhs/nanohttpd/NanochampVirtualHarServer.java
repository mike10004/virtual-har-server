package io.github.mike10004.vhs.nanohttpd;

import com.google.common.net.HostAndPort;
import io.github.mike10004.nanochamp.server.NanoControl;
import io.github.mike10004.nanochamp.server.NanoServer;
import io.github.mike10004.vhs.VirtualHarServer;
import io.github.mike10004.vhs.VirtualHarServerControl;

import javax.annotation.Nullable;
import java.io.IOException;

import static java.util.Objects.requireNonNull;

public class NanochampVirtualHarServer implements VirtualHarServer {

    private final ReplayingRequestHandler requestHandler;
    @Nullable
    private final Integer port;

    public NanochampVirtualHarServer(ReplayingRequestHandler requestHandler, @Nullable Integer port) {
        this.requestHandler = requireNonNull(requestHandler);
        this.port = port;
    }

    @Override
    public VirtualHarServerControl start() throws IOException {
        NanoServer server = NanoServer.builder()
                .session(requestHandler)
                .httpdFactory(new UnadulteratedNanoHttpdFactory())
                .build();
        if (port == null) {
            return new NanochampVhsControl(server.startServer());
        } else {
            return new NanochampVhsControl(server.startServer(port));
        }
    }

    private static class NanochampVhsControl implements VirtualHarServerControl {

        private final NanoControl nanoControl;

        private NanochampVhsControl(NanoControl nanoControl) {
            this.nanoControl = requireNonNull(nanoControl);
        }

        @Override
        public void close() throws IOException {
            nanoControl.close();
        }

        @Override
        public HostAndPort getSocketAddress() {
            return nanoControl.getSocketAddress();
        }
    }
}
