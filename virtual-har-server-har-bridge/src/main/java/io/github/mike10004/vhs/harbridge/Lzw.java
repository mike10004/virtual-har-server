package io.github.mike10004.vhs.harbridge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Stack;

public class Lzw {

    private static final int BYTESIZE                = 8;
    private static final int BYTEMASK                = 0xff;
    private static final int BITSIZE                 = 1;
    private static final int FIRSTCW                 = 0x100;
    private static final int NULLCW                  = 0xFFFF;
    private static final int EOFFLUSH                = NULLCW;
    private static final int MINCWLEN                = 9;
    private static final int MAXCWLEN                = 12;
    private static final int CODEWORDMASK            = ((1 << MAXCWLEN) - 1);
    private static final int DICTFULL                = (1 << MAXCWLEN);
    private static final int NOMATCH                 = DICTFULL;
    private static final int MAXWORDLENGTH           = (1 << MAXCWLEN);
    private static final int NOERROR                 = 0;

    private Lzw() {}

    // Get a byte from a buffered input
    private static short getc(InputStream ip) throws IOException {
        int rbyte;
        rbyte = ip.read();
        return (short)rbyte;
    }

    // Write a byte to a buffered input
    private static void putc(byte val, OutputStream op) throws IOException {
        op.write(val);
    }

    private static void flush(OutputStream op) throws IOException {
        op.flush();
    }

    private static class Dict {

        // Internal data structures
        private int[][]     indirection_table = new int[DICTFULL][256];
        private int         next_available_codeword = FIRSTCW;
        private int         codeword_len = MINCWLEN;
        private boolean     compress_mode;
        private DictEntry[] dictionary = new DictEntry[DICTFULL];


        public Dict(boolean mode) {
            compress_mode = mode;

            for (int idx = 0; idx < DICTFULL; idx++)
                dictionary[idx] = new DictEntry();
        }

        //=======================================================================
        // Method name: reset_dictionary
        //
        // Description:
        //    Resets the dictionary
        //=======================================================================

        public int reset_dictionary() {
            // Reset common state
            next_available_codeword = FIRSTCW;

            return MINCWLEN;
        }

        //=======================================================================
        // Method name: entry_match
        //
        // Description:
        //    Returns TRUE if the dictionary entry at the specified
        //    address matches the specified byte. Otherwise FALSE.
        //=======================================================================

        protected int entry_match(int pointer, byte byte_val) {
            int addr;

            // Get possible dictionary entry value for match.
            // (This is not part of algorithm, but a method for
            // speeding up dictionary searches).
            addr = indirection_table[pointer][byte_val];

            // Test to see if we have a match at the address, and is in the
            // valid portion of the dictionary.
            if (!(addr >= FIRSTCW && addr < next_available_codeword &&
                        (dictionary_entry_byte(addr) == byte_val) &&
                        (dictionary_entry_pointer(addr) == pointer)))
                addr = NOMATCH; // Set addr to indicate no match

            // Return address of match (or no match)
            return addr;
        }

        //=======================================================================
        // Method name: build_entry
        //
        // Description:
        //    Creates a new dictionary entry at next_free_code, so
        //    long as the dictionary isn't full, in which case the
        //    build is not performed, and a partial_reset is done
        //    instead.
        //=======================================================================

        protected int build_entry(int codeword, byte byte_val) {

            // If the dictionary is full, reset it before doing a build
            if (dictionary_full())
                codeword_len = reset_dictionary();

            // Set the entry values for the pointer and bytes
            set_dictionary_entry_pointer(next_available_codeword, codeword);
            set_dictionary_entry_byte(next_available_codeword, byte_val);

            // Set the pointer table to point into the dictionary. (This is not
            // part of the algorithm, but a mechanism for fast dictionary
            // accesses.)
            if (compress_mode)
               indirection_table[codeword][byte_val] = next_available_codeword;

            // If we've just built an entry whose codeword value is greater than
            // the current output codeword size, then increment the current codeword
            // size. The decompression builds lag behind by one, so this event
            // is anticipated by an entry.
            if (codeword_len < MAXCWLEN) {
                if (next_available_codeword == (1 << codeword_len) - (!compress_mode ? 1 : 0))
                    codeword_len++;

            // If decompressing and we're one short of having a full dictionary,
            // reset the codeword_len. This anticipates a reset next build,
            // in a similar way as for the codeword length increment.
            } else if (!compress_mode && next_available_codeword == DICTFULL -1)
                codeword_len = MINCWLEN;

            if (next_available_codeword != DICTFULL)
                ++next_available_codeword;

            return codeword_len;
        }

