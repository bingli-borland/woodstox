package com.ctc.wstx.util;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;

/**
 * TextBuffer is a class similar to {@link StringBuffer}, with
 * following differences:
 *<ul>
 *  <li>TextBuffer uses segments character arrays, to avoid having
 *     to do additional array copies when array is not big enough. This
 *     means that only reallocating that is necessary is done only once --
 *     if and when caller
 *     wants to access contents in a linear array (char[], String).
 *    </li>
 *  <li>TextBuffer is not synchronized.
 *    </li>
 * </ul>
 *<p>
 * Notes about usage: for debugging purposes, it's suggested to use 
 * {@link #toString} method, as opposed to
 * {@link #contentsAsArray} or {@link #contentsAsString}. Internally
 * resulting code paths may or may not be different, WRT caching.
 */
public final class TextBuffer
{
    final static int DEF_INITIAL_BUFFER_SIZE = 4000; // 8k

    // // // Shared read-only input buffer:

    /**
     * Shared input buffer; stored here in case some input can be returned
     * as is, without being copied to collector's own buffers. Note that
     * this is read-only for this Objet.
     */
    private char[] mInputBuffer;

    /**
     * Character offset of first char in input buffer; -1 to indicate
     * that input buffer currently does not contain any useful char data
     */
    private int mInputStart;

    private int mInputLen;

    // // // Internal collector buffers:

    /**
     * Initial allocation size to use, if/when temporary output buffer
     * is needed.
     */
    private final int mInitialBufSize;

    /**
     * List of segments prior to currently active segment.
     */
    private ArrayList mSegments;

    /**
     * Amount of characters in segments in {@link mSegments}
     */
    private int mSegmentSize;

    private char[] mCurrentSegment;

    /**
     * Number of characters in currently active (last) segment
     */
    private int mCurrentSize;

    // // // Temporary caching for Objects to return

    /**
     * String that will be constructed when the whole contents are
     * needed; will be temporarily stored in case asked for again.
     */
    private String mResultString;

    private char[] mResultArray;

    /*
    //////////////////////////////////////////////
    // Life-cycle
    //////////////////////////////////////////////
     */

    public TextBuffer()
    {
        this(DEF_INITIAL_BUFFER_SIZE);
    }

    public TextBuffer(int initialSize)
    {
        mInitialBufSize = initialSize;
    }

    /**
     * Method that can be called to clear all data; makes sure that all
     * referenced Objects can be GC'ed.
     */
    public void clear() {
        // Temp results:
        mResultString = null;
        mResultArray = null;

        // Shared buffer stuff
        mInputBuffer = null;
        mInputStart = -1;
        mInputLen = 0;

        // Internal segments:
        mSegments = null;
        mSegmentSize = 0;
        mCurrentSegment = null;
        mCurrentSize = 0;
    }

    /**
     * Method called to clear out any content text buffer may have, and
     * initializes buffer to use non-shared data.
     */
    public void resetWithEmpty()
    {
        mInputBuffer = null;
        mInputStart = -1; // indicates shared buffer not used
        mInputLen = 0;

        mResultString = null;
        mResultArray = null;

        // And then reset internal input buffers, if necessary:
        if (mSegments != null && mSegments.size() > 0) {
            /* Let's start using _last_ segment from list; for one, it's
             * the biggest one, and it's also most likely to be cached
             */
            mCurrentSegment = (char[]) mSegments.get(mSegments.size() - 1);
            mSegments.clear();
            mSegmentSize = 0;
        }
        mCurrentSize = 0;
    }

    /**
     * Method called to initialize the buffer with a shared copy of data;
     * this means that buffer will just have pointers to actual data. It
     * also means that if anything is to be appended to the buffer, it
     * will first have to unshare it (make a local copy).
     */
    public void resetWithShared(char[] buf, int start, int len)
    {
        // Let's for mark things we need about input buffer
        mInputBuffer = buf;
        mInputStart = start;
        mInputLen = len;

        // Then clear intermediate values, if any:
        mResultString = null;
        mResultArray = null;

        // And then reset internal input buffers, if necessary:
        if (mSegments != null && mSegments.size() > 0) {
            /* Let's start using _last_ segment from list; for one, it's
             * the biggest one, and it's also most likely to be cached
             */
            mCurrentSegment = (char[]) mSegments.get(mSegments.size() - 1);
            mSegments.clear();
            mCurrentSize = mSegmentSize = 0;
        }
    }

