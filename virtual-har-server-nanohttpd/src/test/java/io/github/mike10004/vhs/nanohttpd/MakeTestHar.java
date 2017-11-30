package io.github.mike10004.vhs.nanohttpd;

import java.io.File;
import java.util.List;

public class MakeTestHar {
    public static void main(String[] args) throws Exception {
        makeTestHar(ReplayingRequestHandlerTest.specs);
    }

    static void makeTestHar(List<HarMaker.EntrySpec> specs) throws Exception {
        File harFile = File.createTempFile("har-replay-test", ".har");
        new HarMaker().produceHarFile(specs, harFile);
        System.out.format("har file: %s%n", harFile);
    }
}
