package io.github.mike10004.vhs.bmp;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import io.github.mike10004.vhs.bmp.BrowsermobVhsConfig.TlsNanoServerFactory;
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

class DefaultTlsNanoServerFactory implements TlsNanoServerFactory {

    private final KeystoreGenerator keystoreGenerator;

    public DefaultTlsNanoServerFactory(KeystoreGenerator keystoreGenerator) {
        this.keystoreGenerator = keystoreGenerator;
    }

    @Override
    public TlsNanoServer produce(BrowsermobVhsConfig config, Path scratchDir, NanoResponseManufacturer responseManufacturer) throws IOException {
        try {
            KeystoreData generatedKeystore = keystoreGenerator.generate();
            KeyStore keystore = generatedKeystore.loadKeystore();
            SSLServerSocketFactory sslServerSocketFactory = createTlsServerSocketFactory(keystore, generatedKeystore.keystorePassword);
            return new TlsNanoServer(sslServerSocketFactory) {
                @Nullable
                @Override
                protected Response respond(IHTTPSession session) {
                    return responseManufacturer.manufacture(session);
                }
            };
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
