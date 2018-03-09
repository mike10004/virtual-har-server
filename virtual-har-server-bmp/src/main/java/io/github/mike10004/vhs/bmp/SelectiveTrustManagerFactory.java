package io.github.mike10004.vhs.bmp;

import com.google.common.base.Suppliers;
import io.netty.handler.ssl.util.SimpleTrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import java.io.IOException;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class SelectiveTrustManagerFactory extends SimpleTrustManagerFactory {

    private static final Logger log = LoggerFactory.getLogger(SelectiveTrustManagerFactory.class);

    private final TrustManagerFactory delegateFactory;
    private final Supplier<TrustManager[]> trustManagersSupplier;

    public SelectiveTrustManagerFactory(TrustManagerFactory delegateFactory) {
        this.delegateFactory = delegateFactory;
        trustManagersSupplier = Suppliers.memoize(() -> {
            X509ExtendedTrustManager delegate = Stream.of(SelectiveTrustManagerFactory.this.delegateFactory.getTrustManagers())
                    .filter(X509ExtendedTrustManager.class::isInstance)
                    .map(X509ExtendedTrustManager.class::cast)
                    .findFirst().orElse(null);
            if (delegate != null) {
                return new TrustManager[]{ new SelectiveTrustManager(delegate) };
            }
            log.warn("no trust managers of {} in delegate factory {}", X509ExtendedTrustManager.class, delegateFactory);
            return new TrustManager[0];
        });
    }

    public static SelectiveTrustManagerFactory createDefaultPlusCustom(KeystoreData keystoreData) throws GeneralSecurityException, IOException {
        String algorithm = TrustManagerFactory.getDefaultAlgorithm();
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(algorithm);
        trustManagerFactory.init((KeyStore) null);
        trustManagerFactory.init(keystoreData.loadKeystore());
        return new SelectiveTrustManagerFactory(trustManagerFactory);
    }

    @Override
    protected void engineInit(KeyStore keyStore) throws Exception {
        throw new UnsupportedOperationException("not supported");
    }

    @Override
    protected void engineInit(ManagerFactoryParameters managerFactoryParameters) throws Exception {
        throw new UnsupportedOperationException("not supported");
    }

    @Override
    protected TrustManager[] engineGetTrustManagers() {
        return trustManagersSupplier.get();
    }

    static class SelectiveTrustManager extends X509ExtendedTrustManager {

        private final X509ExtendedTrustManager delegate;

        SelectiveTrustManager(X509ExtendedTrustManager delegate) {
            this.delegate = delegate;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            delegate.checkClientTrusted(chain, authType, socket);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            delegate.checkServerTrusted(chain, authType, socket);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
            delegate.checkClientTrusted(chain, authType, engine);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
//            sun.security.ssl.X509TrustManagerImpl.class.getName();
            delegate.checkServerTrusted(chain, authType, engine);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            delegate.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            delegate.checkServerTrusted(chain, authType);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return delegate.getAcceptedIssuers();
        }
    }
}
