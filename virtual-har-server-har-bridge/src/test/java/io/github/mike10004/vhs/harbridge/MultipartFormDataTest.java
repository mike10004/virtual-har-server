package io.github.mike10004.vhs.harbridge;

import com.google.common.base.Joiner;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.common.net.MediaType;
import com.google.common.primitives.Bytes;
import org.apache.commons.io.FileUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.junit.Assume;
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
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class MultipartFormDataTest {

    private static final boolean DUMP_FILE_WITH_UNEXPECTED_CONTENT_FOR_DEBUGGING = false;

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
        byte[] data = testCase.asByteSource().read();
        System.out.format("full data size: %s%n", data.length);
        List<MultipartFormData.FormDataPart> parts = formDataParser.decodeMultipartFormData(contentType, data);
        for (int i = 0; i < parts.size() ;i++) {
            System.out.format("part[%d] = %s%n", i, parts.get(i));
        }
        assertEquals("num parts", 2, parts.size());
        MultipartFormData.FormDataPart paramPart = parts.get(1);
        assertFormDataPartEquals(paramPart, MediaType.FORM_DATA, "tag", null);
        assertNotNull(paramPart.file);
        String paramValue = paramPart.file.asByteSource().asCharSource(MultipartFormData.URLENCODED_FORM_DATA_DEFAULT_ENCODING).read();
        String expectedParamValue = testCase.getExpectedParamValue();
        System.out.format("param value:%n  expect %s%n  actual %s%n", Arrays.toString(expectedParamValue.getBytes(MultipartFormData.URLENCODED_FORM_DATA_DEFAULT_ENCODING)), Arrays.toString(paramValue.getBytes(MultipartFormData.URLENCODED_FORM_DATA_DEFAULT_ENCODING)));
        assertEquals("param value", expectedParamValue, paramValue);
        MultipartFormData.FormDataPart filePart = parts.get(0);
        assertFormDataPartEquals(filePart, MediaType.JPEG, "f", "image-for-upload3965121338549146845.jpeg");
        assertNotNull(filePart.file);
        byte[] fileBytes = filePart.file.asByteSource().read();
        File imageFile = copyImageForUpload(FileUtils.getTempDirectory().toPath());
        byte[] expectedFileBytes = java.nio.file.Files.readAllBytes(imageFile.toPath());
        if (DUMP_FILE_WITH_UNEXPECTED_CONTENT_FOR_DEBUGGING) {
            if (!Arrays.equals(expectedFileBytes, fileBytes)) {
                File file = File.createTempFile("unexpected-content", ".jpg");
                java.nio.file.Files.write(file.toPath(), fileBytes);
                System.out.format("This file does not have expected content:%n%s%n", file);
            }
        }
        System.out.format("comparing parsed file (%d bytes) with expected file (%d bytes)%n", expectedFileBytes.length, fileBytes.length);
        assertArrayEquals("file bytes", expectedFileBytes, fileBytes);
    }

    /**
     * Test parsing request body as stored in a HAR by Browsermob.
     * There is a TODO in {@link net.lightbody.bmp.filters.HarCaptureFilter#captureRequestContent}
     * that says "implement capture of files and multipart form data." Namely, what they do wrong is to
     * construct a string from the data in order to store it as text in a request content 'text' field, but
     * {@code multipart/form-data} is more appropriately interpreted as a binary type, and should be base-64
     * encoded. As far as I can tell, Browsermob *never* stores request content as base-64, but it probably should.
     * We'd like to be able to handle this case, but the construction of a string from binary data sometimes
     * loses some of the original bytes (when they are not decodable as characters).
     */
    @Test
    public void decode_asBmpHarCaptureFilterWould() throws Exception {
        TestCase requestBodyFixture = buildRequestBody();
        byte[] data = requestBodyFixture.asByteSource().read();
        Charset charset = UTF_8;
        String dataAsCapturedInHar = new String(data, charset);
        byte[] corruptedData = dataAsCapturedInHar.getBytes(charset);
        try {
            testDecode(TestCase.of(requestBodyFixture.getContentType(), ByteSource.wrap(corruptedData), requestBodyFixture.getExpectedParamValue(), requestBodyFixture.getExpectedFileData()));
        } catch (AssertionError e) {
            System.out.format("%s: %s%n", "decode_asBmpHarCaptureFilterWould", e);
            Assume.assumeNoException("we would like to be able to handle the HARs created by Browsermob, but there's not much you " +
                    "can do about a HAR that was created in a way that may have lost some of the original request data", e);
        }

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
        byte[] utf8EncodingOfGnarlyString = {
                (byte) 0xf0, (byte) 0x9f, (byte) 0x82, (byte) 0xa1,
                (byte) 0xf0, (byte) 0x9f, (byte) 0x82, (byte) 0xa8,
                (byte) 0xf0,(byte) 0x9f, (byte) 0x83, (byte) 0x91,
                (byte) 0xf0, (byte) 0x9f, (byte) 0x83, (byte) 0x98,
                (byte) 0xf0, (byte) 0x9f, (byte) 0x83, (byte) 0x93,
        };
        String gnarlyString = new String(utf8EncodingOfGnarlyString, UTF_8);
        String expectedParamValue = "Some UTF-8 text: " + gnarlyString;
        System.out.format("String expectedParamValue = \"%s\"; // contains %s%n", StringEscapeUtils.escapeJava(expectedParamValue), gnarlyString);
        byte[] expectedParamValueBytes = expectedParamValue.getBytes(UTF_8);
        System.out.format("byte[] expectedParamValueBytes = {%s};%n", Joiner.on(", ").join(Bytes.asList(expectedParamValueBytes)));
        File imageFile = copyImageForUpload(temporaryFolder.getRoot().toPath());
        Charset TEXT_CHARSET = UTF_8;
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
