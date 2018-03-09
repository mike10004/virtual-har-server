package io.github.mike10004.vhs.bmp;

import net.lightbody.bmp.mitm.CertificateAndKey;
import net.lightbody.bmp.mitm.exception.ImportException;
import net.lightbody.bmp.mitm.exception.KeyStoreAccessException;
import net.lightbody.bmp.mitm.tools.DefaultSecurityProviderTool;
import net.lightbody.bmp.mitm.util.KeyStoreUtil;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import static com.google.common.base.Preconditions.checkNotNull;

final class MemorySecurityProviderTool extends DefaultSecurityProviderTool {

    public MemorySecurityProviderTool() {
    }

    @Override
    public KeyStore loadKeyStore(File file, String keyStoreType, String password) {
        throw new ImportException("loading from file not supported by " + getClass());
    }

    public KeyStore loadKeyStore(byte[] bytes, String keyStoreType, char[] password) {
        checkNotNull(bytes, "bytes");
        checkNotNull(password, "password");
        try {
            return KeystoreData.loadKeystore(keyStoreType, bytes, password);
        } catch (KeyStoreException e) {
            throw new KeyStoreAccessException("Unable to get KeyStore instance of type: " + keyStoreType, e);
        } catch (IOException e) {
            throw new ImportException("Unable to read KeyStore from byte array of length " + bytes.length, e);
        } catch (CertificateException | NoSuchAlgorithmException e) {
            throw new ImportException("Error while reading KeyStore", e);
        }
    }

    /**
     * Exports the keyStore to the specified output stream.
     *
     * @param fos             output stream to write to
     * @param keyStore         KeyStore to export
     * @param keystorePassword the password for the KeyStore
     */
    public void saveKeyStore(OutputStream fos, KeyStore keyStore, char[] keystorePassword) throws IOException {
        try {
            keyStore.store(fos, keystorePassword);
        } catch (CertificateException | NoSuchAlgorithmException | KeyStoreException e) {
            throw new KeyStoreAccessException("Unable to write KeyStore to stream", e);
        }
    }

    /**
     * Creates a new KeyStore containing the specified root certificate and private key.
     *
     * @param keyStoreType       type of the generated KeyStore, such as PKCS12 or JKS
     * @param certificate        root certificate to add to the KeyStore
     * @param privateKeyAlias    alias for the private key in the KeyStore
     * @param privateKey         private key to add to the KeyStore
     * @param privateKeyPassword password for the private key
     * @param provider           JCA provider to use, or null to use the system default
     * @return new KeyStore containing the root certificate and private key
     * @see KeyStoreUtil#createRootCertificateKeyStore(String, X509Certificate, String, PrivateKey, String, String)
     */
    @SuppressWarnings("SameParameterValue")
    private static KeyStore _createRootCertificateKeyStore(String keyStoreType,
                                                           X509Certificate certificate,
                                                           String privateKeyAlias,
                                                           PrivateKey privateKey,
                                                           char[] privateKeyPassword,
                                                           @Nullable String provider) {
        if (privateKeyPassword == null) {
            throw new IllegalArgumentException("Must specify a KeyStore password");
        }

        KeyStore newKeyStore = KeyStoreUtil.createEmptyKeyStore(keyStoreType, provider);

        try {
            newKeyStore.setKeyEntry(privateKeyAlias, privateKey, privateKeyPassword, new Certificate[]{certificate});
        } catch (KeyStoreException e) {
            throw new KeyStoreAccessException("Unable to store certificate and private key in KeyStore", e);
        }
        return newKeyStore;
    }

    public KeyStore createRootCertificateKeyStore(String keyStoreType,
                                                  CertificateAndKey rootCertificateAndKey,
                                                  String privateKeyAlias,
                                                  char[] password) {
        return _createRootCertificateKeyStore(keyStoreType, rootCertificateAndKey.getCertificate(), privateKeyAlias, rootCertificateAndKey.getPrivateKey(), password, null);
    }
}
