package io.github.mike10004.vhs.harbridge;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * Transformer of {@link HarResponseData} objects.
 * @see HarResponseData#transform()
 */
public final class HarResponseDataTransformer {

    private Function<ByteSource, ByteSource> bodyTransform = Functions.identity();
    private Function<MediaType, MediaType> contentTypeTransform = Functions.identity();
    private Function<Stream<Map.Entry<String, String>>, Stream<Map.Entry<String, String>>> headersTransform = Functions.identity();

    private HarResponseDataTransformer() {
    }

    /**
     * Method for use by {@link HarResponseData#transform()}.
     * @return a new transformer instance
     */
    static HarResponseDataTransformer build() {
        return new HarResponseDataTransformer();
    }

    public HarResponseDataTransformer body(Function<ByteSource, ByteSource> bodyTransform) {
        this.bodyTransform = this.bodyTransform.andThen(bodyTransform);
        return this;
    }

    public HarResponseDataTransformer contentType(Function<MediaType, MediaType> contentTypeSupplier) {
        this.contentTypeTransform = this.contentTypeTransform.andThen(contentTypeSupplier);
        return this;
    }

    public HarResponseDataTransformer headers(Function<Stream<Map.Entry<String, String>>, Stream<Map.Entry<String, String>>> headersSupplier) {
        this.headersTransform = headersSupplier;
        return this;
    }

    public HarResponseDataTransformer filterHeaders(Predicate<? super Map.Entry<String, String>> filter) {
        return headers(stream -> stream.filter(filter));
    }

    public HarResponseDataTransformer mapHeader(Function<Map.Entry<String, String>, Map.Entry<String, String>> map) {
        return headers(stream -> stream.map(map));
    }

    public HarResponseDataTransformer mapHeaderWithName(String headerName, Function<String, String> valueMap) {
        // TODO handle malformed header lists by removing all headers with specified name and appending single new name-value pair
        return mapHeader(entry -> {
            if (headerName.equalsIgnoreCase(entry.getKey())) {
                String newValue = valueMap.apply(entry.getValue());
                if (!Objects.equals(entry.getValue(), newValue)) {
                    return new AbstractMap.SimpleImmutableEntry<>(headerName, newValue);
                }
            }
            return entry;
        });
    }

    public HarResponseDataTransformer replaceHeader(String headerName, String newValue) {
        return mapHeaderWithName(headerName, oldValue -> newValue);
    }

    public HarResponseDataTransformer replaceContentType(MediaType contentType) {
        return replaceHeader(HttpHeaders.CONTENT_TYPE, contentType.toString())
                .contentType(old -> contentType);
    }

    public LazyHarResponseData transform(HarResponseData wrapped) {
        requireNonNull(wrapped);
        return new LazyHarResponseData(() -> bodyTransform.apply(wrapped.getBody()),
                () -> contentTypeTransform.apply(wrapped.getContentType()),
                () -> headersTransform.apply(wrapped.headers().stream()).collect(ImmutableList.toImmutableList()));
    }
}
