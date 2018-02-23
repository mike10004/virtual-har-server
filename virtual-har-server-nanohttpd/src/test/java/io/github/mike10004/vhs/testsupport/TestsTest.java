package io.github.mike10004.vhs.testsupport;

import com.google.common.net.HostAndPort;
import io.github.mike10004.nanochamp.server.NanoControl;
import io.github.mike10004.nanochamp.server.NanoServer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestsTest {

    @Test
    void test_isStillAlive() throws Exception {
        HostAndPort liveAddress;
        try (NanoControl ctrl = NanoServer.builder().build().startServer()) {
            liveAddress = ctrl.getSocketAddress();
            assertTrue(Tests.isStillAlive(liveAddress), "ctrl.getSocketAddress() alive");
        }
        assertFalse(Tests.isStillAlive(liveAddress), "after closed");
        int port = Tests.findOpenPort();
        assertFalse(Tests.isStillAlive(HostAndPort.fromParts("localhost", port)), "unused port still alive");
    }
}
