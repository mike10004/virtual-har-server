package io.github.mike10004.vhs;

import com.google.common.net.MediaType;
import fi.iki.elonen.NanoHTTPD;
import io.github.mike10004.nanochamp.server.NanoResponse;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public interface ResponseManager {

    NanoHTTPD.Response toResponse(HttpRespondable response);

    static ResponseManager createDefault() {
        return new ResponseManager() {
            @Override
            public NanoHTTPD.Response toResponse(HttpRespondable response) {
                NanoResponse rb = NanoResponse.status(response.getStatus());
                response.streamHeaders().forEach(header -> {
                    rb.header(header.getKey(), header.getValue());
                });
                ByteArrayOutputStream baos = new ByteArrayOutputStream(256); // TODO set size from Content-Length header if present
                MediaType contentType;
                try {
                    contentType = response.writeBody(baos);
                } catch (IOException e) {
                    LoggerFactory.getLogger(ResponseManager.class).error("could not buffer HTTP response data", e);
                    return NanoResponse.status(500).plainTextUtf8("Internal server error");
                }
                rb.content(contentType, baos.toByteArray());
                return rb.build();
            }
        };
    }
}
