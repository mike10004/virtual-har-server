package io.github.mike10004.vhs.nanohttpd;

import com.github.mike10004.sampleimggen.ImageFormat;
import com.github.mike10004.sampleimggen.NoiseImageGenerator;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.google.gson.Gson;
import io.github.mike10004.nanochamp.repackaged.fi.iki.elonen.NanoHTTPD;
import io.github.mike10004.nanochamp.server.NanoResponse;
import io.github.mike10004.vhs.nanohttpd.HarMaker.EntrySpec;
import io.github.mike10004.vhs.nanohttpd.HarMaker.HttpCallback;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Preconditions.checkState;

public class MakeTestHar {
    public static void main(String[] args) throws Exception {
//        makeTestHar(ReplayingRequestHandlerTest.specs);
        makeTestHar(makeSpecsForHarBridgeTest());
    }

    static void makeTestHar(List<HarMaker.EntrySpec> specs) throws Exception {
        AtomicInteger responsesConsumed = new AtomicInteger(0), requestsSent = new AtomicInteger(0);
        File harFile = File.createTempFile("har-replay-test", ".har");
        new HarMaker().produceHarFile(specs, new HttpCallback() {
            @Override
            public void requestSent(RequestSpec requestSpec) {
                requestsSent.incrementAndGet();
            }

            @Override
            public void responseConsumed() {
                responsesConsumed.incrementAndGet();
            }
        }, harFile);
        System.out.format("har file: %s%n", harFile);
        System.out.format("%d requests sent, %d responses consumed%n", requestsSent.get(), responsesConsumed.get());
        checkState(requestsSent.get() == responsesConsumed.get(), "requests != responses");
    }

    static List<EntrySpec> makeSpecsForHarBridgeTest() throws IOException {
        return Arrays.asList(createGetTextSpec(), createGetImageSpec(), createPostJsonSpec());
    }

    static EntrySpec createPostJsonSpec() {
        URI url = URI.create("http://www.somedomain.com/text");
        byte[] body = new Gson().toJson(ImmutableMap.of("foo", "bar", "baz", 2)).getBytes(StandardCharsets.UTF_8);
        RequestSpec request = new RequestSpec("POST", url, ImmutableMultimap.of(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString()), body);
        String responseJson = new Gson().toJson(ImmutableMap.of("status", "good"));
        NanoHTTPD.Response response = NanoResponse.status(201).jsonUtf8(responseJson);
        return new EntrySpec(request, response);

    }

    static EntrySpec createGetTextSpec() {
        RequestSpec request = RequestSpec.get(URI.create("http://www.somedomain.com/text"));
        NanoHTTPD.Response response = NanoResponse.status(200).htmlUtf8("<!DOCTYPE html><html><body>This is a test</body></html>");
        return new EntrySpec(request, response);
    }

    static EntrySpec createGet500Spec() {
        RequestSpec request = RequestSpec.get(URI.create("http://www.somedomain.com/error"));
        NanoHTTPD.Response response = NanoResponse.status(500).plainTextUtf8("500 Internal server error");
        return new EntrySpec(request, response);
    }

    static EntrySpec createGetImageSpec() throws IOException {
        RequestSpec request = RequestSpec.get(URI.create("http://www.somedomain.com/text"));
        ByteArrayOutputStream baos = new ByteArrayOutputStream(64);
        NoiseImageGenerator.createGenerator(ImageFormat.JPEG).generate(64, baos);
        baos.flush();
        NanoHTTPD.Response response = NanoResponse.status(200).jpeg(baos.toByteArray());
        return new EntrySpec(request, response);
    }
}
