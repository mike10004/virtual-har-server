package io.github.mike10004.vhs.bmp;

import io.github.mike10004.vhs.bmp.BrowsermobVhsConfig.TlsEndpointFactory;
import io.github.mike10004.vhs.bmp.KeystoreGenerator.KeystoreData;

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

    public DefaultTlsNanoServerFactory(KeystoreGenerator keystoreGenerator) {
        this.keystoreGenerator = requireNonNull(keystoreGenerator);
    }

    protected TlsNanoServer createNanohttpdServer(SSLServerSocketFactory sslServerSocketFactory) throws IOException {
        return new TlsNanoServer(sslServerSocketFactory);
    }

    @Override
    public TlsNanoServer produce(BrowsermobVhsConfig config, Path scratchDir) throws IOException {
        try {
            KeystoreData keystoreData = keystoreGenerator.generate(scratchDir);
            KeyStore keystore = keystoreData.loadKeystore();
            SSLServerSocketFactory sslServerSocketFactory = createTlsServerSocketFactory(keystore, keystoreData.keystorePassword);
            return createNanohttpdServer(sslServerSocketFactory);
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
