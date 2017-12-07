package io.github.mike10004.vhs.nanohttpd;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;

@SuppressWarnings("unused")
public class ImmutableHttpMessage {

    private final HttpContentSource dataSource;
    public final ImmutableMultimap<String, String> headers;

    protected ImmutableHttpMessage(MessageBuilder<?> builder) {
        dataSource = builder.dataSource;
        headers = ImmutableMultimap.copyOf(builder.headers);
    }

    protected MoreObjects.ToStringHelper toStringHelper() {
        return MoreObjects.toStringHelper(this)
                .add("contentSource", dataSource)
                .add("headers.size", headers.size());
    }

    public ByteSource getContentAsBytes() {
        return dataSource.asBytes(this);
    }

    public CharSource getContentAsChars(Charset charsetIfNotSpecified) {
        return dataSource.asChars(this, charsetIfNotSpecified);
    }

    protected static CharSource encodeToBase64(ByteSource byteSource) {
        return new CharSource() {
            @Override
            public Reader openStream() throws IOException {
                return new StringReader(Base64.getEncoder().encodeToString(byteSource.read()));
            }
        };
    }

    protected static class Base64ByteSource extends ByteSource {

        private final String base64Data;
        private final Supplier<Decoder> decoderSupplier;

        public static Base64ByteSource forBase64String(String base64Data) {
            return new Base64ByteSource(base64Data, Base64::getDecoder);
        }

        private Base64ByteSource(String base64Data, Supplier<Decoder> decoderSupplier) {
            this.base64Data = checkNotNull(base64Data);
            this.decoderSupplier = checkNotNull(decoderSupplier);
        }

        @Override
        public InputStream openStream() throws IOException {
            ByteSource base64Source = CharSource.wrap(base64Data).asByteSource(StandardCharsets.US_ASCII);
            return decoderSupplier.get().wrap(base64Source.openStream());
        }
    }

    public interface HttpContentSource {

        boolean isNativelyText();
        CharSource asChars(ImmutableHttpMessage message, Charset charsetIfNotSpecified);
        ByteSource asBytes(ImmutableHttpMessage message);

        static HttpContentSource empty() {
            return new HttpContentSource() {
                @Override
                public boolean isNativelyText() {
                    return false;
                }

                @Override
                public CharSource asChars(ImmutableHttpMessage message, Charset charsetIfNotSpecified) {
                    return CharSource.empty();
                }

                @Override
                public ByteSource asBytes(ImmutableHttpMessage message) {
                    return ByteSource.empty();
                }

                @Override
                public String toString() {
                    return "HttpContentSource{empty}";
                }
            };
        }

        static HttpContentSource fromBytes(ByteSource byteSource) {
            return new OriginalByteSource(byteSource);
        }

        static HttpContentSource fromChars(CharSource charSource, Charset charsetForEncoding) {
            return new OriginalCharSource(charSource, charsetForEncoding);
        }

        static HttpContentSource fromBase64(String base64Data) {
            return new OriginalByteSource(Base64ByteSource.forBase64String(base64Data));
        }
    }

    protected static final class OriginalByteSource implements HttpContentSource {

        private final ByteSource byteSource;

        public OriginalByteSource(ByteSource byteSource) {
            this.byteSource = checkNotNull(byteSource);
        }

        @Override
        public CharSource asChars(ImmutableHttpMessage message, Charset charsetIfNotSpecified) {
            @Nullable String contentType = message.getFirstHeaderValue(HttpHeaders.CONTENT_TYPE);
            if (contentType != null) {
                MediaType mediaType = MediaType.parse(contentType);
                Charset charset = mediaType.charset().orNull();
                if (charset == null) {
                    LoggerFactory.getLogger(OriginalByteSource.class).info("charset not specified by content type {}; falling back to {}", contentType, charsetIfNotSpecified);
                    charset = Objects.requireNonNull(charsetIfNotSpecified, "fallback charset must be non-null");
                }
                return byteSource.asCharSource(charset);
            }
            throw new UnsupportedOperationException("cannot decode bytes that do not have explicit content type with charset defined; this content type is null");
        }

