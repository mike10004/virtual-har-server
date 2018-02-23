package io.github.mike10004.vhs.lzw;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Lz {

    public static final int BYTESIZE                = 8;
    public static final int BYTEMASK                = 0xff;
    public static final int BITSIZE                 = 1;
    public static final int FIRSTCW                 = 0x100;
    public static final int NULLCW                  = 0xFFFF;
    public static final int EOFFLUSH                = NULLCW;
    public static final int MINCWLEN                = 9;
    public static final int MAXCWLEN                = 12;
    public static final int CODEWORDMASK            = ((1 << MAXCWLEN) - 1);
    public static final int DICTFULL                = (1 << MAXCWLEN);
    public static final int NOMATCH                 = DICTFULL;
    public static final int MAXWORDLENGTH           = (1 << MAXCWLEN);
    public static final int NOERROR                 = 0;

    private Lz() {}

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
