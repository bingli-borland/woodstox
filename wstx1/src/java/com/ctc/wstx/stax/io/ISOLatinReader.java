package com.ctc.wstx.stax.io;

import java.io.*;

/**
 * Optimized Reader that reads ISO-Latin (aka ISO-8859-1) content from an
 * input stream.
 * In addition to doing (hopefully) optimal conversion, it can also take
 * array of "pre-read" (leftover) bytes; this is necessary when preliminary
 * stream/reader is trying to figure out XML encoding.
 */
public final class ISOLatinReader
    extends BaseReader
{
    /*
    ////////////////////////////////////////
    // Life-cycle
    ////////////////////////////////////////
    */

    public ISOLatinReader(InputStream in, byte[] buf, int ptr, int len)
    {
        super(in, buf, ptr, len);
    }

    /*
    ////////////////////////////////////////
    // Public API
    ////////////////////////////////////////
    */

    public int read(char[] cbuf, int start, int len)
        throws IOException
    {
        // Already EOF?
        if (mBuffer == null) {
            return -1;
        }
        // Let's then ensure there's enough room...
        if (start < 0 || (start+len) > cbuf.length) {
            reportBounds(cbuf, start, len);
        }

        // Need to load more data?
        int avail = mLength - mPtr;
        if (avail <= 0) {
            // Let's always read full buffers, actually...
            int count = mIn.read(mBuffer);
            if (count <= 0) {
                if (count == 0) {
                    reportStrangeStream();
                }
                /* Let's actually then free the buffer right away; shouldn't
                 * yet close the underlying stream though?
                 */
                mBuffer = null;
                return -1;
            }
            mLength = avail = count;
            mPtr = 0;
        }

        // K, have at least one byte == char, good enough:

        if (len > avail) {
            len = avail;
        }
        int i = mPtr;
        int last = i + len;

        for (; i < last; ) {
            cbuf[start++] = (char) (mBuffer[i++] & 0xFF);
        }

        mPtr = last;
        return len;
    }

    /*
    ////////////////////////////////////////
    // Internal methods
    ////////////////////////////////////////
    */
}

