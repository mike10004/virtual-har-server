package io.github.mike10004.vhs.lzw;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Lz {
    // Get a byte from a buffered input
    public static short getc(InputStream ip) throws IOException {
        int rbyte;
        rbyte = ip.read();
        return (short)rbyte;
    }

    // Write a byte to a buffered input
    public static void putc(byte val, OutputStream op) throws IOException {
        op.write(val);
    }

    public static void flush(OutputStream op) throws IOException {
        op.flush();
    }
}
