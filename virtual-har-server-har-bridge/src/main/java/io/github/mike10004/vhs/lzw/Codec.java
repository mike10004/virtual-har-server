//=======================================================================
//                                                                       
// Codec.java                                            date: 2010/04/01  
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
// $Id: Codec.java,v 1.2 2010-07-31 06:08:48 simon Exp $
// $Source: /home/simon/CVS/src/java/Lzw/codec/Codec.java,v $
//
//=======================================================================

//=======================================================================
// This class implements the LZW algorithm. It has a dictionary space of 
// 4K---See Dict.java.                       
//=======================================================================

package io.github.mike10004.vhs.lzw;

import java.io.*;

public class Codec extends Lz {

    // References to codec objects
    private Dict     dict;
    private Packer   packer;
    private Comp     comp; 
    private Decomp   decomp; 
    private Unpacker unpacker; 

    // The following variables are initialised to the equivalent of
    // a hardware reset. 
    private int previous_codeword;

    // IO configuration 
//    private String ip_filename;
//    private String op_filename;
//    private BufferedOutputStream ofp;
//    private BufferedInputStream  ifp;

    private int config_max_str_len;

    //=======================================================================
    // Constructor
    //
    // Only a default constructor, as all the configuration is done from
    // the user command line options in methods set_user_config()
    //=======================================================================

    public Codec() {
        previous_codeword = NULLCW;
        config_max_str_len = MAXWORDLENGTH;
    }

    //=======================================================================
    // Configure and run the codec                                                         
    //=======================================================================
    public int compress(BufferedInputStream ifp, BufferedOutputStream ofp) throws IOException {
        return run(true, ifp, ofp);
    }

    public int decompress(BufferedInputStream ifp, BufferedOutputStream ofp) throws IOException {
        return run(false, ifp, ofp);
    }

    private int run (boolean compress_mode, BufferedInputStream ifp, BufferedOutputStream ofp) throws IOException {

        int status = NOERROR;
    
        // Create a dictionary (inform whether compressing or decompressing---
        // dictionary is used as CAM in compression, SRAM in decompression)
        dict = new Dict(compress_mode);

        // Select compression/decompression routines as specified 
        if (compress_mode) {

            // Create a packer (arguments configure formatter)
            packer = new Packer(compress_mode, ofp);

            // Create a compression encoder
            comp = new Comp(config_max_str_len, ifp);

            // Connect dictionary and packer, and start compressing from input stream
            comp.compress(dict, packer);

        } else {
            // Create an unpacker (arguments configure formatter)
            unpacker = new Unpacker(compress_mode, ifp);

            // Create a decompression decoder
            decomp = new Decomp(config_max_str_len, ofp);

            // Connect dictionary and unpacker, and start decompressing from input stream
            status = decomp.decompress(dict, unpacker);

        }

        return status;
    }

}
