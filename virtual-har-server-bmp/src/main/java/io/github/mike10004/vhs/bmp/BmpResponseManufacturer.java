package io.github.mike10004.vhs.bmp;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import net.lightbody.bmp.core.har.HarRequest;

/**
 * Interface that defines a method to manufacture responses that the proxy
 * will send to the client.
 */
public interface BmpResponseManufacturer {

    /**
     * Manufactures a response for a given request.
     * @param originalRequest the original request object
     * @param fullCapturedRequest the full request, as captured by the filter
     * @return the response
     */
    HttpResponse manufacture(HttpRequest originalRequest, HarRequest fullCapturedRequest);

}
