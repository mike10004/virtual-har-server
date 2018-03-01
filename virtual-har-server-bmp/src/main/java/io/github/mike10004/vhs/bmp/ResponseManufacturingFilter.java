package io.github.mike10004.vhs.bmp;

import com.google.common.collect.ImmutableList;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import net.lightbody.bmp.core.har.HarNameValuePair;
import net.lightbody.bmp.core.har.HarPostData;
import net.lightbody.bmp.core.har.HarPostDataParam;
import net.lightbody.bmp.core.har.HarRequest;
import net.lightbody.bmp.exception.UnsupportedCharsetException;
import net.lightbody.bmp.filters.ClientRequestCaptureFilter;
import net.lightbody.bmp.filters.HttpsAwareFiltersAdapter;
import net.lightbody.bmp.util.BrowserMobHttpUtil;
import org.littleshoot.proxy.impl.ProxyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

/**
 * Implementation of {@code HttpFilters} that sends a notification when an HTTP response
 * is received. This is still a pretty rough implementation, but it may suffice for
 * limited use cases at this stage. Don't expect the HTTP request/response objects
 * contained in the notification to be perfect representations.
 *
 * <p>Most of this code is copied from {@link net.lightbody.bmp.filters.HarCaptureFilter}.
 * The main difference is that the HAR filter keeps adding to the HAR object when
 * HTTP requests or responses are intercepted, but this implementation accumulates
 * (most of) the same data and packages it up in a notification when the request/response
 * interaction is completed. The interaction is considered completed when one of four methods
 * is invoked:
 * <ul>
 *     <li>{@link #serverToProxyResponse(HttpObject)} if everything goes normally,</li>
 *     <li>or one of the failure methods:
 *        <ul>
 *            <li>{@link #serverToProxyResponseTimedOut()}</li>
 *            <li>{@link #proxyToServerConnectionFailed()}</li>
 *            <li>{@link #proxyToServerResolutionFailed(String)}</li>
 *        </ul>
 *     </li>
 * </ul>
 */
public class ResponseManufacturingFilter extends HttpsAwareFiltersAdapter {

    private static final Logger log = LoggerFactory.getLogger(ResponseManufacturingFilter.class);

    private final HarRequest harRequest = new HarRequest();

    /**
     * The requestCaptureFilter captures all request content, including headers, trailing headers, and content. This filter
     * delegates to it when the clientToProxyRequest() callback is invoked. If this request does not need content capture, the
     * ClientRequestCaptureFilter filter will not be instantiated and will not capture content.
     */
    private final ClientRequestCaptureFilter requestCaptureFilter;

    private final ResponseManufacturer responseManufacturer;

    /**
     * The "real" original request, as captured by the {@link #clientToProxyRequest(io.netty.handler.codec.http.HttpObject)} method.
     */
    private volatile HttpRequest capturedOriginalRequest;

    /**
     * Create a new instance.
     * @param originalRequest the original HttpRequest from the HttpFiltersSource factory
     * @param ctx channel handler context
     * @throws IllegalArgumentException if request method is {@code CONNECT}
     */
    public ResponseManufacturingFilter(HttpRequest originalRequest, ChannelHandlerContext ctx, ResponseManufacturer responseManufacturer) {
        super(originalRequest, ctx);
        if (ProxyUtils.isCONNECT(originalRequest)) {
            throw new IllegalArgumentException("Attempted traffic listener capture for HTTP CONNECT request");
        }
        requestCaptureFilter = new ClientRequestCaptureFilter(originalRequest);
        this.responseManufacturer = requireNonNull(responseManufacturer);
    }

    private transient final Object responseLock = new Object();
    private volatile boolean responseSent;
    @Override
    public HttpResponse clientToProxyRequest(HttpObject httpObject) {
        // if a ServerResponseCaptureFilter is configured, delegate to it to collect the client request. if it is not
        // configured, we still need to capture basic information (timings, possibly client headers, etc.), just not content.
        requestCaptureFilter.clientToProxyRequest(httpObject);
        if (httpObject instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) httpObject;
            this.capturedOriginalRequest = httpRequest;
            // associate this request's HarRequest object with the har entry
            populateHarRequestFromHttpRequest(httpRequest, harRequest);
            captureQueryParameters(httpRequest, harRequest);
            captureRequestHeaders(httpRequest, harRequest);
        }

