//******************************************************************************
//
// File:    LongMatrixBuf.java
// Package: edu.rit.mp.buf
// Unit:    Class edu.rit.mp.buf.LongMatrixBuf
//
// This Java source file is copyright (C) 2009 by Alan Kaminsky. All rights
// reserved. For further information, contact the author, Alan Kaminsky, at
// ark@cs.rit.edu.
//
// This Java source file is part of the Parallel Java Library ("PJ"). PJ is free
// software; you can redistribute it and/or modify it under the terms of the GNU
// General Public License as published by the Free Software Foundation; either
// version 3 of the License, or (at your option) any later version.
//
// PJ is distributed in the hope that it will be useful, but WITHOUT ANY
// WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
// A PARTICULAR PURPOSE. See the GNU General Public License for more details.
//
// A copy of the GNU General Public License is provided in the file gpl.txt. You
// may also obtain a copy of the GNU General Public License on the World Wide
// Web at http://www.gnu.org/licenses/gpl.html.
//
//******************************************************************************
package edu.rit.mp.buf;

import edu.rit.mp.Buf;
import edu.rit.mp.LongBuf;

import edu.rit.pj.reduction.LongOp;
import edu.rit.pj.reduction.Op;

import edu.rit.util.Arrays;
import edu.rit.util.Range;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

/**
 * Class LongMatrixBuf provides a buffer for a matrix of long items sent or
 * received using the Message Protocol (MP). The matrix row and column strides
 * may be 1 or greater than 1. While an instance of class LongMatrixBuf may be
 * constructed directly, normally you will use a factory method in class
 * {@linkplain edu.rit.mp.LongBuf LongBuf}. See that class for further
 * information.
 *
 * @author Alan Kaminsky
 * @version 05-Apr-2009
 */