    public void resetWithCopy(char[] buf, int start, int len)
    {
        mInputBuffer = null;
        mInputStart = -1; // indicates shared buffer not used
        mInputLen = 0;

        mResultString = null;
        mResultArray = null;

        // And then reset internal input buffers, if necessary:
        if (mSegments != null && mSegments.size() > 0) {
            /* Let's start using _last_ segment from list; for one, it's
             * the biggest one, and it's also most likely to be cached
             */
            mCurrentSegment = (char[]) mSegments.get(mSegments.size() - 1);
            mSegments.clear();
        } else {
            if (mCurrentSegment == null) {
                mCurrentSegment = new char[mInitialBufSize];
            }
        }
        mCurrentSize = mSegmentSize = 0;
        append(buf, start, len);
    }

  /**
   * Method called to make sure there is a non-shared segment to use, without
   * appending any content yet.
   */
    public void resetInitialized()
    {
      resetWithEmpty();
      if (mCurrentSegment == null) {
          mCurrentSegment = new char[mInitialBufSize];
      }
    }

    /*
    //////////////////////////////////////////////
    // Accessors for implementing StAX interface:
    //////////////////////////////////////////////
     */

    /**
     * @return Number of characters currently stored by this collector
     */
    public int size() {
        if (mInputStart >= 0) { // shared copy from input buf
            return mInputLen;
        }
        // local segmented buffers
        return mSegmentSize + mCurrentSize;
    }

    public int getTextStart()
    {
        /* Only shared input buffer can have non-zero offset; buffer
         * segments start at 0, and if we have to create a combo buffer,
         * that too will start from beginning of the buffer
         */
        return (mInputStart >= 0) ? mInputStart : 0;
    }

    public char[] getTextBuffer()
    {
        // Are we just using shared input buffer?
        if (mInputStart >= 0) {
            return mInputBuffer;
        }
        // Nope; but does it fit in just one segment?
        if (mSegments == null || mSegments.size() == 0) {
            return mCurrentSegment;
        }
        // Nope, need to have/create a non-segmented array and return it
        return contentsAsArray();
    }

    /*
    //////////////////////////////////////////////
    // Accessors:
    //////////////////////////////////////////////
     */

    public String contentsAsString()
    {
        if (mResultString == null) {
            // Has array been requested? Can make a shortcut, if so:
            if (mResultArray != null) {
                mResultString = new String(mResultArray);
            } else {
                // Do we use shared array?
                if (mInputStart >= 0) {
                    if (mInputLen < 1) {
                        return (mResultString = "");
                    }
                    mResultString = new String(mInputBuffer, mInputStart, mInputLen);
                } else { // nope 
                    int size = size();
                    if (size < 1) {
                        return (mResultString = "");
                    }
                    StringBuffer sb = new StringBuffer(size);
                    // First stored segments
                    if (mSegments != null) {
                        for (int i = 0, len = mSegments.size(); i < len; ++i) {
                            char[] curr = (char[]) mSegments.get(i);
                            sb.append(curr, 0, curr.length);
                        }
                    }
                    // And finally, current segment:
                    sb.append(mCurrentSegment, 0, mCurrentSize);
                    mResultString = sb.toString();
                }
            }
        }
        return mResultString;
    }
 
    public char[] contentsAsArray()
    {
        char[] result = mResultArray;
        if (result == null) {
            mResultArray = result = buildResultArray();
        }
        return result;
    }

    public int contentsToArray(int srcStart, char[] dst, int dstStart, int len) {
        // Easy to copy from shared buffer:
        if (mInputStart >= 0) {
            int amount = mInputLen - srcStart;
            if (amount > len) {
                amount = len;
            } else if (amount < 0) {
                amount = 0;
            }
            if (amount > 0) {
                System.arraycopy(mInputBuffer, mInputStart+srcStart,
                                 dst, dstStart, amount);
            }
            return amount;
        }

        /* Could also check if we have array, but that'd only help with
         * braindead clients that get full array first, then segments...
         * which hopefully aren't that common
         */

        // Copying from segmented array is bit more involved:
        int totalAmount = 0;
        if (mSegments != null) {
            for (int i = 0, segc = mSegments.size(); i < segc; ++i) {
                char[] segment = (char[]) mSegments.get(i);
                int segLen = segment.length;
                int amount = segLen - srcStart;
                if (amount < 1) { // nothing from this segment?
                    srcStart -= segLen;
                    continue;
                }
                if (amount >= len) { // can get rest from this segment?
                    System.arraycopy(segment, srcStart, dst, dstStart, len);
                    return (totalAmount + len);
                }
                // Can get some from this segment, offset becomes zero:
                System.arraycopy(segment, srcStart, dst, dstStart, amount);
                totalAmount += amount;
                dstStart += amount;
                len -= amount;
                srcStart = 0;
            }
        }

        // Need to copy anything from last segment?
        if (len > 0) {
            int amount = mSegmentSize - srcStart;
            if (len > amount) {
                len = amount;
            }
            if (amount > 0) {
                System.arraycopy(mCurrentSegment, srcStart, dst, dstStart, amount);
                totalAmount += amount;
            }
        }

        return totalAmount;
    }

