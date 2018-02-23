//=======================================================================
//                                                                       
// Lz.java                                               date: 2010/04/01  
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
// $Id: Lz.java,v 1.1 2010-04-04 10:44:26 simon Exp $
// $Source: /home/simon/CVS/src/java/Lzw/codec/Lz.java,v $
//                                                                      
//=======================================================================

package io.github.mike10004.vhs.lzw;
                  
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

public class Lz implements LzConsts {

    //=======================================================================
    // Helper IO methods to hide the try/catch awkwardness
    //=======================================================================

    // Get a byte from a buffered input
    public short getc(InputStream ip) throws IOException {
        int rbyte;
        rbyte = ip.read();
        return (short)rbyte;
    }

    // Write a byte to a buffered input
    public void putc(byte val, OutputStream op) throws IOException {
        op.write(val);
    }

    public void flush(OutputStream op) throws IOException {
        op.flush();
    }
}
