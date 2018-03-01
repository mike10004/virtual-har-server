package io.github.mike10004.vhs.bmp;

import io.github.mike10004.vhs.EntryMatcherFactory;
import io.github.mike10004.vhs.VirtualHarServer;
import io.github.mike10004.vhs.testsupport.VirtualHarServerTestBase;

import java.io.File;
import java.io.IOException;

class BrowsermobVirtualHarServerTest extends VirtualHarServerTestBase {

    @Override
    protected VirtualHarServer createServer(int port, File harFile, EntryMatcherFactory entryMatcherFactory) throws IOException {
        BrowsermobVhsConfig config = BrowsermobVhsConfig.builder().build();
        return new BrowsermobVirtualHarServer(config);
    }

}