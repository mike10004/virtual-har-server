package io.github.mike10004.vhs.bmp;

import com.google.common.net.HostAndPort;
import net.lightbody.bmp.mitm.TrustSource;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * Interface that provides access to the socket address where TLS
 * connections should be forwarded.
 */
public interface TlsEndpoint extends java.io.Closeable {

    HostAndPort getSocketAddress();

    @Nullable
    TrustSource getTrustSource();

    static TlsEndpoint createDefault() throws IOException {
        return new BrokenTlsEndpoint();
    }
}
