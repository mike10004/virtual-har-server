package io.github.mike10004.vhs.lzw;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LzwTest {

    @Test
    void compressAndDecompress() throws Exception {
        String data = "This is a low-entropy string. This is a low-entropy string. This is a low-entropy string. This is a low-entropy string.";
        ByteArrayOutputStream buffer1 = new ByteArrayOutputStream(data.length() + 16);
        byte[] inbytes = data.getBytes();
        System.out.format("%d input bytes%n", inbytes.length);
        try (InputStream bin = new ByteArrayInputStream(inbytes)) {
            new Codec().compress(bin, buffer1);
        }
        byte[] compressedBytes = buffer1.toByteArray();
        System.out.format("%d compressed bytes%n", compressedBytes.length);
        ByteArrayOutputStream buffer2 = new ByteArrayOutputStream(data.length() + 16);
        try (InputStream bin = new ByteArrayInputStream(compressedBytes)) {
            new Codec().decompress(bin, buffer2);
        }
        byte[] decompressedBytes = buffer2.toByteArray();
        System.out.format("%d decompressed bytes%n", decompressedBytes.length);
        String actual = new String(decompressedBytes);
        assertEquals(data, actual, "string");
    }
}