public class LongMatrixBuf
        extends LongBuf {

// Hidden data members.
    long[][] myMatrix;
    Range myRowRange;
    Range myColRange;
    int myLowerRow;
    int myRowCount;
    int myRowStride;
    int myLowerCol;
    int myColCount;
    int myColStride;

// Exported constructors.
    /**
     * Construct a new long matrix buffer. It is assumed that the rows and
     * columns of <TT>theMatrix</TT> are allocated and that each row of
     * <TT>theMatrix</TT> has the same number of columns.
     *
     * @param theMatrix Matrix.
     * @param theRowRange Range of rows to include.
     * @param theColRange Range of columns to include.
     */
    public LongMatrixBuf(long[][] theMatrix,
            Range theRowRange,
            Range theColRange) {
        super(theRowRange.length() * theColRange.length());
        myMatrix = theMatrix;
        myRowRange = theRowRange;
        myColRange = theColRange;
        myLowerRow = theRowRange.lb();
        myRowCount = theRowRange.length();
        myRowStride = theRowRange.stride();
        myLowerCol = theColRange.lb();
        myColCount = theColRange.length();
        myColStride = theColRange.stride();
    }

// Exported operations.
    /**
     * {@inheritDoc}
     *
     * Obtain the given item from this buffer.
     * <P>
     * The <TT>get()</TT> method must not block the calling thread; if it does,
     * all message I/O in MP will be blocked.
     */
    public long get(int i) {
        return myMatrix[i2r(i) * myRowStride + myLowerRow][i2c(i) * myColStride + myLowerCol];
    }

    /**
     * {@inheritDoc}
     *
     * Store the given item in this buffer.
     * <P>
     * The <TT>put()</TT> method must not block the calling thread; if it does,
     * all message I/O in MP will be blocked.
     */
    public void put(int i,
            long item) {
        myMatrix[i2r(i) * myRowStride + myLowerRow][i2c(i) * myColStride + myLowerCol] = item;
    }

    /**
     * {@inheritDoc}
     *
     * Copy items from the given buffer to this buffer. The number of items
     * copied is this buffer's length or <TT>theSrc</TT>'s length, whichever is
     * smaller. If <TT>theSrc</TT> is this buffer, the <TT>copy()</TT> method
     * does nothing.
     * @exception ClassCastException (unchecked exception) Thrown if
     * <TT>theSrc</TT>'s item data type is not the same as this buffer's item
     * data type.
     */
    public void copy(Buf theSrc) {
        if (theSrc == this) {
        } else if (theSrc instanceof LongMatrixBuf) {
            LongMatrixBuf src = (LongMatrixBuf) theSrc;
            Arrays.copy(src.myMatrix, src.myRowRange, src.myColRange,
                    this.myMatrix, this.myRowRange, this.myColRange);
        } else {
            LongBuf.defaultCopy((LongBuf) theSrc, this);
        }
    }

    /**
     * {@inheritDoc}
     *
     * Create a buffer for performing parallel reduction using the given binary
     * operation. The results of the reduction are placed into this buffer.
     * @exception ClassCastException (unchecked exception) Thrown if this
     * buffer's element data type and the given binary operation's argument data
     * type are not the same.
     */
    public Buf getReductionBuf(Op op) {
        return new LongMatrixReductionBuf(myMatrix, myRowRange, myColRange, (LongOp) op);
    }

// Hidden operations.
    /**
     * {@inheritDoc}
     *
     * Send as many items as possible from this buffer to the given byte buffer.
     * <P>
     * The <TT>sendItems()</TT> method must not block the calling thread; if it
     * does, all message I/O in MP will be blocked.
     */
    protected int sendItems(int i,
            ByteBuffer buffer) {
        LongBuffer longbuffer = buffer.asLongBuffer();
        int n = 0;
        int r = i2r(i);
        int row = r * myRowStride + myLowerRow;
        int c = i2c(i);
        int col = c * myColStride + myLowerCol;
        int ncols = Math.min(myColCount - c, longbuffer.remaining());
        while (r < myRowCount && ncols > 0) {
            long[] myMatrix_row = myMatrix[row];
            while (c < ncols) {
                longbuffer.put(myMatrix_row[col]);
                ++c;
                col += myColStride;
            }
            n += ncols;
            ++r;
            row += myRowStride;
            c = 0;
            col = myLowerCol;
            ncols = Math.min(myColCount, longbuffer.remaining());
        }
        buffer.position(buffer.position() + 8 * n);
        return n;
    }

    /**
     * {@inheritDoc}
     *
     * Receive as many items as possible from the given byte buffer to this
     * buffer.
     * <P>
     * The <TT>receiveItems()</TT> method must not block the calling thread; if
     * it does, all message I/O in MP will be blocked.
     */
    protected int receiveItems(int i,
            int num,
            ByteBuffer buffer) {
        LongBuffer longbuffer = buffer.asLongBuffer();
        num = Math.min(num, longbuffer.remaining());
        int n = 0;
        int r = i2r(i);
        int row = r * myRowStride + myLowerRow;
        int c = i2c(i);
        int col = c * myColStride + myLowerCol;
        int ncols = Math.min(myColCount - c, num);
        while (r < myRowCount && ncols > 0) {
            long[] myMatrix_row = myMatrix[row];
            for (c = 0; c < ncols; ++c) {
                myMatrix_row[col] = longbuffer.get();
                col += myColStride;
            }
            num -= ncols;
            n += ncols;
            ++r;
            row += myRowStride;
            col = myLowerCol;
            ncols = Math.min(myColCount, num);
        }
        buffer.position(buffer.position() + 8 * n);
        return n;
    }

    /**
     * Convert the given buffer index to a row index.
     */
    int i2r(int i) {
        return myColCount == 0 ? i : i / myColCount;
    }

    /**
     * Convert the given buffer index to a column index.
     */
    int i2c(int i) {
        return myColCount == 0 ? 0 : i % myColCount;
    }

}
