package io.github.mike10004.vhs.bmp;

import com.google.common.net.HostAndPort;
import io.github.mike10004.vhs.bmp.repackaged.fi.iki.elonen.NanoHTTPD;
import io.github.mike10004.vhs.bmp.BrowsermobVhsConfig.TlsEndpointFactory;
import net.lightbody.bmp.mitm.TrustSource;

import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import static java.util.Objects.requireNonNull;

public class NanohttpdTlsEndpointFactory implements TlsEndpointFactory {

    private SSLServerSocketFactory socketFactory;
    private TrustSource trustSource;
    @Nullable
    private Integer port;

    public NanohttpdTlsEndpointFactory(SSLServerSocketFactory socketFactory, @Nullable TrustSource trustSource, @Nullable Integer port) {
        this.port = port;
        this.socketFactory = requireNonNull(socketFactory);
        this.trustSource = trustSource;
    }

    @Override
    public TlsEndpoint produce(BrowsermobVhsConfig config, Path scratchDir) throws IOException {
        try {
            return new NanoEndpoint(findPort());
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    private int findPort() throws IOException {
        if (port != null) {
            return port.intValue();
        }
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private class NanoServer extends NanoHTTPD {

        public NanoServer(int port) {
            super(port);
        }
    }

    public static SSLServerSocketFactory createSSLServerSocketFactory(KeystoreData keystoreData) throws IOException, GeneralSecurityException {
        KeyStore keystore = keystoreData.loadKeystore();
        String keyManagerAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(keyManagerAlgorithm);
        keyManagerFactory.init(keystore, keystoreData.keystorePassword);
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(keyManagerFactory.getKeyManagers(), null, null);
        return sc.getServerSocketFactory();
    }

    public static TrustSource createTrustSource(KeystoreData keystoreData) throws IOException, GeneralSecurityException {
        return null;
    }

    private class NanoEndpoint implements TlsEndpoint {

        private NanoServer server;
        private HostAndPort socketAddress;

        public NanoEndpoint(int port) throws IOException, GeneralSecurityException {
            server = new NanoServer(port);
            server.makeSecure(socketFactory, null);
            server.start();
            socketAddress = HostAndPort.fromParts("localhost", server.getListeningPort());
        }

        @Override
        public HostAndPort getSocketAddress() {
            return socketAddress;
        }

        @SuppressWarnings("RedundantThrows")
        @Override
        public void close() throws IOException {
            server.stop();
        }

        @Nullable
        @Override
        public TrustSource getTrustSource() {
            return trustSource;
        }
    }

}
