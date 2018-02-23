package io.github.mike10004.vhs.harbridge;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.brotli.dec.BrotliInputStream;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class HttpContentCodecs {

    @SuppressWarnings("unused")
    public static final String CONTENT_ENCODING_GZIP = "gzip";
    @SuppressWarnings("unused")
    public static final String CONTENT_ENCODING_COMPRESS = "compress";
    @SuppressWarnings("unused")
    public static final String CONTENT_ENCODING_DEFLATE = "deflate";
    @SuppressWarnings("unused")
    public static final String CONTENT_ENCODING_IDENTITY = "identity";
    @SuppressWarnings("unused")
    public static final String CONTENT_ENCODING_BROTLI = "br";

    private HttpContentCodecs() {}

    private static final Splitter ENCODING_SPLITTER = Splitter.on(Pattern.compile("\\s*,\\s*")).omitEmptyStrings().trimResults();

    public static ImmutableList<String> parseEncodings(@Nullable String contentEncodingHeaderValue) {
        if (contentEncodingHeaderValue == null) {
            return ImmutableList.of();
        }
        return ENCODING_SPLITTER
                .splitToList(contentEncodingHeaderValue)
                .stream().map(String::toLowerCase).collect(ImmutableList.toImmutableList());
    }

    static class GzipCompressor implements HttpContentCodec {

        @Override
        public OutputStream openCompressionFilter(OutputStream sink, int uncompressedLength) throws IOException {
            return new GZIPOutputStream(sink);
        }

        @Override
        public InputStream openDecompressingStream(InputStream source) throws IOException {
            return new GZIPInputStream(source);
        }
    }

    @SuppressWarnings("RedundantThrows")
    static class ZlibCompressor implements HttpContentCodec {
        @Override
        public InputStream openDecompressingStream(InputStream source) throws IOException {
            return new java.util.zip.DeflaterInputStream(source);
        }

        @Override
        public OutputStream openCompressionFilter(OutputStream sink, int uncompressedLength) throws IOException {
            return new java.util.zip.DeflaterOutputStream(sink);
        }
    }

    static class LzwCompressor implements HttpContentCodec {
        @Override
        public byte[] compress(byte[] uncompressed) {
            return new LzwCompressor().compress(uncompressed);
        }

        @Override
        public byte[] decompress(byte[] compressed) {
            return new LzwCompressor().decompress(compressed);
        }

        @Override
        public OutputStream openCompressionFilter(OutputStream sink, int uncompressedLength) throws IOException {
            throw new IOException("lzw stream compression not supported");
        }

        @Override
        public InputStream openDecompressingStream(InputStream source) throws IOException {
            throw new IOException("lzw stream decompression not supported");
        }
    }

    static class BrotliCompressor implements HttpContentCodec {
        @Override
        public OutputStream openCompressionFilter(OutputStream sink, int uncompressedLength) throws IOException {
            throw new IOException("brotli compression not supported");
        }

        @Override
        public InputStream openDecompressingStream(InputStream source) throws IOException {
            return new BrotliInputStream(source);
        }
    }

    @Nullable
    public static HttpContentCodec getCodec(String encoding) {
        return compressors.get(encoding);
    }

    private static final ImmutableMap<String, HttpContentCodec> compressors = ImmutableMap.<String, HttpContentCodec>builder()
            .put(CONTENT_ENCODING_GZIP, new GzipCompressor())
            .put("deflate", new ZlibCompressor())
            .put("compress", new LzwCompressor())
            .put("identity", HttpContentCodec.identity())
            .put("br", new BrotliCompressor())
            .build();
}