        //=======================================================================
        // Public test and data hiding methods
        //=======================================================================
        protected boolean codeword_valid (int codeword) {
            return codeword <= next_available_codeword;
        }

        protected boolean is_next_free_entry (int address) {
            return address == next_available_codeword;
        }

        protected boolean dictionary_full () {
            return next_available_codeword == DICTFULL;
        }

        protected byte dictionary_entry_byte (int address) {
            return dictionary[address].byte_val;
        }

        protected int dictionary_entry_pointer (int address) {
            return dictionary[address].pointer;
        }

        protected boolean root_codeword (int codeword) {
            return  codeword < FIRSTCW;
        }

        //=======================================================================
        // Internal update methods
        //=======================================================================
        private void set_dictionary_entry_pointer (int address, int pointer) {
            dictionary[address].pointer = pointer;
        }

        private void set_dictionary_entry_byte (int address, byte byte_val) {
            dictionary[address].byte_val = byte_val;
        }
    }

    private static class DictEntry
    {
        protected int          pointer;
        protected byte         byte_val;
    }

    private static class IntRef {
        public int value;

        public IntRef() {
            value = 0;
        }

        public IntRef (int initial) {
            value = initial;
        }
    }

    private static class Packer {

        private final OutputStream op_file;

        // Barrel shift register used to formulate the output bytes
        private long barrel = 0;

        // The residue count of unflushed bits left on the barrel shifter
        private int residue = 0;

        //=======================================================================
        // Constructors
        //=======================================================================

        public Packer(OutputStream ofp) {
            op_file = ofp;
        }

        //=======================================================================
        // Method name: pack
        //
        // Description:
        //    This method packs valid LZW codewords into the appropriate
        //    sized packets (ie. 9 to 12 bits). The codeword length is passed
        //    in as a parameter, as this is managed by the dictionary.
        //=======================================================================

        protected int pack(int ip_codeword, int codeword_length) throws IOException {

            int byte_count = 0;

            // Append codeword to the bottom of the barrel shifter
            barrel |= ((ip_codeword & CODEWORDMASK) << residue);

            // If not the last (NULL) codeword, increment the number of bits on the
            // barrel shifter by the current codeword size
            if (ip_codeword != NULLCW)
                residue += codeword_length;

            // While there are sufficient bits, place bytes on the output.
            // Normally this is whilst there are whole bytes, but the last (NULL)
            // codeword causes a flush of ALL remaining bits
            while (residue >= ((ip_codeword != NULLCW) ? BYTESIZE : BITSIZE)) {
                putc((byte)(barrel & BYTEMASK), op_file);
                byte_count++;
                barrel >>= BYTESIZE;
                residue -= BYTESIZE;
            }

            if (ip_codeword == NULLCW)
                flush(op_file);

            // Return number of bytes output
            return byte_count;
        }
    }

    private static class Unpacker {

        private short ipbyte;
        private int currlen, barrel;
        private int op_codeword;

        private final InputStream ip_file;

        //=======================================================================
        // Constructors
        //=======================================================================

        public Unpacker(InputStream ifp) {
            currlen = 0;
            barrel= 0;
            op_codeword = 0;
            ip_file = ifp;
        }

        //=======================================================================
        // Method name: unpack
        //
        // Description:
        //    unpack() grabs bytes from input stream, placing then on a barrel
        //    shifter until it has enough bits for a codeword of the current
        //    codeword length (codeword_length).
        //=======================================================================

        protected int unpack(IntRef codeword, int codeword_length) throws IOException {

            int byte_count = 0;

            // Start inputing bytes to form a whole codeword
            do {
                // Gracefully fail if no more input bytes---codeword is
                // don't care.
                if ((ipbyte = getc(ip_file)) == -1)
                    return byte_count;

                // We successfully got a byte so increment the byte counter
                byte_count++;

                // Put the byte on the barrel shifter
                barrel |= (ipbyte & BYTEMASK) << currlen;

                // We have another byte's worth of bits
                currlen += BYTESIZE;

            // Continue until we have enough bits for a codeword.
            } while (currlen < codeword_length);

            // Codeword is bottom 'codeword_length' bits: I.e. mask=2^codeword_length - 1
            op_codeword = (barrel & ((0x1 << codeword_length) - 1));
            currlen -= codeword_length;
            barrel >>= codeword_length;

            // Return the codeword value in the pointer
            codeword.value = op_codeword;

            // Mark the operation as successful
            return byte_count;
        }
    }

    static class Decomp {

        // Configurable parameter
        private int max_string_length;

