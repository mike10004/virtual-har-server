package io.github.mike10004.vhs.testsupport;

import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.common.net.HostAndPort;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;

public class Tests {

    private Tests(){}

    public static File getReplayTest1HarFile(Path temporaryDirectory) throws IOException {
        ByteSource byteSource = Resources.asByteSource(Tests.class.getResource("/replay-test-1.har"));
        File harFile = File.createTempFile("replay-test-1-", ".har", temporaryDirectory.toFile());
        byteSource.copyTo(Files.asByteSink(harFile));
        return harFile;
    }

    public static int findOpenPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    public static boolean isStillAlive(HostAndPort socketAddress) {
        try (Socket ignore = new Socket(socketAddress.getHost(), socketAddress.getPort())) {
            return true;
        } catch (IOException e) {
            System.err.format("test of socket liveness failed: %s%n", e.toString());
            return false;
        }
    }
}
