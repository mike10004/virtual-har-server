package io.github.mike10004.vhs;

import io.github.mike10004.vhs.harbridge.ParsedRequest;

import java.io.IOException;

/**
 * Interface that defines methods used to parse requests and responses from
 * a HAR entry.
 * @param <E> har entry type used by HAR library
 */
public interface EntryParser<E> {

    /**
     * Parses the request present in a HAR entry.
     * @param harEntry the HAR entry
     * @return the parsed request
     */
    ParsedRequest parseRequest(E harEntry) throws IOException;

    /**
     * Parses the HTTP response present in a HAR entry.
     * @param harEntry the HAR entry
     * @return the parsed response
     */
    HttpRespondable parseResponse(ParsedRequest request, E harEntry) throws IOException;

}
