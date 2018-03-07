package io.github.mike10004.vhs.bmp;

import io.github.mike10004.vhs.bmp.KeystoreGenerator.KeystoreType;
import net.lightbody.bmp.mitm.TrustSource;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

public class NanohttpdTlsEndpointFactoryTest {

    @Test
    public void createTrustSource() throws Exception {
        Random random = new Random(NanohttpdTlsEndpointFactoryTest.class.getName().hashCode());
        KeystoreGenerator gen = new KeystoreGenerator(KeystoreType.PKCS12, new MemorySecurityProviderTool(), random);
        KeystoreData keystoreData = gen.generate();
        TrustSource trustSource = NanohttpdTlsEndpointFactory.createTrustSource(keystoreData);
        assertNotNull("trustSource", trustSource);
    }
}