package io.github.mike10004.vhs;

import net.lightbody.bmp.core.har.HarEntry;
import net.lightbody.bmp.core.har.HarNameValuePair;
import net.lightbody.bmp.core.har.HarRequest;

class BmpRequestParserTest extends RequestParserTestBase<HarEntry> {

    @Override
    protected RequestParser<HarEntry> createParser() {
        return new RequestParser<>(new BmpHarBridge());
    }

    @SuppressWarnings("Duplicates")
    @Override
    protected HarEntry createEntryWithRequest(String method, String urlStr, String... headers) {
        HarRequest request = new HarRequest();
        request.setMethod("GET");
        request.setUrl(urlStr);
        for (int i = 0; i < headers.length; i += 2) {
            request.getHeaders().add(new HarNameValuePair(headers[i], headers[i+1]));
        }
        HarEntry entry = new HarEntry();
        entry.setRequest(request);
        return entry;
    }

}