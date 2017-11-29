package io.github.mike10004.vhs;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import de.sstoehr.harreader.model.HttpMethod;

import javax.annotation.Nullable;
import java.net.URI;

public class RequestSpec {
    public HttpMethod method;
    public URI url;
    public Multimap<String, String> headers;
    @Nullable
    public byte[] body;

    public RequestSpec(HttpMethod method, URI url, @Nullable Multimap<String, String> headers, @Nullable byte[] body) {
        this.method = method;
        this.url = url;
        this.headers = headers == null ? ImmutableMultimap.of() : headers;
        this.body = body;
    }

    public static RequestSpec get(URI url) {
        return new RequestSpec(HttpMethod.GET, url, null, null);
    }

}
