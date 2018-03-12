package io.github.mike10004.vhs.bmp;

import de.sstoehr.harreader.HarReader;
import de.sstoehr.harreader.HarReaderMode;
import de.sstoehr.harreader.model.Har;
import de.sstoehr.harreader.model.HarEntry;
import io.github.mike10004.vhs.HarBridgeEntryParser;
import io.github.mike10004.vhs.harbridge.HarBridge;
import io.github.mike10004.vhs.harbridge.sstoehr.SstoehrHarBridge;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;

public class RealHarFileParsingTest {

    @Test
    public void parseRealHarFile() throws Exception {
        String harFilePathname = System.getProperty("vhs.realHarFile");
        Assume.assumeTrue("no real har file specified", harFilePathname != null);
        File harFile = new File(harFilePathname);
        Assume.assumeTrue(String.format("skipping because not a file: %s", harFile), harFile.isFile());
        HarBridge<HarEntry> bridge = new SstoehrHarBridge();
        HarBridgeEntryParser<HarEntry> entryParser = new HarBridgeEntryParser<>(bridge);
        Har har = new HarReader().readFromFile(harFile, HarReaderMode.LAX);
        for (HarEntry entry : har.getLog().getEntries()) {
            entryParser.parseRequest(entry);
        }
    }
}
