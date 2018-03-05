package io.github.mike10004.vhs.bmp;

import io.github.mike10004.vhs.repackaged.fi.iki.elonen.NanoHTTPD;
import io.github.mike10004.vhs.repackaged.fi.iki.elonen.NanoHTTPD.IHTTPSession;

interface NanoResponseManufacturer {

    NanoHTTPD.Response manufacture(IHTTPSession session);

}
