package io.github.mike10004.vhs.bmp;

import io.netty.handler.codec.http.HttpResponse;


/**
 * Interface that defines a method to manufacture responses that the proxy
 * will send to the client.
 */
public interface BmpResponseManufacturer {

    /**
     * Manufactures a response for a given request.
     * @param capture the request
     * @return the response
     */
    HttpResponse manufacture(RequestCapture capture);

}
