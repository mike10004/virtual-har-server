package io.github.mike10004.vhs.bmp;

import net.lightbody.bmp.mitm.TrustSource;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class NanohttpdTlsEndpointFactoryTest {

    @Test
    public void createTrustSource() throws Exception {
        KeystoreData keystoreData = BmpTests.generateKeystoreForUnitTest(null);
        TrustSource trustSource = NanohttpdTlsEndpointFactory.createTrustConfig(keystoreData);
        assertNotNull("trustSource", trustSource);
    }

}