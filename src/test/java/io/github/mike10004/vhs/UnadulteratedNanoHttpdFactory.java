package io.github.mike10004.vhs;

import io.github.mike10004.nanochamp.server.NanoControl;
import io.github.mike10004.nanochamp.server.NanoControl.NanoHttpdImpl;
import io.github.mike10004.nanochamp.server.NanoServer.RequestHandler;

class UnadulteratedNanoHttpdFactory implements NanoControl.HttpdImplFactory {

    @Override
    public NanoHttpdImpl construct(NanoControl control, int port, RequestHandler requestHandler) {
        return control.new NanoHttpdImpl(port, requestHandler) {
            @Override
            protected boolean useGzipWhenAccepted(Response r) {
                return false;
            }
        };
    }
}
