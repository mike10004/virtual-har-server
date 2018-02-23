//=======================================================================
//                                                                        
// Decomp.java                                           date: 2010/04/01 
//                                                                        
// Author: Simon Southwell                                                
//                                                                        
// Copyright (c) 2010 Simon Southwell                                                                     
//                                                                       
// This file is part of Lzw.
//
// Lzw is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// Lzw is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with Lzw. If not, see <http://www.gnu.org/licenses/>.
//
// $Id: Decomp.java,v 1.3 2010-07-31 06:08:48 simon Exp $
// $Source: /home/simon/CVS/src/java/Lzw/codec/Decomp.java,v $
//                                                                      
//=======================================================================

//=======================================================================
//  The Decomp class takes a stream of valid LZW codewords (through  
//  calls to the unpacker) and formulates a dictionary as it goes. The  
//  output is a byte sequence constructed  from following a linked list  
//  of entries starting with the entry for the input codeword until  
//  reaching a root  codeword (pointing to NULL).The bytes are pushed  
//  onto a stack as the list is followed, and then flushed upon list  
//  termination. An exception condition  exists (called the K omega K  
//  case, as originally  described by Welch) where a codeword is input  
//  that has a yet to be constructed entry in the linked list it points
//  to. The code will reconstruct the entry before proceeding down the
//  rest of the list.
//=======================================================================

package io.github.mike10004.vhs.lzw;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Stack;

public class Decomp {

    // Configurable parameter
    private int max_string_length;

    // Algorithm variables 
    private int                  previous_codeword = Lz.NULLCW;
    private int                  code_size         = Lz.MINCWLEN;
    private byte                 string_terminator_byte;

    private final OutputStream op_file;
    private IntRef               ip_codeword       = new IntRef();
    private Stack<Byte>          stack             = new Stack<>();

    //=======================================================================
    // Constructors
    //=======================================================================

    @SuppressWarnings("unused")
    public Decomp(OutputStream ofp) {
        this(Lz.MAXWORDLENGTH, ofp);
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

        IntRef status      = new IntRef(Lz.NOERROR);

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
                if (previous_codeword != Lz.NULLCW)
                    code_size = dict.build_entry(previous_codeword, string_terminator_byte);

            } else {  // No valid entry exists
                throw new IOException(String.format("***decompress: Error --- UNKNOWN CODEWORD %08x\n", ip_codeword.value));
            }

            previous_codeword = ip_codeword.value;

        } // end while 

        Lz.flush(op_file);

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
        while (pointer != Lz.NULLCW) {

            // If not a root codeword ... 
            if (!dict.root_codeword(pointer)) {

                // If an entry in the linked list is the next free codeword,
                // then it must need building as a KwK case. 
                if (dict.is_next_free_entry(pointer) && (previous_codeword != Lz.NULLCW)) {

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
                pointer = Lz.NULLCW;
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
            Lz.putc(pop_val.byteValue(), op_file);
        } while (!stack.empty());

        // Return the last flushed byte, for use in building dictionary entries
        return byte_val;
    }
}
