package io.github.mike10004.vhs.bmp;

import com.google.common.net.MediaType;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import io.github.mike10004.vhs.HttpRespondable;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

interface NanoResponseManufacturer {

    NanoHTTPD.Response manufacture(IHTTPSession session);

}
