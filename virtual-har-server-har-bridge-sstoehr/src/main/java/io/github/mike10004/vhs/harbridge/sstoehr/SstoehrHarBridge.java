package io.github.mike10004.vhs.harbridge.sstoehr;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.io.ByteSource;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import de.sstoehr.harreader.model.HarContent;
import de.sstoehr.harreader.model.HarEntry;
import de.sstoehr.harreader.model.HarHeader;
import de.sstoehr.harreader.model.HarPostData;
import de.sstoehr.harreader.model.HarPostDataParam;
import de.sstoehr.harreader.model.HarRequest;
import de.sstoehr.harreader.model.HarResponse;
import de.sstoehr.harreader.model.HttpMethod;
import io.github.mike10004.vhs.harbridge.HarBridge;
import io.github.mike10004.vhs.harbridge.HarResponseData;
import io.github.mike10004.vhs.harbridge.Hars;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import io.github.mike10004.vhs.repackaged.org.apache.http.NameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public class SstoehrHarBridge implements HarBridge<HarEntry> {

    private static final Logger log = LoggerFactory.getLogger(SstoehrHarBridge.class);

    @Override
    public String getRequestMethod(HarEntry entry) {
        HarRequest request = entry.getRequest();
        if (request != null) {
            HttpMethod method = request.getMethod();
            if (method != null) {
                return method.name();
            }
        }
        log.info("request method not present in HAR entry");
        return "";
    }

    @Override
    public String getRequestUrl(HarEntry entry) {
        String url = null;
        HarRequest request = entry.getRequest();
        if (request != null) {
            url = request.getUrl();
        }
        if (url == null) {
            log.info("request URL not present in HAR entry");
            url = "";
        }
        return url;
    }

    @Override
    public Stream<Map.Entry<String, String>> getRequestHeaders(HarEntry entry) {
        HarRequest request = entry.getRequest();
        if (request != null) {
            List<HarHeader> headers = request.getHeaders();
            if (headers != null) {
                return headers.stream()
                        .map(header -> new SimpleImmutableEntry<>(header.getName(), header.getValue()));
            }
        }
        return Stream.empty();
    }

    @Nullable
    protected static Long nullIfNegative(@Nullable Long value) {
        if (value == null) {
            return null;
        }
        return nullIfNegative(value.longValue());
    }

    protected static Long nullIfNegative(long value) {
        return value < 0 ? null : value;
    }

    @Nullable
    @Override
    public ByteSource getRequestPostData(HarEntry entry) throws IOException {
        HarRequest request = entry.getRequest();
        if (request != null) {
            HarPostData postData = request.getPostData();
            if (postData != null) {
                List<HarPostDataParam> params = postData.getParams();
                List<NameValuePair> pairs = null;
                if (params != null) {
                    pairs = params.stream().map(p -> NameValuePair.of(p.getName(), p.getValue())).collect(Collectors.toList());
                }
                String contentType = postData.getMimeType();
                String postDataText = postData.getText();
                @Nullable Long requestBodySize = nullIfNegative(request.getBodySize());
                @Nullable String postDataComment = postData.getComment();
                return Hars.getRequestPostData(pairs, contentType, postDataText, requestBodySize, postDataComment);
            }
        }
        return null;
    }

    @Override
    public HarResponseData getResponseData(ParsedRequest request, HarEntry entry) throws IOException {
        return HarResponseData.of(getResponseHeaders(entry), getResponseContentType(entry), getResponseBody(request, entry));
    }

    @Nullable
    private Supplier<Stream<Entry<String, String>>> getResponseHeaders(HarEntry entry) {
        HarResponse harResponse = entry.getResponse();
        if (harResponse != null) {
            return () -> {
                List<HarHeader> headers = harResponse.getHeaders();
                if (headers != null) {
                    return headers.stream()
                            .map(header -> new SimpleImmutableEntry<>(header.getName(), header.getValue()));
                } else {
                    return Stream.empty();
                }
            };
        } else {
            return null;
        }
    }

    private ByteSource getResponseBody(ParsedRequest request, HarEntry entry) throws IOException {
        HarResponse rsp = entry.getResponse();
        if (rsp == null) {
            return ByteSource.empty();
        }
        HarContent content = requireNonNull(rsp.getContent(), "response.content");
        @Nullable Long harContentSize = nullIfNegative(content.getSize());
        @Nullable Long bodySize = nullIfNegative(rsp.getBodySize());
        List<HarHeader> headers = MoreObjects.firstNonNull(rsp.getHeaders(), Collections.emptyList());
        @Nullable String contentEncodingHeaderValue = headers.stream()
                .filter(h -> HttpHeaders.CONTENT_ENCODING.equalsIgnoreCase(h.getName()))
                .map(HarHeader::getValue)
                .findFirst().orElse(null);
        @Nullable String harContentEncoding = Strings.emptyToNull(content.getEncoding());
        @Nullable String contentType = content.getMimeType();
        @Nullable String comment = content.getComment();
        @Nullable String text = content.getText();
        return Hars.translateResponseContent(request, contentType, text, bodySize, harContentSize, contentEncodingHeaderValue, harContentEncoding, comment).body;
    }

    @Nullable
    private MediaType getResponseContentType(HarEntry entry) {
        HarResponse rsp = entry.getResponse();
        if (rsp != null) {
            HarContent content = rsp.getContent();
            if (content != null) {
                String contentType = content.getMimeType();
                if (!Strings.nullToEmpty(contentType).trim().isEmpty()) {
                    return MediaType.parse(contentType);
                }
            }
        }
        return null;
    }

    @Override
    public int getResponseStatus(HarEntry entry) {
        HarResponse response = entry.getResponse();
        if (response != null) {
            return response.getStatus();
        }
        log.info("response not present in entry; returning 500 as status");
        return 500;
    }
}
