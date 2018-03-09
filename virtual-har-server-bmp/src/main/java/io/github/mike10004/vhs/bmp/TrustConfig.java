package io.github.mike10004.vhs.bmp;

import com.google.common.collect.Iterables;
import io.netty.handler.ssl.SslContextBuilder;
import net.lightbody.bmp.mitm.TrustSource;
import net.lightbody.bmp.mitm.trustmanager.InsecureTrustManagerFactory;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.security.GeneralSecurityException;

public interface TrustConfig {

    void configure(SslContextBuilder sslContextBuilder);

    static TrustConfig fromSource(@Nullable TrustSource trustSource) {
        return new TrustConfig() {

            @Override
            public void configure(SslContextBuilder sslContextBuilder) {
                if (trustSource == null) {
                    LoggerFactory.getLogger(getClass()).warn("Disabling upstream server certificate verification. This will allow attackers to intercept communications with upstream servers.");
                    sslContextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE);
                } else {
                    sslContextBuilder.trustManager(trustSource.getTrustedCAs());
                }
            }
        };
    }

    static TrustConfig fromManagerFactory(TrustManagerFactory trustManagerFactory) {
        return new TrustConfig() {
            @Override
            public void configure(SslContextBuilder sslContextBuilder) {
                sslContextBuilder.trustManager(trustManagerFactory);
            }
        };
    }

    static TrustConfig fromCertificates(Iterable<java.security.cert.X509Certificate> certificateList) throws GeneralSecurityException {
        java.security.cert.X509Certificate[] certs = Iterables.toArray(certificateList, java.security.cert.X509Certificate.class);
        return new TrustConfig() {
            @Override
            public void configure(SslContextBuilder sslContextBuilder) {
                sslContextBuilder.trustManager(certs);
            }
        };
    }

    static TrustConfig totallyInsecure() {
        return fromSource(null);
    }
}
