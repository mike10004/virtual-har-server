package io.github.mike10004.vhs.testsupport;

import com.google.common.net.HostAndPort;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestsTest {

    @Test
    void test_isStillAlive() throws Exception {
        HostAndPort liveAddress;
        try (ServerSocket ctrl = new ServerSocket(0)) {
            liveAddress = HostAndPort.fromParts("localhost", ctrl.getLocalPort());
            assertTrue(Tests.isStillAlive(liveAddress), "ctrl.getSocketAddress() alive");
        }
        assertFalse(Tests.isStillAlive(liveAddress), "after closed");
        int port = Tests.findOpenPort();
        assertFalse(Tests.isStillAlive(HostAndPort.fromParts("localhost", port)), "unused port still alive");
    }
}
