package io.github.mike10004.vhs.bmp;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import io.github.mike10004.vhs.harbridge.HttpMethod;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import io.netty.handler.codec.http.HttpRequest;
import net.lightbody.bmp.core.har.HarNameValuePair;
import net.lightbody.bmp.core.har.HarPostData;
import net.lightbody.bmp.core.har.HarRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.List;

public class DefaultRequestParser implements BmpRequestParser {

    private static final Logger log = LoggerFactory.getLogger(DefaultRequestParser.class);

    @Override
    public ParsedRequest parse(HttpRequest originalRequest, HarRequest fullCapturedRequest) {
        HttpMethod method = HttpMethod.valueOf(fullCapturedRequest.getMethod());
        URI url = URI.create(fullCapturedRequest.getUrl());
        Multimap<String, String> headers = ArrayListMultimap.create();
        fullCapturedRequest.getHeaders().forEach(header -> {
            headers.put(header.getName(), header.getValue());
        });
        @Nullable Multimap<String, String> query = null;
        List<HarNameValuePair> queryParams = fullCapturedRequest.getQueryString();
        if (queryParams != null) {
            query = toMultimap(queryParams);
        }
        @Nullable byte[] body = null;
        HarPostData postData = fullCapturedRequest.getPostData();
        if (postData != null) {
            body = toBytes(postData);
        }
        return ParsedRequest.inMemory(method, url, query, headers, body);
    }

    @Nullable
    protected byte[] toBytes(HarPostData postData) {
        log.warn("postData -> bytes not yet implemented");
        return null;
    }

    protected Multimap<String, String> toMultimap(Iterable<? extends HarNameValuePair> nameValuePairs) {
        Multimap<String, String> mm = ArrayListMultimap.create();
        nameValuePairs.forEach(pair -> {
            mm.put(pair.getName(), pair.getValue());
        });
        return mm;
    }
}
