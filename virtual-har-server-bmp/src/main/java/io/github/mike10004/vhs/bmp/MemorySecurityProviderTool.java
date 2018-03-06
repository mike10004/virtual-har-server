package io.github.mike10004.vhs.bmp;

import net.lightbody.bmp.mitm.exception.ImportException;
import net.lightbody.bmp.mitm.exception.KeyStoreAccessException;
import net.lightbody.bmp.mitm.tools.DefaultSecurityProviderTool;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

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
    public void saveKeyStore(OutputStream fos, KeyStore keyStore, String keystorePassword) throws IOException {
        try {
            keyStore.store(fos, keystorePassword.toCharArray());
        } catch (CertificateException | NoSuchAlgorithmException | KeyStoreException e) {
            throw new KeyStoreAccessException("Unable to write KeyStore to stream", e);
        }
    }

}
