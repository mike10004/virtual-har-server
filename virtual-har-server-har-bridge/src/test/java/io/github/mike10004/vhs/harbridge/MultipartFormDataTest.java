package io.github.mike10004.vhs.harbridge;

import com.google.common.io.BaseEncoding;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class MultipartFormDataTest {

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
        MediaType contentType = MediaType.parse(CONTENT_TYPE);
        byte[] data = BaseEncoding.base16().decode(REQUEST_BODY_AS_HEX);
        testDecode(contentType, data);
    }

    private void testDecode(MediaType contentType, byte[] data) throws IOException, MultipartFormData.MultipartFormDataParseException {
        String boundary = contentType.parameters().get("boundary").iterator().next();

        System.out.format("full data size: %s%n", data.length);
        List<MultipartFormData.FormDataPart> parts = formDataParser.decodeMultipartFormData(contentType, boundary, data);
        for (int i = 0; i < parts.size() ;i++) {
            System.out.format("part[%d] = %s%n", i, parts.get(i));
        }
        assertEquals("num parts", 2, parts.size());
        MultipartFormData.FormDataPart paramPart = parts.get(1);
        assertFormDataPartEquals(paramPart, MediaType.FORM_DATA, "tag", null);
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
        MediaType contentType = MediaType.parse(CONTENT_TYPE);
        byte[] data = BaseEncoding.base16().decode(REQUEST_BODY_AS_HEX);
        Charset charset = StandardCharsets.UTF_8;
        String dataAsCapturedInHar = new String(data, charset);
        byte[] corruptedData = dataAsCapturedInHar.getBytes(charset);
        testDecode(contentType, corruptedData);

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
        file.deleteOnExit();
        return file;
    }

    private static final String CONTENT_TYPE = "multipart/form-data; boundary=----WebKitFormBoundarykWXf2mC9KePVVkV6";
    private static final String REQUEST_BODY_AS_HEX = "2D2D2D2D2D2D5765624B6974466F726D426F756E646172796B575866326D43394B655056" +
            "566B56360D0A436F6E74656E742D446973706F736974696F6E3A20666F726D2D64617461" +
            "3B206E616D653D2266223B2066696C656E616D653D22696D6167652D666F722D75706C6F" +
            "6164333936353132313333383534393134363834352E6A706567220D0A436F6E74656E74" +
            "2D547970653A20696D6167652F6A7065670D0A0D0AFFD8FFE000104A4649460001010101" +
            "2C012C0000FFDB0043002016181C1814201C1A1C24222026305034302C2C3062464A3A50" +
            "74667A787266706E8090B89C8088AE8A6E70A0DAA2AEBEC4CED0CE7C9AE2F2E0C8F0B8CA" +
            "CEC6FFDB004301222424302A305E34345EC6847084C6C6C6C6C6C6C6C6C6C6C6C6C6C6C6" +
            "C6C6C6C6C6C6C6C6C6C6C6C6C6C6C6C6C6C6C6C6C6C6C6C6C6C6C6C6C6C6C6C6C6C6C6FF" +
            "C0001108008F008003012200021101031101FFC400190000030101010000000000000000" +
            "000000020304010005FFC400321000010401030302030801050000000000010002031121" +
            "12314104225161711391B11432334281A1D1E1C1052382F0F1FFC4001701000301000000" +
            "0000000000000000000000010203FFC4001B110101010101010101000000000000000000" +
            "01110231214112FFDA000C03010002110311003F009BA77B83DCE047A83CAC7CAE92606E" +
            "8020769FDD2C35C73A09FD1188653B336F341435B8AE467C21ADA4E3717823F94C094ED5" +
            "239BAC06B466866CA6075EC9C6695FA9D296CAE200CE364711739C43B21B49A638CBB516" +
            "8D5EAB5AC6B30C6D5E4D20F7E01D1B48D8FCD4B3C4E68258F3EC55CE52F504869A174329" +
            "9427BDFD306B1B7677F099040E677970B1C043D2B9C22B09BF10768C8F52565D5A29A5DA" +
            "AB39E6F65BAABD0D6E10B0EA36795AE16364A43E614FFF00770771CA4B9A5A722C790531" +
            "8EEF3C8AD9617B28DFD505D7A4EBD39BAF253FA790539AF1A83B144253D8643DADD87B2D" +
            "6C6F8C0C0763147213D28A8BB2434035E4A1329A6968DF84B73DB64E49DB037581D60763" +
            "8340BC82168AF8A0171DF3681D8A24EC5146F040A4D2CB1BFE8A4F08665F62F4EE9C646C" +
            "6DD4E2024B9A630681AF01243277CA1E180B78D4AA26A8F8864FC31FF22364A963733EE9" +
            "2E71DED501AFACB80F64646939E794F13A97A689ED6516D1F55AFE99DA8B81002ABE2340" +
            "E49D85296695E5C016F6E762A2E2E4B4C8C696E10CAE7561F7E96B23248D91106AC81454" +
            "EAF09899F11DA5D8D5CA77D88037AC9431B435C1FC5D572ABDD5F32567D27FB3D0AD44AC" +
            "7404114EAF454D2C71F455FCC4B749036A42F66A696F909E5C39E52DC7368081A4C6EA24" +
            "81E89ED758C02BBA98FF00301637C25C6EE012B3AD7F0C793A4D84974CE029A727829D54" +
            "33BF94B91B64E12D3C6364708E8924946C3F128DE0A014D6D9F19F54DE9C02ED1F9864A0" +
            "7C0BE37773362720A0D3DB4734AF9E3EF69CED4A3635C5D203F9764B04AC6E328C1F39B4" +
            "2DA736C708AABF541B58D05D778F29C3B9A0EC959E7FF56C6E0D01A761B5AAE6A3A86AEA" +
            "C2D2D22B6CF36836F06D6ACBC30C566DCE35E1686378D8795C5C109793848F61733759C1" +
            "200C7BA9DC0B6436DC7B2AEAC11E6ED4E2C8EEF6F521476BE2BB7C15845EFB260683EC85" +
            "F57400AF2B3D685BF0D046C4855C118F8C1E08DA8FAA48883A32C2717829910702DCE02A" +
            "89ABCB439B45453304525D7DE55B5D6104D1EBE2D5D8995E7B23FC402C0285EE6B2839C2" +
            "C7253268A5966D1138318D1DC7FA49774D14592D2F7793FC28C5CA635FA81208212DF236" +
            "EB67042C8C97F60D03DFFC2EEA986838608F44A4357D3C8658AAAF3946E8F48DBF748FF4" +
            "D758703E45D2B1F44795B4AC7A856D9B5C01DC0B2898DD4EAE02638E809DF8993497DB5A" +
            "6C29B5D9B3FBA64F29AA3F25117D3493CF95975F5AF3314B5ED7DD6DF5499267B9C5B134" +
            "6372990B43E2A77289DD3E96F6B75258A03219640357555E802B3A48DF0F63DC5ED390EF" +
            "1E8930464BB23F4E57A6006B29544DA16D04452C9D0E16563A50132C747135B21783BEE1" +
            "04F0026C728E29438968DC2224D12118350931C1800D9DCDA487891D476473C41EEEE364" +
            "FF00DD97323118C0CFB2587A2E958237389BA3F44F2E279C29C38B4E41CEF84F02C2AE51" +
            "D3587BBDD6CCEA6DA0F54BEA1F7438A4743921E7538929063EE000253895ACA1409CDACD" +
            "A8980B19DC292CF50F2ED2CD95258D7B7BB7E01423A7634DEA5585A6F4AE23049B3C956D" +
            "A8E234450E79FE138CA36BBF64D2EEA2373D96C753B8B1617953BBA863431E7B8922C720" +
            "AF41F2F1F52A09E673DFD99D39B3FE10717746C674D0681B9C93E53249C31849202F39B3" +
            "BEB31D9F75A03E4372501C008252E735E41B7673821068DEEC7BAE1E870953CFA46861EE" +
            "F98F6A4805CFBC8B03CF1FB2B6175B06785E64344B8B49693EF4AFE99C7453864273D1D7" +
            "8769A054B29EF3E15249AFD94D2FE21F54FBF13C7A5118CF0B1D8A37B0465B42FEA5669D" +
            "78FE9671AD3219BE237228EDBE53436F270029BE03DA41E3E898D9AB0EC019FE95C453CE" +
            "D8C0FAA5B807022FE4500EA6379FBC31C7B2E32B69041743672E27D0EC884600C0A42661" +
            "95866B384C0CB003C15C6872945F63B9D414D2C8657E96134373B5A4714493E3B3E6975A" +
            "B3CFD10B19436E13E38AF7A48C31C5CDD7BF2A9E9B3A81E12DE007015B2289FA65A342D1" +
            "3D166C552700244CDC83FA271CBED648DB6E3C2BEBC67CFA95D9F64E818090525D62F29F" +
            "D33B241F9ACA46B7C50E68D345473404835EE57A00002EBE6B080EC016AD0F07E13A3EA4" +
            "3BF2FF0049D5F106318B0782BD297A76BC56CA5920747641C7841A090CBF7A32D78F4169" +
            "5F6C9058A68E364D8E131CD6080DA228A16C001B394F4630992668D6701398DD2286C830" +
            "096EE6B64F8DBB7B2463899A8E558D650C514A88504C7346E37481728B77AA92523E21A2" +
            "AA739AD06F7AC2908B24EE91C7A75485CEED39B5DDCD3450C87B49A5A5F194F4A7379471" +
            "3C30E7658338E50B85DF9597EB6FC5AD70201DD303F343254114CE029DBAA18FC1A572A2" +
            "9C4A4B817938C04776BABB71CA090CB151D94EF6D05E8B9B6A77C44A152BCFD344924876" +
            "E15903750040493D3B8BEF564AAFA68C3230DBB41DA6340AAA4123B4EE98E3414EFCEF69" +
            "11521376EC8DFD903059DB089DDD85CD708C0B291EBFFFD90D0A2D2D2D2D2D2D5765624B" +
            "6974466F726D426F756E646172796B575866326D43394B655056566B56360D0A436F6E74" +
            "656E742D446973706F736974696F6E3A20666F726D2D646174613B206E616D653D227461" +
            "67220D0A0D0A746869732077696C6C2062652075726C2D656E636F6465640D0A2D2D2D2D" +
            "2D2D5765624B6974466F726D426F756E646172796B575866326D43394B655056566B5636" +
            "2D2D0D0A";

}