        // Algorithm variables
        private int                  previous_codeword = NULLCW;
        private int                  code_size         = MINCWLEN;
        private byte                 string_terminator_byte;

        private final OutputStream op_file;
        private IntRef ip_codeword       = new IntRef();
        private Stack<Byte> stack             = new Stack<>();

        //=======================================================================
        // Constructors
        //=======================================================================

        @SuppressWarnings("unused")
        public Decomp(OutputStream ofp) {
            this(MAXWORDLENGTH, ofp);
        }

        public Decomp(int maxstr, OutputStream ofp) {
            max_string_length = maxstr;
            op_file = ofp;
            stack.clear();
        }

        //========================================================================
        // Method name: decompress
        //
        // Description:
        //    Performs LZW decompression of codewords from
        //    standard input outputing decompressed data to standard
        //    output.
        //========================================================================

        public int decompress(Dict dict, Unpacker unpacker) throws IOException {

            IntRef status      = new IntRef(NOERROR);

            // Keep going until thare are no more codewords
            while ((unpacker.unpack(ip_codeword, code_size)) != 0) {

                if (dict.codeword_valid(ip_codeword.value)) {

                    // Traverse down the dictionary's linked list placing bytes onto
                    // the stack. Empty the stack when reached a NULLCW pointer and remember
                    // the last flushed  byte.
                    string_terminator_byte = output_linked_list(dict, status);

                    if (status.value != 0)
                        return status.value;

                    // We must build a dictionary entry using the last codeword fully
                    // processed and the first flushed byte of the present codeword (if we
                    // aren't flushed)
                    if (previous_codeword != NULLCW)
                        code_size = dict.build_entry(previous_codeword, string_terminator_byte);

                } else {  // No valid entry exists
                    throw new IOException(String.format("***decompress: Error --- UNKNOWN CODEWORD %08x\n", ip_codeword.value));
                }

                previous_codeword = ip_codeword.value;

            } // end while

            flush(op_file);

            return status.value;
        }

        //========================================================================
        // Method name: output_linked_list
        //
        // Description:
        //    Follows a linked list of dictionary entries and outputs
        //    the bytes in reverse order.
        //========================================================================

        private byte output_linked_list(Dict dict, IntRef errflag) throws IOException {

            int pointer;
            byte byte_val = 0;

            errflag.value = 0;

            pointer = ip_codeword.value;

            // While not at the end of the list, follow the linked list, placing
            // byte values on a stack
            while (pointer != NULLCW) {

                // If not a root codeword ...
                if (!dict.root_codeword(pointer)) {

                    // If an entry in the linked list is the next free codeword,
                    // then it must need building as a KwK case.
                    if (dict.is_next_free_entry(pointer) && (previous_codeword != NULLCW)) {

                        // The pointer and byte values are as for the KwK build;
                        // i.e. the last codeword that was input and its first
                        // character
                        byte_val = string_terminator_byte;
                        pointer = previous_codeword;

                    } else {

                        // Get the byte and pointer values from the entry.
                        byte_val = dict.dictionary_entry_byte(pointer);
                        pointer = dict.dictionary_entry_pointer(pointer);

                    }

                // We have to generate the entry for root codewords
                } else {
                    byte_val = (byte)pointer;
                    pointer = NULLCW;
                }

                // It is an error to have a codeword which overflows the stack
                if (stack.size() == max_string_length) {
                    throw new IOException("decompress: Error --- BAD WORD LENGTH");
    //                errflag.value = DECOMPRESSION_ERROR;
    //                return 0;
                }

                // Push the current byte value on the stack
                stack.push(byte_val);

            } // end while

            // Now flush the stack to the output stream
            do {
                Byte pop_val;

                pop_val = stack.pop();
                putc(pop_val.byteValue(), op_file);
            } while (!stack.empty());

            // Return the last flushed byte, for use in building dictionary entries
            return byte_val;
        }
    }

    static class Comp {

        private int op_bytecount;
        private final InputStream ip_file;

        //=======================================================================
        // Constructors
        //=======================================================================

        // Constructor with configuration parameters
        public Comp(InputStream ifp) {
            ip_file = ifp;
            op_bytecount = 0;
        }

        @SuppressWarnings("unused")
        public int getOp_bytecount() {
            return op_bytecount;
        }

        //========================================================================
        // Method name: compress
        //
        // Description:
        //    Performs LZW compression on input stream, outputing codewords
        //    (via the packer) to an output stream.
        //========================================================================

