package io.github.mike10004.vhs.harbridge;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteSource;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpData;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NettyMultipartFormDataParser implements MultipartFormData.FormDataParser {

    protected HttpRequest mockRequest(MediaType contentType, byte[] data) {
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, io.netty.handler.codec.http.HttpMethod.POST, "", Unpooled.wrappedBuffer(data));
        request.headers().set(HttpHeaders.CONTENT_TYPE, contentType.toString());
        return request;
    }

    @Override
    public List<MultipartFormData.FormDataPart> decodeMultipartFormData(MediaType contentType, byte[] content) throws MultipartFormData.BadMultipartFormDataException, MultipartFormData.RuntimeIOException {
        HttpDataFactory dataFactory = new DefaultHttpDataFactory(false);
        HttpRequest request = mockRequest(contentType, content);
        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(dataFactory, request);
        List<MultipartFormData.FormDataPart> parts = new ArrayList<>();
        while (decoder.hasNext()) {
            InterfaceHttpData data = decoder.next();
            if (data != null) {
                try {
                    switch (data.getHttpDataType()) {
                        case Attribute:
                            handleAttribute(parts, (Attribute) data);
                            break;
                        case FileUpload:
                            handleFileUpload(parts, (FileUpload) data);
                            break;
                        case InternalAttribute:
                            handleInternalAttribute(parts, data);
                            break;
                    }
                } catch (IOException e) {
                    throw new MultipartFormData.RuntimeIOException("netty threw exception", e);
                } finally {
                    data.release();
                }
            }
        }
        return parts;
    }

    protected void handleInternalAttribute(List<MultipartFormData.FormDataPart> parts, InterfaceHttpData attr) {
        System.out.format("handleInternalAttribute (%s) %s%n", attr.getClass(), attr);
    }

    protected void handleAttribute(List<MultipartFormData.FormDataPart> parts, Attribute attr) throws IOException {
        parts.add(toFormDataPart(attr, null));
    }

    @Nullable
    protected String maybeGetFilename(@Nullable HttpData httpData) {
        if (httpData instanceof FileUpload) {
            return ((FileUpload)httpData).getFilename();
        }
        return null;
    }

    protected MultipartFormData.FormDataPart toFormDataPart(HttpData httpData, @Nullable String partContentType) throws IOException {
        byte[] parsedContent = httpData.get();
        TypedContent file = TypedContent.identity(ByteSource.wrap(parsedContent), partContentType);
        String name = httpData.getName();
        ContentDisposition disposition = ContentDisposition.builder("form-data")
                .filename(maybeGetFilename(httpData))
                .name(name)
                .build();
        Multimap<String, String> headers = ArrayListMultimap.create();
        return new MultipartFormData.FormDataPart(headers, disposition, file);
    }

    protected void handleFileUpload(List<MultipartFormData.FormDataPart> parts, FileUpload fileUpload) throws IOException {
        parts.add(toFormDataPart(fileUpload, fileUpload.getContentType()));
    }


}
