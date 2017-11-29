package io.github.mike10004.vhs;

import com.google.common.net.MediaType;
import edu.umass.cs.benchlab.har.HarContent;
import edu.umass.cs.benchlab.har.HarResponse;
import fi.iki.elonen.NanoHTTPD;
import io.github.mike10004.nanochamp.server.NanoResponse;

import javax.annotation.Nullable;

public interface ResponseManager {

    NanoHTTPD.Response toResponse(HarResponse harResponse);

    static ResponseManager createDefault() {
        return new ResponseManager() {
            @Override
            public NanoHTTPD.Response toResponse(HarResponse harResponse) {
                NanoResponse rb = NanoResponse.status(harResponse.getStatus());
                harResponse.getHeaders().getHeaders().forEach(header -> {
                    rb.header(header.getName(), header.getValue());
                });
                HarContent content = harResponse.getContent();
                @Nullable byte[] body = Hars.translate(content);
                if (body == null) {
                    body = new byte[0];
                }
                rb.content(MediaType.parse(content.getMimeType()), body);
                return rb.build();
            }
        };
    }
}