    public void rawContentsTo(Writer w)
        throws IOException
    {
        // Let's first see if we have created helper objects:
        if (mResultArray != null) {
            w.write(mResultArray);
            return;
        }
        if (mResultString != null) {
            w.write(mResultString);
            return;
        }

        // Do we use shared array?
        if (mInputStart >= 0) {
            if (mInputLen > 0) {
                w.write(mInputBuffer, mInputStart, mInputLen);
            }
            return;
        }
        // Nope, need to do full segmented output
        if (mSegments != null) {
            for (int i = 0, len = mSegments.size(); i < len; ++i) {
                w.write((char[]) mSegments.get(i));
            }
        }
        if (mCurrentSize > 0) {
            w.write(mCurrentSegment, 0, mCurrentSize);
        }
    }

    public boolean isAllWhitespace()
    {
        if (mInputStart >= 0) { // using single shared buffer?
            char[] buf = mInputBuffer;
            int i = mInputStart;
            int last = i + mInputLen;
            for (; i < last; ++i) {
                if (buf[i] > 0x0020) {
                    return false;
                }
            }
            return true;
        }

        // Nope, need to do full segmented output
        if (mSegments != null) {
            for (int i = 0, len = mSegments.size(); i < len; ++i) {
                char[] buf = (char[]) mSegments.get(i);
                for (int j = 0, len2 = buf.length; j < len2; ++j) {
                    if (buf[j] > 0x0020) {
                        return false;
                    }
                }
            }
        }
        
        char[] buf = mCurrentSegment;
        for (int i = 0, len = mCurrentSize; i < len; ++i) {
            if (buf[i] > 0x0020) {
                return false;
            }
        }
        return true;
    }

    /**
     * Method that can be used to check if the contents of the buffer end
     * in specified String.
     *
     * @return True if the textual content buffer contains ends with the
     *   specified String; false otherwise
     */
    public boolean endsWith(String str)
    {
        /* Let's just play this safe; should seldom if ever happen...
         * and because of that, can be sub-optimal, performancewise, to
         * alternatives.
         */
        if (mInputStart >= 0) {
            unshare(16);
        }

        int segIndex = (mSegments == null) ? 0 : mSegments.size();
        int inIndex = str.length() - 1;
        char[] buf = mCurrentSegment;
        int bufIndex = mCurrentSize-1;

        while (inIndex >= 0) {
            if (str.charAt(inIndex) != buf[bufIndex]) {
                return false;
            }
            if (--inIndex == 0) {
                break;
            }
            if (--bufIndex < 0) {
                if (--segIndex < 0) { // no more data?
                    return false;
                }
                buf = (char[]) mSegments.get(segIndex);
                bufIndex = buf.length-1;
            }
        }

        return true;
    }

    /*
    //////////////////////////////////////////////
    // Public mutators:
    //////////////////////////////////////////////
     */

    /**
     * Method called to make sure that buffer is not using shared input
     * buffer; if it is, it will copy such contents to private buffer.
     */
    public void ensureNotShared() {
        if (mInputStart >= 0) {
            unshare(16);
        }
    }

    public void append(char c) {
        // Using shared buffer so far?
        if (mInputStart >= 0) {
            unshare(16);
        }
        mResultString = null;
        mResultArray = null;
        // Room in current segment?
        char[] curr = mCurrentSegment;
        if (mCurrentSize >= curr.length) {
            expand(1);
        }
        curr[mCurrentSize++] = c;
    }

    public void append(char[] c, int start, int len)
    {
        // Can't append to shared buf (sanity check)
        if (mInputStart >= 0) {
            unshare(len);
        }
        mResultString = null;
        mResultArray = null;

        // Room in current segment?
        char[] curr = mCurrentSegment;
        int max = curr.length - mCurrentSize;
            
        if (max >= len) {
            System.arraycopy(c, start, curr, mCurrentSize, len);
            mCurrentSize += len;
        } else {
            // No room for all, need to copy part(s):
            if (max > 0) {
                System.arraycopy(c, start, curr, mCurrentSize, max);
                start += max;
                len -= max;
            }
            /* And then allocate new segment; we are guaranteed to now
             * have enough room in segment.
             */
            expand(len); // note: curr != mCurrentSegment after this
            System.arraycopy(c, start, mCurrentSegment, 0, len);
            mCurrentSize = len;
        }
    }

