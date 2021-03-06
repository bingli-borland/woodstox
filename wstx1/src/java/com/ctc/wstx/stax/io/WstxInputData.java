package com.ctc.wstx.stax.io;

/**
 * Base class used by readers (specifically,
 * {@link com.ctc.wstx.stax.WstxStreamReader}),
 * to encapsulate input buffer portion of the class. Philosophically
 * this should probably be used via containment (composition), but for
 * performance reason, this "core" class is generally extended instead.
 *<p>
 * Main reason for the input data portion to be factored out of main
 * class is that this way it can also be passed to nested input handling
 * Objects, which can then manipulate input buffers of the caller,
 * efficiently.
 */
public class WstxInputData
{
    // // // Some well-known chars:

    /**
     * Null-character is used as return value from some method(s), since
     * it is not a legal character in an XML document.
     */
    public final static char CHAR_NULL = '\u0000';

    public final static char CHAR_SPACE = (char) 0x0020;

    /*
    ////////////////////////////////////////////////////
    // Current input data
    ////////////////////////////////////////////////////
     */

    /**
     * Current buffer from which data is read; generally data is read into
     * buffer from input source, but not always (especially when using nested
     * input contexts when expanding parsed entity references etc).
     */
    protected char[] mInputBuffer;

    /**
     * Pointer to next available character in buffer
     */
    protected int mInputPtr = 0;

    /**
     * Index of character after last available one in the buffer.
     */
    protected int mInputLen = 0;

    /*
    ////////////////////////////////////////////////////
    // Current input location information
    ////////////////////////////////////////////////////
     */

    /**
     * Current number of characters that were processed in previous blocks,
     * before contents of current input buffer.
     */
    protected int mCurrInputProcessed = 0;

    /**
     * Current row location of current point in input buffer, starting
     * from 1
     */
    protected int mCurrInputRow = 1;

    /**
     * Current index of the first character of the current row in input
     * buffer. Needed to calculate column position, if necessary; benefit
     * of not having column itself is that this only has to be updated
     * once per line.
     */
    protected int mCurrInputRowStart = 0;

    /*
    ////////////////////////////////////////////////////
    // Life-cycle
    ////////////////////////////////////////////////////
     */

    protected WstxInputData() {
    }

    /*
    ////////////////////////////////////////////////////
    // Public/package API
    ////////////////////////////////////////////////////
     */

    /**
     * Note: Only public due to sub-classes needing to call this on
     * base class instance from different package (confusing?)
     */
    public void copyBufferStateFrom(WstxInputData src)
    {
        mInputBuffer = src.mInputBuffer;
        mInputPtr = src.mInputPtr;
        mInputLen = src.mInputLen;

        mCurrInputProcessed = src.mCurrInputProcessed;
        mCurrInputRow = src.mCurrInputRow;
        mCurrInputRowStart = src.mCurrInputRowStart;
    }
}

