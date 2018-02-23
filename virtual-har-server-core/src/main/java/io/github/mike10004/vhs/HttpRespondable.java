package io.github.mike10004.vhs;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteSource;
import com.google.common.net.MediaType;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public interface HttpRespondable {

    int getStatus();
    Stream<? extends Entry<String, String>> streamHeaders();

    /**
     *
     * @param out an output stream
     * @return the content type (value for Content-Type header)
     */
    MediaType writeBody(OutputStream out) throws IOException;

    /**
     * Gets the content type if it is available.
     * @return the content type
     */
    @Nullable
    MediaType previewContentType();

    static HttpRespondable inMemory(int status, Multimap<String, String> headers, MediaType contentType, byte[] body) {
        requireNonNull(headers, "headers");
        requireNonNull(contentType, "contentType");
        requireNonNull(body, "body");
        return ImmutableHttpRespondable.builder(status)
                .headers(headers)
                .contentType(contentType)
                .bodySource(ByteSource.wrap(body))
                .build();
    }

    class ImmutableHttpRespondable implements HttpRespondable {
        private final int status;
        private final MediaType contentType;
        private final ImmutableMultimap<String, String> headers;
        private final ByteSource bodySource;

        private ImmutableHttpRespondable(Builder builder) {
            status = builder.status;
            contentType = builder.contentType;
            headers = ImmutableMultimap.copyOf(builder.headers);
            bodySource = builder.bodySource;
        }

        public static Builder builder(HttpRespondable source) throws IOException {
            Builder b = builder(source.getStatus());
            if (source instanceof ImmutableHttpRespondable) {
                ImmutableHttpRespondable src = (ImmutableHttpRespondable) source;
                return b.bodySource(src.bodySource)
                        .contentType(src.contentType)
                        .headers(src.headers);
            } else {
                source.streamHeaders().forEach(b::header);
                ByteArrayOutputStream baos = new ByteArrayOutputStream(256);
                MediaType contentType = source.writeBody(baos);
                b.contentType(contentType);
                b.bodySource(ByteSource.wrap(baos.toByteArray()));
            }
            return b;
        }

        public static Builder builder(int status) {
            return new Builder(status);
        }

        @Override
        public MediaType previewContentType() {
            return contentType;
        }

        @Override
        public int getStatus() {
            return status;
        }

        @Override
        public Stream<? extends Entry<String, String>> streamHeaders() {
            return headers.entries().stream();
        }

        @Override
        public MediaType writeBody(OutputStream out) throws IOException {
            bodySource.copyTo(out);
            return contentType;
        }

        public static final class Builder {
            private final int status;
            private MediaType contentType = MediaType.OCTET_STREAM;
            private Multimap<String, String> headers = ArrayListMultimap.create();
            private ByteSource bodySource = ByteSource.empty();

            private Builder(int status) {
                this.status = status;
            }

            public Builder header(Map.Entry<String, String> entry) {
                return header(entry.getKey(), entry.getValue());
            }

            public Builder header(String name, String value) {
                headers.put(name, value);
                return this;
            }

            public Builder headers(Multimap<String, String> val) {
                headers.putAll(val);
                return this;
            }

            public Builder bodySource(ByteSource val) {
                bodySource = requireNonNull(val);
                return this;
            }

            public Builder contentType(MediaType contentType) {
                this.contentType = requireNonNull(contentType);
                return this;

            }

            public ImmutableHttpRespondable build() {
                return new ImmutableHttpRespondable(this);
            }
        }
    }
}