    public void append(String str)
    {
        // Can't append to shared buf (sanity check)
        int len = str.length();
        if (mInputStart >= 0) {
            unshare(len);
        }
        mResultString = null;
        mResultArray = null;

        // Room in current segment?
        char[] curr = mCurrentSegment;
        int max = curr.length - mCurrentSize;
        if (max >= len) {
            str.getChars(0, len, curr, mCurrentSize);
            mCurrentSize += len;
        } else {
            // No room for all, need to copy part(s):
            if (max > 0) {
                str.getChars(0, max, curr, mCurrentSize);
                len -= max;
            }
            /* And then allocate new segment; we are guaranteed to now
             * have enough room in segment.
             */
            expand(len);
            str.getChars(max, len, mCurrentSegment, 0);
            mCurrentSize = len;
        }
    }

    /*
    //////////////////////////////////////////////
    // Raw access, for high-performance use:
    //////////////////////////////////////////////
     */

    public char[] getCurrentSegment()
    {
        /* Since the intention of the caller is to directly add stuff into
         * buffers, we should NOT have anything in shared buffer... ie. may
         * need to unshare contents.
         */
        if (mInputStart >= 0) {
            unshare(1);
        } else if (mCurrentSize >= mCurrentSegment.length) {
            /* Plus, we better have room for at least one more char
             */
            expand(1);
        }
        return mCurrentSegment;
    }

    public int getCurrentSegmentSize() {
        return mCurrentSize;
    }

    public void setCurrentLength(int len) {
        mCurrentSize = len;
    }

    public char[] finishCurrentSegment()
    {
        if (mSegments == null) {
            mSegments = new ArrayList();
        }
        mSegments.add(mCurrentSegment);
        int oldLen = mCurrentSegment.length;
        mSegmentSize += oldLen;
        // Let's grow segments by 50%
        char[] curr = new char[oldLen + (oldLen >> 1)];
        mCurrentSize = 0;
        mCurrentSegment = curr;
        return curr;
    }

    /*
    //////////////////////////////////////////////
    // Standard methods:
    //////////////////////////////////////////////
     */

    /**
     * Note: calling this method may not be as efficient as calling
     * {@link #contentsAsString}, since it's not guaranteed that resulting
     * String is cached.
     */
    public String toString() {
         return contentsAsString();
    }

    /*
    //////////////////////////////////////////////
    // Internal methods:
    //////////////////////////////////////////////
     */

    /**
     * Method called if/when we need to append content when we have been
     * initialized to use shared buffer.
     */
    public void unshare(int needExtra)
    {
        int len = mInputLen;
        mInputLen = 0;
        char[] inputBuf = mInputBuffer;
        mInputBuffer = null;
        int start = mInputStart;
        mInputStart = -1;

        // Is buffer big enough, or do we need to reallocate?
        int needed = len+needExtra;
        if (mCurrentSegment == null || (needed > mCurrentSegment.length)) {
            if (needed > mInitialBufSize) {
                mCurrentSegment = new char[needed];
            } else {
                mCurrentSegment = new char[mInitialBufSize];
            }
        }
        if (len > 0) {
            System.arraycopy(inputBuf, start, mCurrentSegment, 0, len);
        }
        mSegmentSize = 0;
        mCurrentSize = len;
    }

    /**
     * Method called when current segment is full, to allocate new
     * segment.
     */
    private void expand(int minNewSegmentSize)
    {
        // First, let's move current segment to segment list:
        if (mSegments == null) {
            mSegments = new ArrayList();
        }
        char[] curr = mCurrentSegment;
        mSegments.add(curr);
        mSegmentSize += curr.length;
        int oldLen = curr.length;
        // Let's grow segments by 50% minimum
        int sizeAddition = oldLen >> 1;
        if (sizeAddition < minNewSegmentSize) {
            sizeAddition = minNewSegmentSize;
        }
        curr = new char[oldLen + sizeAddition];
        mCurrentSize = 0;
        mCurrentSegment = curr;
    }

    private char[] buildResultArray()
    {
        if (mResultString != null) { // Can take a shortcut...
            return mResultString.toCharArray();
        }
        char[] result;
        
        // Do we use shared array?
        if (mInputStart >= 0) {
            if (mInputLen < 1) {
                return EmptyIterator.getEmptyCharArray();
            }
            result = new char[mInputLen];
            System.arraycopy(mInputBuffer, mInputStart, result, 0,
                             mInputLen);
        } else { // nope 
            int size = size();
            if (size < 1) {
                return EmptyIterator.getEmptyCharArray();
            }
            int offset = 0;
            result = new char[size];
            if (mSegments != null) {
                for (int i = 0, len = mSegments.size(); i < len; ++i) {
                    char[] curr = (char[]) mSegments.get(i);
                    int currLen = curr.length;
                    System.arraycopy(curr, 0, result, offset, currLen);
                    offset += currLen;
                }
            }
            System.arraycopy(mCurrentSegment, 0, result, offset, mCurrentSize);
        }
        return result;
    }
}
