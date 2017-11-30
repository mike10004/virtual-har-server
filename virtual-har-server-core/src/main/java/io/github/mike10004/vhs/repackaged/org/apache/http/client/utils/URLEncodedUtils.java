/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package io.github.mike10004.vhs.repackaged.org.apache.http.client.utils;

import io.github.mike10004.vhs.repackaged.org.apache.http.message.ParserCursor;
import io.github.mike10004.vhs.repackaged.org.apache.http.message.TokenParser;
import io.github.mike10004.vhs.repackaged.org.apache.http.util.CharArrayBuffer;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;


public class URLEncodedUtils {

    private URLEncodedUtils() {}

    /**
     * Returns a list of {@link NameValuePair NameValuePairs} as built from the URI's query portion. For example, a URI
     * of {@code http://example.org/path/to/file?a=1&b=2&c=3} would return a list of three NameValuePairs, one for a=1,
     * one for b=2, and one for c=3. By convention, {@code '&'} and {@code ';'} are accepted as parameter separators.
     * <p>
     * This is typically useful while parsing an HTTP PUT.
     *
     * This API is currently only used for testing.
     *
     * @param uri
     *        URI to parse
     * @param charset
     *        Charset to use while parsing the query
     * @return a list of {@link NameValuePair} as built from the URI's query portion.
     *
     * @since 4.5
     */
    public static List<Entry<String, String>> parse(final URI uri, final Charset charset) {
        Objects.requireNonNull(uri, "URI");
        final String query = uri.getRawQuery();
        if (query != null && !query.isEmpty()) {
            return parse(query, charset);
        }
        return Collections.emptyList();
    }

    /**
     * Returns a list of {@link NameValuePair NameValuePairs} as parsed from the given string using the given character
     * encoding. By convention, {@code '&'} and {@code ';'} are accepted as parameter separators.
     *
     * @param s
     *            text to parse.
     * @param charset
     *            Encoding to use when decoding the parameters.
     * @return a list of {@link NameValuePair} as built from the URI's query portion.
     *
     * @since 4.2
     */
    public static List<Map.Entry<String, String>> parse(final String s, final Charset charset) {
        if (s == null) {
            return Collections.emptyList();
        }
        final CharArrayBuffer buffer = new CharArrayBuffer(s.length());
        buffer.append(s);
        return parse(buffer, charset, QP_SEP_A, QP_SEP_S);
    }

    /**
     * The default HTML form content type.
     */
    public static final String CONTENT_TYPE = "application/x-www-form-urlencoded";

    private static final char QP_SEP_A = '&';
    private static final char QP_SEP_S = ';';
    private static final String NAME_VALUE_SEPARATOR = "=";

    /**
     * Returns a list of {@link NameValuePair NameValuePairs} as parsed from the given string using
     * the given character encoding.
     *
     * @param buf
     *            text to parse.
     * @param charset
     *            Encoding to use when decoding the parameters.
     * @param separators
     *            element separators.
     * @return a list of {@link NameValuePair} as built from the URI's query portion.
     *
     * @since 4.4
     */
    public static List<Map.Entry<String, String>> parse(
            final CharArrayBuffer buf, final Charset charset, final char... separators) {
        Objects.requireNonNull(buf, "Char array buffer");
        final TokenParser tokenParser = TokenParser.INSTANCE;
        final BitSet delimSet = new BitSet();
        for (final char separator: separators) {
            delimSet.set(separator);
        }
        final ParserCursor cursor = new ParserCursor(0, buf.length());
        final List<Map.Entry<String, String>> list = new ArrayList<>();
        while (!cursor.atEnd()) {
            delimSet.set('=');
            final String name = tokenParser.parseToken(buf, cursor, delimSet);
            String value = null;
            if (!cursor.atEnd()) {
                final int delim = buf.charAt(cursor.getPos());
                cursor.updatePos(cursor.getPos() + 1);
                if (delim == '=') {
                    delimSet.clear('=');
                    value = tokenParser.parseValue(buf, cursor, delimSet);
                    if (!cursor.atEnd()) {
                        cursor.updatePos(cursor.getPos() + 1);
                    }
                }
            }
            if (!name.isEmpty()) {
                list.add(new SimpleImmutableEntry<>(
                        decodeFormFields(name, charset),
                        decodeFormFields(value, charset)));
            }
        }
        return list;
    }

    /**
     * Decode/unescape www-url-form-encoded content.
     *
     * @param content the content to decode, will decode '+' as space
     * @param charset the charset to use
     * @return encoded string
     */
    private static String decodeFormFields (final String content, final Charset charset) {
        if (content == null) {
            return null;
        }
        return urlDecode(content, charset != null ? charset : StandardCharsets.UTF_8, true);
    }

    /**
     * Decode/unescape a portion of a URL, to use with the query part ensure {@code plusAsBlank} is true.
     *
     * @param content the portion to decode
     * @param charset the charset to use
     * @param plusAsBlank if {@code true}, then convert '+' to space (e.g. for www-url-form-encoded content), otherwise leave as is.
     * @return encoded string
     */
    private static String urlDecode(
            final String content,
            final Charset charset,
            final boolean plusAsBlank) {
        if (content == null) {
            return null;
        }
        final ByteBuffer bb = ByteBuffer.allocate(content.length());
        final CharBuffer cb = CharBuffer.wrap(content);
        while (cb.hasRemaining()) {
            final char c = cb.get();
            if (c == '%' && cb.remaining() >= 2) {
                final char uc = cb.get();
                final char lc = cb.get();
                final int u = Character.digit(uc, 16);
                final int l = Character.digit(lc, 16);
                if (u != -1 && l != -1) {
                    bb.put((byte) ((u << 4) + l));
                } else {
                    bb.put((byte) '%');
                    bb.put((byte) uc);
                    bb.put((byte) lc);
                }
            } else if (plusAsBlank && c == '+') {
                bb.put((byte) ' ');
            } else {
                bb.put((byte) c);
            }
        }
        bb.flip();
        return charset.decode(bb).toString();
    }

}
