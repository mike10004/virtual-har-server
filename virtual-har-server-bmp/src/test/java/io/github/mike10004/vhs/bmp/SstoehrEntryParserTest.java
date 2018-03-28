package io.github.mike10004.vhs.bmp;

import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import de.sstoehr.harreader.HarReader;
import de.sstoehr.harreader.HarReaderMode;
import de.sstoehr.harreader.model.Har;
import de.sstoehr.harreader.model.HarEntry;
import de.sstoehr.harreader.model.HarHeader;
import de.sstoehr.harreader.model.HarRequest;
import de.sstoehr.harreader.model.HttpMethod;
import io.github.mike10004.vhs.EntryParser;
import io.github.mike10004.vhs.HarBridgeEntryParser;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import io.github.mike10004.vhs.harbridge.sstoehr.SstoehrHarBridge;
import io.github.mike10004.vhs.testsupport.Tests;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SstoehrEntryParserTest extends EntryParserTestBase<HarEntry> {

    @Override
    protected EntryParser<HarEntry> createParser() {
        return HarBridgeEntryParser.withPlainEncoder(new SstoehrHarBridge());
    }

    @SuppressWarnings("Duplicates")
    @Override
    protected HarEntry createEntryWithRequest(String method, String urlStr, String... headers) {
        HarRequest request = new HarRequest();
        request.setMethod(HttpMethod.GET);
        request.setUrl(urlStr);
        for (int i = 0; i < headers.length; i += 2) {
            HarHeader header = new HarHeader();
            header.setName((headers[i]));
            header.setValue(headers[i+1]);
            request.getHeaders().add(header);
        }
        HarEntry entry = new HarEntry();
        entry.setRequest(request);
        return entry;
    }


    @Test
    public void getRequestPostData_uploadedImageFileInMultipartFormData() throws Exception {
        File harFile = new File(getClass().getResource("/file-upload-example.har").toURI());
        Har har = new HarReader().readFromFile(harFile, HarReaderMode.STRICT);
        List<HarEntry> postRequestEntries = har.getLog().getEntries().stream()
                .filter(entry -> {
                    return "POST".equalsIgnoreCase(entry.getRequest().getMethod().name());
                }).collect(Collectors.toList());
        checkState(postRequestEntries.size() == 1, "expected only one POST request");
        HarEntry entry = postRequestEntries.get(0);
        ParsedRequest request = createParser().parseRequest(entry);
        byte[] requestBody;
        try (InputStream in = request.openBodyStream()) {
            requestBody = ByteStreams.toByteArray(in);
        }
        String requestBodyPrefix = "------WebKitFormBoundaryKyVr0IODW8vdep4X\r\n" +
                "Content-Disposition: form-data; name=\"f\"; filename=\"image-for-upload7721496967317030273.jpeg\"\r\n" +
                "Content-Type: image/jpeg\r\n" +
                "\r\n";
        ByteSource requestBodyPostBoundaryByteSource = ByteSource.wrap(requestBody).slice(requestBodyPrefix.length(), requestBody.length - requestBodyPrefix.length());
        byte[] requestBodyPostBoundaryBytes = requestBodyPostBoundaryByteSource.read();
        System.out.format("%d bytes in request body of %s %s%n", requestBody.length, entry.getRequest().getMethod(), entry.getRequest().getUrl());
        File groundTruthImageFile = Tests.copyImageForUpload(FileUtils.getTempDirectory().toPath());
        byte[] imageBytes = java.nio.file.Files.readAllBytes(groundTruthImageFile.toPath());
        String imageBytesStr = new String(imageBytes, StandardCharsets.US_ASCII);
        System.out.format("image bytes: %s%n", StringUtils.abbreviateMiddle(imageBytesStr, "[...]", 128));
        String imageBytesHex = BaseEncoding.base16().encode(imageBytes);
        String requestBodyPostBoundaryStr = new String(requestBodyPostBoundaryBytes, StandardCharsets.US_ASCII);
        System.out.format("request body: %s%n", StringUtils.abbreviateMiddle(requestBodyPostBoundaryStr, "[...]", 128));
        String requestBodyPostBoundaryHex = BaseEncoding.base16().encode(requestBodyPostBoundaryBytes);
        assertTrue("request body contains image bytes", requestBodyPostBoundaryHex.contains(imageBytesHex));

    }
}