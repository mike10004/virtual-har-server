package io.github.mike10004.vhs.harbridge;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteSource;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUpload;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.RequestContext;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ApacheMultipartFormDataParser implements MultipartFormData.FormDataParser {

    private final Supplier<Path> scratchDirSupplier;

    public ApacheMultipartFormDataParser(Supplier<Path> scratchDirSupplier) {
        this.scratchDirSupplier = scratchDirSupplier;
    }

    protected RequestContext createRequestContext(MediaType contentType, byte[] data) {
        return new RequestContext() {
            @Override
            public String getCharacterEncoding() {
                return contentType.charset().toJavaUtil().map(Charset::name).orElse(null);
            }

            @Override
            public String getContentType() {
                return contentType.toString();
            }

            @Override
            @SuppressWarnings("deprecation")
            public int getContentLength() {
                return data.length;
            }

            @Override
            public InputStream getInputStream() throws IOException {
                return new ByteArrayInputStream(data);
            }
        };
    }

    @Override
    public List<MultipartFormData.FormDataPart> decodeMultipartFormData(MediaType contentType, byte[] data) throws MultipartFormData.BadMultipartFormDataException, MultipartFormData.RuntimeIOException {
        DiskFileItemFactory fileItemFactory = new DiskFileItemFactory(10 * 1024 * 1024, scratchDirSupplier.get().toFile());
        RequestContext request = createRequestContext(contentType, data);
        List<FileItem> multiparts;
        try {
            multiparts = new FileUpload(fileItemFactory).parseRequest(request);
        } catch (FileUploadException e) {
            throw new MultipartFormData.BadMultipartFormDataException(e);
        }
        return multiparts.stream().map(this::toFormDataPart).collect(Collectors.toList());
    }

    protected MultipartFormData.FormDataPart toFormDataPart(FileItem item) {
        byte[] data = item.get();
        String contentTypeStr = item.getContentType();
        String paramName = item.getFieldName();
        ContentDisposition contentDisposition = null;
        String contentDispositionHeaderValue = item.getHeaders().getHeader(HttpHeaders.CONTENT_DISPOSITION);
        if (contentDispositionHeaderValue != null) {
            contentDisposition = ContentDisposition.parse(contentDispositionHeaderValue);
        }
        Multimap<String, String> headers = ArrayListMultimap.create();
        for (Iterator<String> headerNames = item.getHeaders().getHeaderNames(); headerNames.hasNext();) {
            String name = headerNames.next();
            headers.putAll(name, ImmutableList.copyOf(item.getHeaders().getHeaders(name)));
        }
        TypedContent file = TypedContent.identity(ByteSource.wrap(data), contentTypeStr);
        return new MultipartFormData.FormDataPart(headers, contentDisposition, file);
    }
}
