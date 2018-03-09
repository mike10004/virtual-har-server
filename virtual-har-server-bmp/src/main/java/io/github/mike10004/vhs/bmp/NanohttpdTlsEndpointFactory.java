package io.github.mike10004.vhs.bmp;

import com.google.common.net.HostAndPort;
import io.github.mike10004.vhs.bmp.BrowsermobVhsConfig.TlsEndpointFactory;
import io.github.mike10004.vhs.bmp.repackaged.fi.iki.elonen.NanoHTTPD;
import net.lightbody.bmp.mitm.TrustSource;
import net.lightbody.bmp.mitm.util.TrustUtil;

import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public class NanohttpdTlsEndpointFactory implements TlsEndpointFactory {

    private SSLServerSocketFactory socketFactory;
    private TrustConfig trustConfig;
    @Nullable
    private Integer port;

    public NanohttpdTlsEndpointFactory(SSLServerSocketFactory socketFactory, TrustConfig trustConfig, @Nullable Integer port) {
        this.socketFactory = requireNonNull(socketFactory, "socketFactory");
        this.trustConfig = requireNonNull(trustConfig, "trustConfig");
        this.port = port;
    }

    @Override
    public TlsEndpoint produce(BrowsermobVhsConfig config, Path scratchDir) throws IOException {
        return new NanoEndpoint(findPort());
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
        String algorithm = KeyManagerFactory.getDefaultAlgorithm();
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(algorithm);
        keyManagerFactory.init(keystore, keystoreData.keystorePassword);
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(keyManagerFactory.getKeyManagers(), null, null);
        return sc.getServerSocketFactory();
    }

    public static TrustConfig createTrustConfig(KeystoreData keystoreData) throws IOException, GeneralSecurityException {
//        return createBestTrustConfig(keystoreData);
        return createTrustConfigFromCertificates(keystoreData);
    }

    static TrustConfig createBestTrustConfig(KeystoreData keystoreData) throws IOException, GeneralSecurityException {
        return TrustConfig.fromManagerFactory(SelectiveTrustManagerFactory.createDefaultPlusCustom(keystoreData));
    }

    static TrustConfig createInsecureTrustConfig() {
        return TrustConfig.totallyInsecure();
    }

    @SuppressWarnings("CollectionAddAllCanBeReplacedWithConstructor")
    static TrustConfig createTrustConfigFromCertificates(KeystoreData keystoreData) throws GeneralSecurityException, IOException {
        List<X509Certificate> allCertificates = new ArrayList<>();
        List<X509Certificate> defaultCerts = Arrays.asList(TrustSource.defaultTrustSource().getTrustedCAs());
        allCertificates.addAll(defaultCerts);
        List<X509Certificate> keystoreCerts = createCertificates(keystoreData);
        allCertificates.addAll(keystoreCerts);
        return TrustConfig.fromCertificates(allCertificates);
    }

    static List<X509Certificate> createCertificates(KeystoreData keystoreData) throws GeneralSecurityException, IOException {
        return Collections.singletonList(keystoreData.asCertificateAndKeySource().load().getCertificate());
//        return TrustUtil.extractTrustedCertificateEntries(keystoreData.loadKeystore());
//                .stream().map(cert -> adjust(cert))
//                .collect(Collectors.toList());
    }

    private class NanoEndpoint implements TlsEndpoint {

        private NanoServer server;
        private HostAndPort socketAddress;

        public NanoEndpoint(int port) throws IOException {
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
        public TrustConfig getTrustConfig() {
            return trustConfig;
        }
    }

}
