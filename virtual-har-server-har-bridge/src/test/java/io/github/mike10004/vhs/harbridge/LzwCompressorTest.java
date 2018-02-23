package io.github.mike10004.vhs.harbridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LzwCompressorTest {

    @Test
    void compressAndDecompress() throws Exception {
        String data = "This is a low-entropy string. This is a low-entropy string. This is a low-entropy string. This is a low-entropy string.";
        byte[] inbytes = data.getBytes();
        System.out.format("%d input bytes%n", inbytes.length);
        byte[] compressedBytes = new LzwCompressor().compress(inbytes);
        System.out.format("%d compressed bytes%n", compressedBytes.length);
        byte[] decompressedBytes = new LzwCompressor().decompress(compressedBytes);
        System.out.format("%d decompressed bytes%n", decompressedBytes.length);
        String actual = new String(decompressedBytes);
        assertEquals(data, actual, "string");
    }
}