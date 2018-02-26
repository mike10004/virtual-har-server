package io.github.mike10004.vhs.harbridge;

import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface HttpContentCodec {

    default byte[] compress(byte[] uncompressed) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(uncompressed.length);
        try (OutputStream gout = openCompressionFilter(baos, uncompressed.length)) {
            gout.write(uncompressed);
        }
        return baos.toByteArray();
    }

    default byte[] decompress(byte[] compressed) throws IOException {
        try (InputStream in = openDecompressingStream(new ByteArrayInputStream(compressed))) {
            return ByteStreams.toByteArray(in);
        }
    }

    OutputStream openCompressionFilter(OutputStream sink, int uncompressedLength) throws IOException;

    InputStream openDecompressingStream(InputStream source) throws IOException;

    static HttpContentCodec identity() {
        return IdentityCodec.INSTANCE;
    }

    class IdentityCodec implements HttpContentCodec {

        protected IdentityCodec() {}

        private static final IdentityCodec INSTANCE = new IdentityCodec();

        @Override
        public byte[] compress(byte[] uncompressed) {
            return uncompressed;
        }

        @Override
        public byte[] decompress(byte[] compressed) {
            return compressed;
        }

        @Override
        public OutputStream openCompressionFilter(OutputStream sink, int uncompressedLength) {
            return sink;
        }

        @Override
        public InputStream openDecompressingStream(InputStream source)  {
            return source;
        }
    }
}