        public void compress(Dict dict, Packer packer) throws IOException {

            int previous_codeword = NULLCW;
            int code_size = dict.reset_dictionary();

            // Process bytes for the while length of the file.
            short ipbyte;
            while ((ipbyte = getc(ip_file)) != -1) {

                //output_graphics_data_point();

                // Increment the byte counter for each input
                //System.err.println("ip_bytecount="+ip_bytecount+ " ipbyte="+ipbyte);

                // First byte, so we need to go round the loop once more for
                // another byte, and find the root codeword representation for
                // this byte.
                if (previous_codeword == NULLCW) {

                    previous_codeword = convert_to_rootcw(ipbyte);

                    // We have an implied root codeword match i.e. match length = 1

                    // Otherwise, process the string as normal
                } else {

                    // Match found
                    int match_addr;
                    if ((match_addr = dict.entry_match(previous_codeword, (byte) ipbyte)) != NOMATCH) {

                        // A match increases our string length representation by
                        // one. This is used simply to check that we can fit on
                        // the stack at decompression (shouldn't reach this
                        // limit).

                        // Previous matched codeword becomes codeword value of dictionary
                        // entry we've just matched
                        previous_codeword = match_addr;

                    // Match not found
                    } else { // entry_match(addr) is TRUE

                        // Output the last matched codeword
                        op_bytecount += packer.pack(previous_codeword, code_size);

                        // Build an entry for the new string (if possible)
                        code_size = dict.build_entry(previous_codeword, (byte) ipbyte);

                        // Carry forward the input byte as a 'matched' root codeword
                        previous_codeword = convert_to_rootcw(ipbyte);

                        // Now we have just a single root codeword match, yet to be processed

                    }

                } // endelse (previous_codeword == NULLCW)

            } // end while

            // If we've terminated and still have a codeword to output,
            // then we have to output the codeword which represents all the
            // matched  string so far (and it could be just a root codeword).
            if (previous_codeword != NULLCW) {
                op_bytecount += packer.pack(previous_codeword, code_size);

                // Pipeline flushed, so no previous codeword
                //noinspection UnusedAssignment
                previous_codeword = NULLCW;
            }

            // We let the packer know we've finished and thus to flush its pipeline
            op_bytecount += packer.pack(EOFFLUSH, code_size);

        } // end compress()

        //=======================================================================
        // Internal access functions
        //=======================================================================

        private int convert_to_rootcw(short byte_val) {
            return (int)byte_val;
        }

    }

    public static class Codec {

        // IO configuration
    //    private String ip_filename;
    //    private String op_filename;
    //    private BufferedOutputStream ofp;
    //    private BufferedInputStream  ifp;

        private final int config_max_str_len;

        //=======================================================================
        // Constructor
        //
        // Only a default constructor, as all the configuration is done from
        // the user command line options in methods set_user_config()
        //=======================================================================

        public Codec() {
            this(MAXWORDLENGTH);
        }

        public Codec(int maxStrLen) {
            config_max_str_len = maxStrLen;
        }

        //=======================================================================
        // Configure and run the codec
        //=======================================================================
        public void compress(InputStream ifp, OutputStream ofp) throws IOException {
            run(true, ifp, ofp);
        }

        public void decompress(InputStream ifp, OutputStream ofp) throws IOException {
            run(false, ifp, ofp);
        }

        private void run (boolean compress_mode, InputStream ifp, OutputStream ofp) throws IOException {

            int status = NOERROR;

            // Create a dictionary (inform whether compressing or decompressing---
            // dictionary is used as CAM in compression, SRAM in decompression)
            Dict dict = new Dict(compress_mode);

            // Select compression/decompression routines as specified
            if (compress_mode) {

                // Create a packer (arguments configure formatter)
                Packer packer = new Packer(ofp);

                // Create a compression encoder
                Comp comp = new Comp(ifp);

                // Connect dictionary and packer, and start compressing from input stream
                comp.compress(dict, packer);

            } else {
                // Create an unpacker (arguments configure formatter)
                Unpacker unpacker = new Unpacker(ifp);

                // Create a decompression decoder
                Decomp decomp = new Decomp(config_max_str_len, ofp);

                // Connect dictionary and unpacker, and start decompressing from input stream
                status = decomp.decompress(dict, unpacker);

            }
            if (status != NOERROR) {
                throw new LzwException("status " + status);
            }
        }

        @SuppressWarnings("unused")
        static class LzwException extends IOException {
            public LzwException(Throwable cause) {
                super(cause);
            }

            public LzwException(String message) {
                super(message);
            }

            public LzwException(String message, Throwable cause) {
                super(message, cause);
            }
        }

    }
}
