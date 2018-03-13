package io.github.mike10004.vhs.harbridge;

import com.google.common.io.ByteSource;
import com.google.common.net.MediaType;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

class HarResponseDataImpl implements HarResponseData {

    private final Supplier<Stream<Map.Entry<String, String>>> headers;
    private final MediaType contentType;
    private final ByteSource body;

    /**
     *
     * @param headers headers; if null, empty stream will be used
     * @param contentType content-type; if null, {@code application/octet-stream} will be used
     * @param body body; if null, empty byte source will be used
     */
    public HarResponseDataImpl(@Nullable Supplier<Stream<Map.Entry<String, String>>> headers, @Nullable MediaType contentType, @Nullable ByteSource body) {
        this.headers = headers == null ? Stream::empty : headers;
        this.contentType = contentType == null ? HarBridge.getContentTypeDefaultValue() : contentType;
        this.body = body == null ? ByteSource.empty() : body;
    }

    @Override
    public Stream<Map.Entry<String, String>> headers() {
        return headers.get();
    }

    @Override
    public MediaType getContentType() {
        return contentType;
    }

    @Override
    public ByteSource getBody() {
        return body;
    }
}
