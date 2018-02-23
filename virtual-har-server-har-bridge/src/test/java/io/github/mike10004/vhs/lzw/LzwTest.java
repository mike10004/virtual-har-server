package io.github.mike10004.vhs.lzw;

import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LzwTest {

    @Test
    void compressAndDecompress() throws Exception {
        String data = "This is a low-entropy string. This is a low-entropy string. This is a low-entropy string. This is a low-entropy string.";
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(data.length() + 16);
        byte[] inbytes = data.getBytes();
        System.out.format("%d input bytes%n", inbytes.length);
        try (BufferedInputStream bin = new BufferedInputStream(new ByteArrayInputStream(inbytes));
            BufferedOutputStream bout = new BufferedOutputStream(buffer)) {
            new Codec().compress(bin, bout);
        }
        byte[] compressedBytes = buffer.toByteArray();
        System.out.format("%d compressed bytes%n", compressedBytes.length);
        ByteArrayOutputStream buffer2 = new ByteArrayOutputStream(data.length() + 16);
        try (BufferedInputStream bin = new BufferedInputStream(new ByteArrayInputStream(compressedBytes));
            BufferedOutputStream bout = new BufferedOutputStream(buffer2)) {
            new Codec().decompress(bin, bout);
        }
        byte[] decompressedBytes = buffer2.toByteArray();
        System.out.format("%d decompressed bytes%n", decompressedBytes.length);
        String actual = new String(decompressedBytes);
        assertEquals(data, actual, "string");
    }
}