        if (httpObject instanceof LastHttpContent) {
            LastHttpContent lastHttpContent = (LastHttpContent) httpObject;
            captureTrailingHeaders(lastHttpContent, harRequest);
            captureRequestContent(requestCaptureFilter.getHttpRequest(), requestCaptureFilter.getFullRequestContents(), harRequest);
        }
        synchronized (responseLock) {
            if (responseSent) {
                throw new IllegalStateException("response already sent");
            }
            log.debug("producing response for {}", describe(httpObject));
            responseSent = true;
        }
        HttpResponse response = produceResponse(capturedOriginalRequest, harRequest);
        return response;
    }

    @Nullable
    protected String describe(@Nullable HttpObject object) {
        if (object != null) {
            if (object instanceof HttpRequest) {
                String uri = ((HttpRequest)object).getUri();
                String method = ((HttpRequest)object).getMethod().name();
                return String.format("%s %s", uri, method);
            }
        }
        return null;
    }

    protected HttpResponse produceResponse(HttpRequest httpRequest, HarRequest harRequest) {
        return responseManufacturer.manufacture(httpRequest, harRequest);
    }

    @Override
    public HttpObject proxyToClientResponse(HttpObject httpObject) {
        return super.proxyToClientResponse(httpObject);
    }

    static class UnreachableCallbackException extends IllegalStateException {}

    @Override
    public HttpObject serverToProxyResponse(HttpObject httpObject) {
        return raiseUnreachable();
    }

    /**
     * Populates a HarRequest object using the method, url, and HTTP version of the specified request.
     * @param httpRequest HTTP request on which the HarRequest will be based
     */
    private void populateHarRequestFromHttpRequest(HttpRequest httpRequest, HarRequest harRequest) {
        harRequest.setMethod(httpRequest.getMethod().toString());
        harRequest.setUrl(getFullUrl(httpRequest));
        harRequest.setHttpVersion(httpRequest.getProtocolVersion().text());
        // the HAR spec defines the request.url field as:
        //     url [string] - Absolute URL of the request (fragments are not included).
        // the URI on the httpRequest may only identify the path of the resource, so find the full URL.
        // the full URL consists of the scheme + host + port (if non-standard) + path + query params + fragment.
    }

    protected void captureQueryParameters(HttpRequest httpRequest, HarRequest harRequest) {
        // capture query parameters. it is safe to assume the query string is UTF-8, since it "should" be in US-ASCII (a subset of UTF-8),
        // but sometimes does include UTF-8 characters.
        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(httpRequest.getUri(), StandardCharsets.UTF_8);

        try {
            for (Map.Entry<String, List<String>> entry : queryStringDecoder.parameters().entrySet()) {
                for (String value : entry.getValue()) {
                    harRequest.getQueryString().add(new HarNameValuePair(entry.getKey(), value));
                }
            }
        } catch (IllegalArgumentException e) {
            // QueryStringDecoder will throw an IllegalArgumentException if it cannot interpret a query string. rather than cause the entire request to
            // fail by propagating the exception, simply skip the query parameter capture.
            log.info("Unable to decode query parameters on URI: " + httpRequest.getUri(), e);
        }
    }

    protected void captureRequestHeaders(HttpRequest httpRequest, HarRequest harRequest) {
        HttpHeaders headers = httpRequest.headers();
        captureHeaders(headers, harRequest);
    }

    protected void captureTrailingHeaders(LastHttpContent lastHttpContent, HarRequest harRequest) {
        HttpHeaders headers = lastHttpContent.trailingHeaders();
        captureHeaders(headers, harRequest);
    }

    protected void captureHeaders(HttpHeaders headers, HarRequest harRequest) {
        for (Map.Entry<String, String> header : headers.entries()) {
            harRequest.getHeaders().add(new HarNameValuePair(header.getKey(), header.getValue()));
        }
    }

    protected void captureRequestContent(HttpRequest httpRequest, byte[] fullMessage, HarRequest harRequest) {
        if (fullMessage.length == 0) {
            return;
        }

        String contentType = HttpHeaders.getHeader(httpRequest, HttpHeaders.Names.CONTENT_TYPE);
        if (contentType == null) {
            log.warn("No content type specified in request to {}. Content will be treated as {}", httpRequest.getUri(), BrowserMobHttpUtil.UNKNOWN_CONTENT_TYPE);
            contentType = BrowserMobHttpUtil.UNKNOWN_CONTENT_TYPE;
        }

        HarPostData postData = new HarPostData();
        harRequest.setPostData(postData);

        postData.setMimeType(contentType);

        final boolean urlEncoded = contentType.startsWith(HttpHeaders.Values.APPLICATION_X_WWW_FORM_URLENCODED);

        Charset charset;
        try {
            charset = BrowserMobHttpUtil.readCharsetInContentTypeHeader(contentType);
        } catch (UnsupportedCharsetException e) {
            log.warn("Found unsupported character set in Content-Type header '{}' in HTTP request to {}. Content will not be captured in HAR.", contentType, httpRequest.getUri(), e);
            return;
        }

        if (charset == null) {
            // no charset specified, so use the default -- but log a message since this might not encode the data correctly
            charset = BrowserMobHttpUtil.DEFAULT_HTTP_CHARSET;
            log.debug("No charset specified; using charset {} to decode contents to {}", charset, httpRequest.getUri());
        }

        if (urlEncoded) {
            String textContents = BrowserMobHttpUtil.getContentAsString(fullMessage, charset);

            QueryStringDecoder queryStringDecoder = new QueryStringDecoder(textContents, charset, false);

            ImmutableList.Builder<HarPostDataParam> paramBuilder = ImmutableList.builder();

            for (Map.Entry<String, List<String>> entry : queryStringDecoder.parameters().entrySet()) {
                for (String value : entry.getValue()) {
                    paramBuilder.add(new HarPostDataParam(entry.getKey(), value));
                }
            }

            harRequest.getPostData().setParams(paramBuilder.build());
        } else {
            //TODO: implement capture of files and multipart form data

            // not URL encoded, so let's grab the body of the POST and capture that
            String postBody = BrowserMobHttpUtil.getContentAsString(fullMessage, charset);
            harRequest.getPostData().setText(postBody);
        }
    }

    @Override
    public void proxyToServerResolutionSucceeded(String serverHostAndPort, InetSocketAddress resolvedRemoteAddress) {
        raiseUnreachable();
    }

    @Override
    public void proxyToServerRequestSending() {
        raiseUnreachable();
    }

    @Nullable // never returns
    private HttpObject raiseUnreachable() {
        HttpRequest captured = capturedOriginalRequest;
        checkState(captured != null);
        log.error("should be unreachable: {} {}", captured.getMethod(), captured.getUri());
        throw new UnreachableCallbackException();
    }

    @Override
    public void proxyToServerResolutionFailed(String hostAndPort) {
        raiseUnreachable();
    }


    @Override
    public void proxyToServerConnectionFailed() {
        raiseUnreachable();
    }

    @Override
    public void serverToProxyResponseTimedOut() {
        raiseUnreachable();
    }

}
