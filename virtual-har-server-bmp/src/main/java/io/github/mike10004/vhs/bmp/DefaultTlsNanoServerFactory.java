package io.github.mike10004.vhs.bmp;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import io.github.mike10004.vhs.bmp.BrowsermobVhsConfig.TlsEndpointFactory;
import io.github.mike10004.vhs.bmp.KeystoreGenerator.KeystoreData;

import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;

import static java.util.Objects.requireNonNull;

class DefaultTlsNanoServerFactory implements TlsEndpointFactory {

    private final KeystoreGenerator keystoreGenerator;
    private final NanoResponseManufacturer nanoResponseManufacturer;

    public DefaultTlsNanoServerFactory(KeystoreGenerator keystoreGenerator, NanoResponseManufacturer nanoResponseManufacturer) {
        this.keystoreGenerator = requireNonNull(keystoreGenerator);
        this.nanoResponseManufacturer = requireNonNull(nanoResponseManufacturer);
    }

    protected static class ResponseManufacturerNanoServer extends TlsNanoServer {
        private final NanoResponseManufacturer nanoResponseManufacturer;

        public ResponseManufacturerNanoServer(@Nullable SSLServerSocketFactory serverSocketFactory, NanoResponseManufacturer nanoResponseManufacturer) throws IOException {
            super(serverSocketFactory);
            this.nanoResponseManufacturer = nanoResponseManufacturer;
        }

        @Nullable
        @Override
        protected Response respond(IHTTPSession session) {
            return nanoResponseManufacturer.manufacture(session);
        }

    }

    protected ResponseManufacturerNanoServer createNanohttpdServer(SSLServerSocketFactory sslServerSocketFactory, NanoResponseManufacturer nanoResponseManufacturer) throws IOException {
        return new ResponseManufacturerNanoServer(sslServerSocketFactory, nanoResponseManufacturer);
    }

    @Override
    public TlsNanoServer produce(BrowsermobVhsConfig config, Path scratchDir) throws IOException {
        try {
            KeystoreData keystoreData = keystoreGenerator.generate();
            KeyStore keystore = keystoreData.loadKeystore();
            SSLServerSocketFactory sslServerSocketFactory = createTlsServerSocketFactory(keystore, keystoreData.keystorePassword);
            return createNanohttpdServer(sslServerSocketFactory, nanoResponseManufacturer);
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }
    static SSLServerSocketFactory createTlsServerSocketFactory(KeyStore keystore, char[] keystorePassword) throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyManagementException {
        String keyManagerAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(keyManagerAlgorithm);
        keyManagerFactory.init(keystore, keystorePassword);
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(keyManagerFactory.getKeyManagers(), null, null);
        return sc.getServerSocketFactory();
    }
}
