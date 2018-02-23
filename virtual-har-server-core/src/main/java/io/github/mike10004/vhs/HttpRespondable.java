package io.github.mike10004.vhs;

import com.google.common.collect.Multimap;
import com.google.common.net.MediaType;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public interface HttpRespondable {

    int getStatus();
    Stream<? extends Map.Entry<String, String>> streamHeaders();

    /**
     *
     * @param out an output stream
     * @return the content type (value for Content-Type header)
     */
    MediaType writeBody(OutputStream out) throws IOException;

    static HttpRespondable inMemory(int status, Multimap<String, String> headers, MediaType contentType, byte[] body) {
        requireNonNull(headers, "headers");
        requireNonNull(contentType, "contentType");
        requireNonNull(body, "body");
        return new HttpRespondable() {
            @Override
            public int getStatus() {
                return status;
            }

            @Override
            public Stream<? extends Map.Entry<String, String>> streamHeaders() {
                return headers.entries().stream();
            }

            @Override
            public MediaType writeBody(OutputStream out) throws IOException {
                out.write(body);
                return contentType;
            }
        };
    }
}
