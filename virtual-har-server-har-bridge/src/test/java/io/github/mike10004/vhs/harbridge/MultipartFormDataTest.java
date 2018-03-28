package io.github.mike10004.vhs.harbridge;

import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.common.net.MediaType;
import io.github.mike10004.vhs.repackaged.org.apache.http.client.utils.URLEncodedUtils;
import net.lightbody.bmp.util.BrowserMobHttpUtil;
import net.lightbody.bmp.util.HttpMessageContents;
import org.apache.commons.io.FileUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class MultipartFormDataTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    private final MultipartFormData.FormDataParser formDataParser;

    public MultipartFormDataTest(MultipartFormData.FormDataParser formDataParser) {
        this.formDataParser = formDataParser;
    }

    @Parameterized.Parameters
    public static List<MultipartFormData.FormDataParser> parsers() {
        return Collections.singletonList(new MultipartFormData.NanohttpdFormDataParser());
    }

    private void assertFormDataPartEquals(MultipartFormData.FormDataPart part, MediaType mediaTypeRange, String paramName, String filename) {
        assertNotNull("content disposition", part.contentDisposition);
        assertEquals("param name", paramName, part.contentDisposition.getName());
        assertEquals("filename", filename, part.contentDisposition.getFilename());
        assertNotNull("file", part.file);
        assertTrue(String.format("expect %s in range %s", part.file.getContentType(), mediaTypeRange), part.file.getContentType().is(mediaTypeRange));
    }

    @Test
    public void decode() throws Exception {
        TestCase requestBodyFixture = buildRequestBody();
        testDecode(requestBodyFixture);
    }

    private void testDecode(TestCase testCase) throws IOException {
        MediaType contentType = testCase.getContentType();
        String boundary = contentType.parameters().get("boundary").iterator().next();
        byte[] data = testCase.asByteSource().read();
        System.out.format("full data size: %s%n", data.length);
        List<MultipartFormData.FormDataPart> parts = formDataParser.decodeMultipartFormData(contentType, boundary, data);
        for (int i = 0; i < parts.size() ;i++) {
            System.out.format("part[%d] = %s%n", i, parts.get(i));
        }
        assertEquals("num parts", 2, parts.size());
        MultipartFormData.FormDataPart paramPart = parts.get(1);
        assertFormDataPartEquals(paramPart, MediaType.FORM_DATA, "tag", null);
        assertNotNull(paramPart.file);
        String paramValue = paramPart.file.asByteSource().asCharSource(US_ASCII).read();
        String expectedParamValue = testCase.getExpectedParamValue();
        assertEquals("param value", expectedParamValue, paramValue);
        MultipartFormData.FormDataPart filePart = parts.get(0);
        assertFormDataPartEquals(filePart, MediaType.JPEG, "f", "image-for-upload3965121338549146845.jpeg");
        assertNotNull(filePart.file);
        byte[] fileBytes = filePart.file.asByteSource().read();
        File imageFile = copyImageForUpload(FileUtils.getTempDirectory().toPath());
        byte[] expectedFileBytes = java.nio.file.Files.readAllBytes(imageFile.toPath());
        if (!Arrays.equals(expectedFileBytes, fileBytes)) {
            File file = File.createTempFile("unexpected-content", ".jpg");
            java.nio.file.Files.write(file.toPath(), fileBytes);
            System.out.format("This file does not have expected content:%n%s%n", file);
        }
        System.out.format("comparing parsed file (%d bytes) with expected file (%d bytes)%n", expectedFileBytes.length, fileBytes.length);
        assertArrayEquals("file bytes", expectedFileBytes, fileBytes);
    }

    /**
     * There is a TODO in {@link net.lightbody.bmp.filters.HarCaptureFilter#captureRequestContent}
     * that says "implement capture of files and multipart form data." Namely, what they do wrong is to
     * interpret {@code multipart/form-data} as a text type, when it can contain non-decodable segments.
     */
    @Test
    public void decode_asBmpHarCaptureFilterWould() throws Exception {
        TestCase requestBodyFixture = buildRequestBody();
        byte[] data = requestBodyFixture.asByteSource().read();
        Charset charset = UTF_8;
        String dataAsCapturedInHar = new String(data, charset);
        byte[] corruptedData = dataAsCapturedInHar.getBytes(charset);
        testDecode(TestCase.of(requestBodyFixture.getContentType(), ByteSource.wrap(corruptedData), requestBodyFixture.getExpectedParamValue(), requestBodyFixture.getExpectedFileData()));

    }

    static File copyImageForUpload(Path directory) throws IOException {
        return copyFileFromClasspath("/image-for-upload.jpg", "image-for-upload", ".jpeg", directory);
    }

    @SuppressWarnings("SameParameterValue")
    private static File copyFileFromClasspath(String resourcePath, String prefix, String suffix, Path tempdir) throws IOException {
        URL resource = MakeFileUploadHar.class.getResource(resourcePath);
        if (resource == null) {
            throw new FileNotFoundException(resourcePath);
        }
        File file = File.createTempFile(prefix, suffix, tempdir.toFile());
        Resources.asByteSource(resource).copyTo(Files.asByteSink(file));
        return file;
    }

    @Test
    public void dumpImageAndRequestBody() throws Exception {
//        Charset charset = MediaType.parse(CONTENT_TYPE).charset().or(StandardCharsets.UTF_8);
//        byte[] requestBody = HEX.decode(REQUEST_BODY_AS_HEX);
//        File requestBodyFile = File.createTempFile("request-body", ".dat");
//        java.nio.file.Files.write(requestBodyFile.toPath(), requestBody);
//        System.out.format("%s is request body data%n", requestBodyFile);
//        File imageFile = MultipartFormDataTest.copyImageForUpload(FileUtils.getTempDirectory().toPath());
//        System.out.format("%s is image file (%d bytes)%n", imageFile, imageFile.length());
//        long imageFileLen = imageFile.length();
//        ByteSource requestBodySrc = ByteSource.wrap(requestBody);
//        ByteSource preImageFileSlice = requestBodySrc.slice(0, 0xA5);
//        ByteSource imageFileSlice = requestBodySrc.slice(0xA5, imageFileLen);
//        ByteSource postImageFileSlice = requestBodySrc.slice(0xA5 + imageFileLen, requestBody.length - (0xA5 + imageFileLen));
//        checkState(requestBodySrc.contentEquals(ByteSource.concat(preImageFileSlice, imageFileSlice, postImageFileSlice)), "concatednated slices");
//        checkState("FFD8FFE000104A464946".equals(HEX.encode(imageFileSlice.slice(0, 10).read())), "image file slice starts with magic num for JPEG");
//        assertTrue("image file slice exists in request body", imageFileSlice.contentEquals(Files.asByteSource(imageFile)));
//        String preImageFileStr = preImageFileSlice.asCharSource(charset).read();
//        System.out.format("String preImageFileStr = \"%s\";%n", StringEscapeUtils.escapeJava(preImageFileStr));
//        String postImageFileStr = postImageFileSlice.asCharSource(charset).read();
//        System.out.format("String postImageFileStr = \"%s\";%n", StringEscapeUtils.escapeJava(postImageFileStr));
    }

    private static final BaseEncoding HEX = BaseEncoding.base16();
    private static final String CONTENT_TYPE_NO_BOUNDARY = "multipart/form-data";
    private static final String preImageFileStrTemplate = "--%s\r\nContent-Disposition: form-data; name=\"f\"; filename=\"image-for-upload3965121338549146845.jpeg\"\r\nContent-Type: image/jpeg\r\n\r\n";
    private static final String postImageFileStrTemplate = "\r\n--%s\r\nContent-Disposition: form-data; name=\"tag\"\r\n\r\n%s\r\n--%s--\r\n";

    private interface TestCase extends TypedContent {

        ByteSource getExpectedFileData();
        String getExpectedParamValue();

        static TestCase of(MediaType contentType, ByteSource data, String expectedParamValue, ByteSource expectedFileData) {
            return new TestCase() {
                @Override
                public ByteSource getExpectedFileData() {
                    return expectedFileData;
                }

                @Override
                public String getExpectedParamValue() {
                    return expectedParamValue;
                }

                @Override
                public MediaType getContentType() {
                    return contentType;
                }

                @Override
                public ByteSource asByteSource() {
                    return data;
                }
            };
        }
    }

    private TestCase buildRequestBody() throws IOException {
        String expectedParamValue = "Some UTF-8 text: \ud802\udd00\ud802\udd01\ud802\udd02";
        System.out.format("String expectedParamValue = \"%s\";%n", StringEscapeUtils.escapeJava(expectedParamValue));
        File imageFile = copyImageForUpload(temporaryFolder.getRoot().toPath());
        Charset TEXT_CHARSET = US_ASCII;
        String boundary = "----WebKitFormBoundarykWXf2mC9KePVVkV6";
        MediaType contentType = MediaType.parse(CONTENT_TYPE_NO_BOUNDARY).withParameter("boundary", boundary);
        String PRE_IMAGE_FILE_TEXT = String.format(preImageFileStrTemplate, boundary);
        String POST_IMAGE_FILE_TEXT = String.format(postImageFileStrTemplate, boundary, expectedParamValue, boundary);
        ByteSource concatenated = ByteSource.concat(CharSource.wrap(PRE_IMAGE_FILE_TEXT).asByteSource(TEXT_CHARSET),
                Files.asByteSource(imageFile),
                CharSource.wrap(POST_IMAGE_FILE_TEXT).asByteSource(TEXT_CHARSET));
        return TestCase.of(contentType, ByteSource.wrap(concatenated.read()), expectedParamValue, Files.asByteSource(imageFile));
    }

}
