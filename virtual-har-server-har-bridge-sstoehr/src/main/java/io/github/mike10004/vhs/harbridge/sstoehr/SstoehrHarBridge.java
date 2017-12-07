package io.github.mike10004.vhs.harbridge.sstoehr;

import com.google.common.net.MediaType;
import de.sstoehr.harreader.model.HarContent;
import de.sstoehr.harreader.model.HarEntry;
import de.sstoehr.harreader.model.HarPostData;
import de.sstoehr.harreader.model.HarPostDataParam;
import de.sstoehr.harreader.model.HarRequest;
import de.sstoehr.harreader.model.HarResponse;
import io.github.mike10004.vhs.harbridge.HarBridge;
import io.github.mike10004.vhs.harbridge.Hars;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

public class SstoehrHarBridge implements HarBridge<HarEntry> {
    protected HarRequest getRequest(HarEntry entry) {
        return checkNotNull(entry.getRequest(), "request");
    }

    protected HarResponse getResponse(HarEntry entry) {
        return checkNotNull(entry.getResponse(), "response");
    }

    protected static <E> E checkNotNull(E thing, String description) {
        if (thing == null) {
            throw new MissingHarFieldException(description);
        }
        return thing;
    }

    @Override
    public String getRequestMethod(HarEntry entry) {
        return checkNotNull(getRequest(entry).getMethod(), "request.method").name();
    }

    @Override
    public String getRequestUrl(HarEntry entry) {
        return checkNotNull(getRequest(entry).getUrl(), "request.url");
    }

    @Override
    public Stream<Map.Entry<String, String>> getRequestHeaders(HarEntry entry) {
        return checkNotNull(getRequest(entry).getHeaders(), "request.headers").stream()
                .map(header -> new SimpleImmutableEntry<>(header.getName(), header.getValue()));
    }

    @Override
    public Stream<Entry<String, String>> getResponseHeaders(HarEntry entry) {
        return checkNotNull(getResponse(entry).getHeaders(), "response.headers").stream()
                .map(header -> new SimpleImmutableEntry<>(header.getName(), header.getValue()));
    }

    @Nullable
    protected static Long nullIfNegative(long value) {
        return value < 0 ? null : value;
    }

    @SuppressWarnings("Duplicates")
    @Nullable
    @Override
    public byte[] getRequestPostData(HarEntry entry) throws IOException {
        HarRequest request = getRequest(entry);
        HarPostData postData = request.getPostData();
        if (postData != null) {
            List<HarPostDataParam> params = postData.getParams();
            if (!params.isEmpty()) {
                throw new UnsupportedOperationException("not yet implemented: parsing post data params");
            }
            return Hars.translate(postData.getMimeType(), postData.getText(), nullIfNegative(request.getBodySize()), null, postData.getComment(), Hars.MessageDirection.REQUEST);
        }
        return null;
    }

    @Override
    public byte[] getResponseBody(HarEntry entry) throws IOException {
        HarResponse rsp =getResponse(entry);
        HarContent content = checkNotNull(rsp.getContent(), "response.content");
        return Hars.translate(content.getMimeType(), content.getText(), nullIfNegative(content.getSize()), content.getEncoding(), content.getComment(), Hars.MessageDirection.RESPONSE);
    }

    @SuppressWarnings("Duplicates")
    @Override
    public MediaType getResponseContentType(HarEntry entry) {
        HarResponse rsp = getResponse(entry);
        HarContent content = checkNotNull(rsp.getContent(), "response.content");
        String contentType = checkNotNull(content.getMimeType(), "response.content.mimeType");
        if (contentType.isEmpty()) {
            throw new MissingHarFieldException("response.content.mimeType");
        }
        return MediaType.parse(contentType);
    }

    @Override
    public int getResponseStatus(HarEntry entry) {
        return getResponse(entry).getStatus();
    }
}