        @Override
        public ByteSource asBytes(ImmutableHttpMessage message) {
            return byteSource;
        }

        @Override
        public boolean isNativelyText() {
            return false;
        }

        @Override
        public String toString() {
            return "OriginalByteSource{" +
                    "nativelyText=" + isNativelyText() +
                    ",byteSource.size=" + byteSource.sizeIfKnown().orNull() +
                    '}';
        }
    }

    protected static final class OriginalCharSource implements HttpContentSource {

        private final CharSource charSource;
        private final Charset charsetForEncoding;

        public OriginalCharSource(CharSource charSource, Charset charsetForEncoding) {
            this.charSource = Objects.requireNonNull(charSource);
            this.charsetForEncoding = Objects.requireNonNull(charsetForEncoding);
        }

        @Override
        public boolean isNativelyText() {
            return true;
        }

        @Override
        public CharSource asChars(ImmutableHttpMessage message, Charset charsetIfNotSpecified) {
            return charSource;
        }

        @Override
        public ByteSource asBytes(ImmutableHttpMessage message) {
            return charSource.asByteSource(charsetForEncoding);
        }

        @Override
        public String toString() {
            return "OriginalCharSource{" +
                    "nativelyText=" + isNativelyText() +
                    ",charSource.length=" + charSource.lengthIfKnown().orNull() +
                    '}';
        }

        public static OriginalCharSource create(CharSource charSource, MediaType contentType) {
            Objects.requireNonNull(contentType);
            Charset charset = contentType.charset().orNull();
            if (charset == null) {
                throw new IllegalArgumentException("no charset specified");
            }
            return new OriginalCharSource(charSource, charset);
        }
    }

    /**
     * Finds headers by name, case-insensitively.
     * @param headerName header name
     * @return the headers
     */
    public Stream<Entry<String, String>> getHeaders(String headerName) {
        checkNotNull(headerName);
        return headers.entries().stream().filter(entry -> entry.getKey().equalsIgnoreCase(headerName));
    }

    public Stream<String> getHeaderValues(String headerName) {
        return getHeaders(headerName).map(Entry::getValue);
    }

    @Nullable
    public MediaType getContentType() {
        String headerValue = getFirstHeaderValue(HttpHeaders.CONTENT_TYPE);
        if (headerValue == null) {
            return null;
        }
        return MediaType.parse(headerValue);
    }

    @Nullable
    public String getFirstHeaderValue(String headerName) {
        return getHeaders(headerName).map(Entry::getValue).findFirst().orElse(null);
    }

    @SuppressWarnings("unchecked")
    public static abstract class MessageBuilder<B extends MessageBuilder> {

        private HttpContentSource dataSource = HttpContentSource.empty();
        private final Multimap<String, String> headers = ArrayListMultimap.create();

        protected MessageBuilder() {
        }

        public B content(HttpContentSource contentSource) {
            dataSource = checkNotNull(contentSource);
            return (B) this;
        }

        public B content(ByteSource byteSource) {
            dataSource = new OriginalByteSource(byteSource);
            return (B) this;
        }

        public B content(CharSource charSource, Charset charsetForEncoding) {
            dataSource = new OriginalCharSource(charSource, charsetForEncoding);
            return (B) this;
        }

        public B headers(Multimap<String, String> val) {
            headers.clear();
            headers.putAll(val);
            return (B) this;
        }

        public B addHeaders(Collection<? extends Entry<String, String>> headers) {
            return addHeaders(headers.stream());
        }

        public B addHeaders(Stream<? extends Entry<String, String>> headers) {
            headers.forEach(entry -> this.headers.put(entry.getKey(), entry.getValue()));
            return (B) this;
        }
    }
}
