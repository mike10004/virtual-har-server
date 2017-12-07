package io.github.mike10004.vhs;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import io.github.mike10004.vhs.repackaged.org.apache.http.client.utils.URLEncodedUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Stream;

/**
 *
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
    HttpRespondable parseResponse(E harEntry) throws IOException;

    class Defaults {

        private Defaults() {}

        /**
         * Creates a lowercase-keyed multimap from a list of headers.
         */
        @VisibleForTesting
        public static Multimap<String, String> indexHeaders(Stream<? extends Entry<String, String>> entryHeaders) {
            Multimap<String, String> headers = ArrayListMultimap.create();
            entryHeaders.forEach(header -> {
                headers.put(header.getKey().toLowerCase(), header.getValue());
            });
            return headers;
        }

        @Nullable
        @VisibleForTesting
        public static Multimap<String, String> parseQuery(URI uri) {
            if (uri.getQuery() == null) {
                return null;
            }
            List<Entry<String, String>> nvps = URLEncodedUtils.parse(uri, StandardCharsets.UTF_8);
            Multimap<String, String> mm = ArrayListMultimap.create();
            nvps.forEach(nvp -> {
                mm.put(nvp.getKey().toLowerCase(), nvp.getValue());
            });
            return mm;
        }

    }

}
