package org.codehaus.stax2.ri.typed;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.codehaus.stax2.typed.ValueDecoder;

/**
 * Default implementation that strives for correctness and reasonable
 * efficiency (at least for simple types). To simplify implementation,
 * decoders are stateful.
 *
 * @author Tatu Saloranta
 */
public class DefaultValueDecoder
    extends ValueDecoder
{
    private final static long L_BILLION = 1000000000;

    private final static long L_MAX_INT = (long) Integer.MAX_VALUE;

    private final static long L_MIN_INT = (long) Integer.MIN_VALUE;

    final static BigInteger BD_MIN_LONG = BigInteger.valueOf(Long.MIN_VALUE);
    final static BigInteger BD_MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);

    // // // State

    protected String mType;

    protected char[] mValueBuffer;

    /**
     * Pointer to the next character to check, within lexical value
     */
    protected int mPtr;

    /**
     * Pointer to the pointer in lexical value <b>after</b> last
     * included character.
     */
    protected int mEnd;

    // // // Life-cycle

    public DefaultValueDecoder() { }

    /*
    ///////////////////////////////////////////////
    // Public API implementation
    ///////////////////////////////////////////////
     */

    public int decodeInt(String lexical, int start, int end)
        throws IllegalArgumentException
    {
        char ch = resetAndTrimLeading(lexical, "integer", start, end);
        boolean neg = (ch == '-');

        int nr = skipSignAndZeroes(lexical, ch, neg || (ch == '+'));

        // Quick check for short (single-digit) values:
        if (mPtr >= mEnd) {
            return neg ? -nr : nr;
        }

        // Otherwise, need to verify that is [digit*][ws*]
        int len = trimTrailingAndCheckDigits(lexical);
        if (len < 1) {
            if (len == 0) {
                return neg ? -nr : nr;
            }
            reportInvalidValue(lexical, start);
        }
        // Note: len is one less than total length (skipped first digit)
        if (len <= 8) { // no overflow
            int i = parseInt(nr, lexical, mPtr, mPtr+len);
            return neg ? -i : i;
        }
        // Otherwise, may have overflow
        // Max 10 digits for a legal int
        if (len == 9 && nr < 3) { // min/max is ~2 billion (+/-)
            long base = L_BILLION;
            if (nr == 2) {
                base += L_BILLION;
            }
            int i = parseInt(lexical, mPtr, mPtr+len);
            long l = base + (long) i;
            if (neg) {
                l = -l;
                if (l >= L_MIN_INT) {
                    return (int) l;
                }
            } else {
                if (l <= L_MAX_INT) {
                    return (int) l;
                }
            }
        }
        throw new IllegalArgumentException("value \""+lexicalDesc(lexical, start)+"\" not a valid 32-bit integer: overflow.");
    }

    public int decodeInt(char[] lexical, int start, int end)
        throws IllegalArgumentException
    {
        char ch = resetAndTrimLeading(lexical, "integer", start, end);
        boolean neg = (ch == '-');
        int nr = skipSignAndZeroes(lexical, ch, neg || (ch == '+'));

        // Quick check for short (single-digit) values:
        if (mPtr >= mEnd) {
            return neg ? -nr : nr;
        }

        // Otherwise, need to verify that is [digit*][ws*]
        int len = trimTrailingAndCheckDigits(lexical);
        if (len < 1) {
            if (len == 0) {
                return neg ? -nr : nr;
            }
            reportInvalidValue(lexical, start);
        }
        // Note: len is one less than total length (skipped first digit)
        if (len <= 8) { // no overflow
            int i = parseInt(nr, lexical, mPtr, mPtr+len);
            return neg ? -i : i;
        }
        // Otherwise, may have overflow
        // Max 10 digits for a legal int
        if (len == 9 && nr < 3) { // min/max is ~2 billion (+/-)
            long base = L_BILLION;
            if (nr == 2) {
                base += L_BILLION;
            }
            int i = parseInt(lexical, mPtr, mPtr+len);
            long l = base + (long) i;
            if (neg) {
                l = -l;
                if (l >= L_MIN_INT) {
                    return (int) l;
                }
            } else {
                if (l <= L_MAX_INT) {
                    return (int) l;
                }
            }
        }
        throw new IllegalArgumentException("value \""+lexicalDesc(lexical, start)+"\" not a valid 32-bit integer: overflow.");
    }

    public long decodeLong(String lexical, int start, int end)
        throws IllegalArgumentException
    {
        char ch = resetAndTrimLeading(lexical, "long", start, end);
        boolean neg = (ch == '-');

        int nr = skipSignAndZeroes(lexical, ch, neg || (ch == '+'));

        // Quick check for short (single-digit) values:
        if (mPtr >= mEnd) {
            return (long) (neg ? -nr : nr);
        }

        // Otherwise, need to verify that is [digit*][ws*]
        int len = trimTrailingAndCheckDigits(lexical);
        if (len < 1) {
            if (len == 0) {
                return (long) (neg ? -nr : nr);
            }
            reportInvalidValue(lexical, start);
        }
        // Note: len is one less than total length (skipped first digit)
        // Can parse more cheaply, if it's really just an int...
        if (len <= 8) { // no overflow
            int i = parseInt(nr, lexical, mPtr, mPtr+len);
            return neg ? -i : i;
        }
        // At this point, let's just push back the first digit... simpler
        --mPtr;
        ++len;

        // Still simple long? 
        if (len <= 18) {
            long l = parseLong(lexical, mPtr, mPtr+len+1);
            return neg ? -l : l;
        }
        /* Otherwise, let's just fallback to an expensive option,
         * BigInteger. While relatively inefficient, it's simple
         * to use, reliable etc.
         */
        end = mPtr+len;
        String str = lexical.substring(mPtr, mPtr+len);
        BigInteger bi = new BigInteger(str);

        // But we may over/underflow, let's check:
        if (neg) {
            bi = bi.negate();
            if (bi.compareTo(BD_MIN_LONG) >= 0) {
                return bi.longValue();
            }
        } else {
            if (bi.compareTo(BD_MAX_LONG) <= 0) {
                return bi.longValue();
            }
        }

        throw new IllegalArgumentException("value \""+lexicalDesc(lexical, start)+"\" not a valid 64-bit integer: overflow.");
    }

    public long decodeLong(char[] lexical, int start, int end)
        throws IllegalArgumentException
    {
        // !!! TBI
        return 0L;
    }

    // Fixed-length floating-point types

    public float decodeFloat(String lexical, int start, int end)
        throws IllegalArgumentException
    {
        // !!! TBI
        return 0.0f;
    }

    public float decodeFloat(char[] lexical, int start, int end)
        throws IllegalArgumentException
    {
        // !!! TBI
        return 0.0f;
    }

    public double decodeDouble(String lexical, int start, int end)
        throws IllegalArgumentException
    {
        // !!! TBI
        return 0.0;
    }

    public double decodeDouble(char[] lexical, int start, int end)
        throws IllegalArgumentException
    {
        // !!! TBI
        return 0.0f;
    }

    // Unlimited precision floating-point types

    public BigDecimal decodeDecimal(String lexical, int start, int end)
        throws IllegalArgumentException
    {
        // !!! TBI
        return null;
    }

    public BigDecimal decodeDecimal(char[] lexical, int start, int end)
        throws IllegalArgumentException
    {
        // !!! TBI
        return null;
    }

    // Enumerated types

    public boolean decodeBoolean(String lexical, int start, int end)
        throws IllegalArgumentException
    {
        // First, skip leading ws if any
        char c = resetAndTrimLeading(lexical, "boolean", start, end);
        int ptr = mPtr;
        int len = mEnd-ptr;
        if (c == 't') {
            if (len >= 3
                && lexical.charAt(ptr) == 'r'
                && lexical.charAt(++ptr) == 'u'
                && lexical.charAt(++ptr) == 'e') {
                if (++ptr >= mEnd || allWhitespace(lexical, ptr, mEnd)) {
                    return true;
                }
            }
        } else if (c == 'f') {
            if (len >= 4
                && lexical.charAt(ptr) == 'a'
                && lexical.charAt(++ptr) == 'l'
                && lexical.charAt(++ptr) == 's'
                && lexical.charAt(++ptr) == 'e') {
                if (++ptr >= mEnd || allWhitespace(lexical, ptr, mEnd)) {
                    return false;
                }
            }
        } else if (c == '0') {
            if (ptr >= mEnd || allWhitespace(lexical, ptr, mEnd)) {
                return false;
            }
        } else if (c == '1') {
            if (ptr >= mEnd || allWhitespace(lexical, ptr, mEnd)) {
                return true;
            }
        }
        throw new IllegalArgumentException("value \""+lexicalDesc(lexical, start)+"\" not a valid lexical representation of boolean");
    }

    public boolean decodeBoolean(char[] lexical, int start, int end)
        throws IllegalArgumentException
    {
        // First, skip leading ws if any
        char c = resetAndTrimLeading(lexical, "boolean", start, end);
        int ptr = mPtr;
        int len = mEnd-ptr;
        if (c == 't') {
            if (len >= 3
                && lexical[ptr] == 'r'
                && lexical[++ptr] == 'u'
                && lexical[++ptr] == 'e') {
                if (++ptr >= mEnd || allWhitespace(lexical, ptr, mEnd)) {
                    return true;
                }
            }
        } else if (c == 'f') {
            if (len >= 4
                && lexical[ptr] == 'a'
                && lexical[++ptr] == 'l'
                && lexical[++ptr] == 's'
                && lexical[++ptr] == 'e') {
                if (++ptr >= mEnd || allWhitespace(lexical, ptr, mEnd)) {
                    return false;
                }
            }
        } else if (c == '0') {
            if (ptr >= mEnd || allWhitespace(lexical, ptr, mEnd)) {
                return false;
            }
        } else if (c == '1') {
            if (ptr >= mEnd || allWhitespace(lexical, ptr, mEnd)) {
                return true;
            }
        }
        throw new IllegalArgumentException("value \""+lexicalDesc(lexical, start)+"\" not a valid lexical representation of boolean");
    }

    /*
    ///////////////////////////////////////////////
    // Internal methods, trimming/scanning
    ///////////////////////////////////////////////
     */

    /**
     * @param start First character of the lexical value to process
     * @param pastEnd Pointer character <b>after</b> last valid character
     *   of the lexical value
     *
     * @return First non-white space character from the String
     */
    protected char resetAndTrimLeading(String lexical, String type, int start, int end)
    {
        mType = type;
        mEnd = end;

        while (start < end) {
            char c = lexical.charAt(start++);
            if (!isSpace(c)) {
                mPtr = start;
                return c;
            }
        }
        reportMissingValue();
        return '?'; // never gets here
    }

    protected char resetAndTrimLeading(char[] lexical, String type, int start, int end)
    {
        mType = type;
        mEnd = end;

        while (start < end) {
            char c = lexical[start++];
            if (!isSpace(c)) {
                mPtr = start;
                return c;
            }
        }
        reportMissingValue();
        return '?'; // never gets here
    }

    /**
     * Method called to check that remaining String consists of zero or
     * more digits, followed by zero or more white space, and nothing else;
     * and to trim trailing white space, if any.
     *
     * @return Number of valid digits found; or -1 to indicate invalid
     *   input
     */
    protected int trimTrailingAndCheckDigits(String lexical)
    {
        // Let's start from beginning
        int ptr = mPtr;

        // Note: caller won't call this with empty String
        char ch = lexical.charAt(ptr);

        // First, skim through digits
        while (ch <= '9' && ch >= '0') {
            if (++ptr >= mEnd) { // no trailing white space, valid
                return (ptr - mPtr);
            }
            ch = lexical.charAt(ptr);
        }

        // And then see what follows digits...
        int len = ptr - mPtr;
        while (true) {
            if (!isSpace(ch)) { // garbage following white space (or digits)
                return -1;
            }
            if (++ptr >= mEnd) {
                return len;
            }
            ch = lexical.charAt(ptr);
        }
    }

    protected int trimTrailingAndCheckDigits(char[] lexical)
    {
        // Let's start from beginning
        int ptr = mPtr;

        // Note: caller won't call this with empty String
        char ch = lexical[ptr];

        // First, skim through digits
        while (ch <= '9' && ch >= '0') {
            if (++ptr >= mEnd) { // no trailing white space, valid
                return (ptr - mPtr);
            }
            ch = lexical[ptr];
        }

        // And then see what follows digits...
        int len = ptr - mPtr;
        while (true) {
            if (!isSpace(ch)) { // garbage following white space (or digits)
                return -1;
            }
            if (++ptr >= mEnd) {
                return len;
            }
            ch = lexical[ptr];
        }
    }

    /**
     * @return Numeric value of the first non-zero character (or, in
     *   case of a zero value, zero)
     */
    protected int skipSignAndZeroes(String lexical, char ch, boolean hasSign)
    {
        int ptr = mPtr;

        // Then optional sign
        if (hasSign) {
            if (ptr >= mEnd) {
                reportInvalidValue(String.valueOf(ch));
            }
            ch = lexical.charAt(ptr++);
        }

        // Has to start with a digit
        int value = ch - '0';
        if (value < 0 || value > 9) {
            reportInvalidValue(lexical, mPtr-1);
        }

        // Then, leading zero(es) to skip? (or just value zero itself)
        while (value == 0 && ptr < mEnd) {
            int v2 = lexical.charAt(ptr) - '0';
            if (v2 < 0 || v2 > 9) {
                break;
            }
            ++ptr;
            value = v2;
        }
        mPtr = ptr;
        return value;
    }

    protected int skipSignAndZeroes(char[] lexical, char ch, boolean hasSign)
    {
        int ptr = mPtr;

        // Then optional sign
        if (hasSign) {
            if (ptr >= mEnd) {
                reportInvalidValue(String.valueOf(ch));
            }
            ch = lexical[ptr++];
        }

        // Has to start with a digit
        int value = ch - '0';
        if (value < 0 || value > 9) {
            reportInvalidValue(lexical, mPtr-1);
        }

        // Then leading zero(es) to skip? (or just value zero itself)
        while (value == 0 && ptr < mEnd) {
            int v2 = lexical[ptr] - '0';
            if (v2 < 0 || v2 > 9) {
                break;
            }
            ++ptr;
            value = v2;
        }
        mPtr = ptr;
        return value;
    }

    protected final boolean allWhitespace(String lexical, int start, int end)
    {
        while (start < end) {
            if (!isSpace(lexical.charAt(start++))) {
                return false;
            }
        }
        return true;
    }

    protected final boolean allWhitespace(char[] lexical, int start, int end)
    {
        while (start < end) {
            if (!isSpace(lexical[start++])) {
                return false;
            }
        }
        return true;
    }

    /*
    ///////////////////////////////////////////////
    // Internal methods, error reporting
    ///////////////////////////////////////////////
     */

    protected void reportMissingValue()
    {
        throw new IllegalArgumentException("Empty value (all white space) not a valid lexical representation of "+mType);
    }

    protected void reportInvalidValue(String lexical)
    {
        // !!! Should we escape ctrl+chars etc?
        throw new IllegalArgumentException("Value \""+lexical+"\" not a valid lexical representation of "+mType);
    }

    protected void reportInvalidValue(String lexical, int startOffset)
    {
        throw new IllegalArgumentException("Value \""+lexicalDesc(lexical, startOffset)+"\" not a valid lexical representation of "+mType);
    }

    protected void reportInvalidValue(char[] lexical, int startOffset)
    {
        throw new IllegalArgumentException("Value \""+lexicalDesc(lexical, startOffset)+"\" not a valid lexical representation of "+mType);
    }

    protected String lexicalDesc(char[] lexical, int startOffset)
    {
        int len = mEnd-startOffset;
        String str = new String(lexical, startOffset, len);
        // !!! Should we escape ctrl+chars etc?
        return str.trim();
    }

    protected String lexicalDesc(String lexical, int startOffset)
    {
        String str = lexical.substring(startOffset, mEnd);
        // !!! Should we escape ctrl+chars etc?
        return str.trim();
    }

    /*
    ///////////////////////////////////////////////
    // Internal methods, int conversions
    ///////////////////////////////////////////////
     */

    /**
     * Fast method for parsing integers that are known to fit into
     * regular 32-bit signed int type. This means that length is
     * between 1 and 9 digits (inclusive)
     *
     * @return Parsed integer value
     */
    protected final static int parseInt(char[] digitChars, int start, int end)
    {
        // This looks ugly, but appears to be the fastest way:
        int num = digitChars[start] - '0';
        if (++start < end) {
            num = (num * 10) + (digitChars[start] - '0');
            if (++start < end) {
                num = (num * 10) + (digitChars[start] - '0');
                if (++start < end) {
                    num = (num * 10) + (digitChars[start] - '0');
                    if (++start < end) {
                        num = (num * 10) + (digitChars[start] - '0');
                        if (++start < end) {
                            num = (num * 10) + (digitChars[start] - '0');
                            if (++start < end) {
                                num = (num * 10) + (digitChars[start] - '0');
                                if (++start < end) {
                                    num = (num * 10) + (digitChars[start] - '0');
                                    if (++start < end) {
                                        num = (num * 10) + (digitChars[start] - '0');
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return num;
    }

    protected final static int parseInt(int num, char[] digitChars, int start, int end)
    {
        // This looks ugly, but appears to be the fastest way:
        num = (num * 10) + (digitChars[start] - '0');
        if (++start < end) {
            num = (num * 10) + (digitChars[start] - '0');
            if (++start < end) {
                num = (num * 10) + (digitChars[start] - '0');
                if (++start < end) {
                    num = (num * 10) + (digitChars[start] - '0');
                    if (++start < end) {
                        num = (num * 10) + (digitChars[start] - '0');
                        if (++start < end) {
                            num = (num * 10) + (digitChars[start] - '0');
                            if (++start < end) {
                                num = (num * 10) + (digitChars[start] - '0');
                                if (++start < end) {
                                    num = (num * 10) + (digitChars[start] - '0');
                                }
                            }
                        }
                    }
                }
            }
        }
        return num;
    }

    protected final static int parseInt(String digitChars, int start, int end)
    {
        int num = digitChars.charAt(start) - '0';
        if (++start < end) {
            num = (num * 10) + (digitChars.charAt(start) - '0');
            if (++start < end) {
                num = (num * 10) + (digitChars.charAt(start) - '0');
                if (++start < end) {
                    num = (num * 10) + (digitChars.charAt(start) - '0');
                    if (++start < end) {
                        num = (num * 10) + (digitChars.charAt(start) - '0');
                        if (++start < end) {
                            num = (num * 10) + (digitChars.charAt(start) - '0');
                            if (++start < end) {
                                num = (num * 10) + (digitChars.charAt(start) - '0');
                                if (++start < end) {
                                    num = (num * 10) + (digitChars.charAt(start) - '0');
                                    if (++start < end) {
                                        num = (num * 10) + (digitChars.charAt(start) - '0');
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return num;
    }

    protected final static int parseInt(int num, String digitChars, int start, int end)
    {
        num = (num * 10) + (digitChars.charAt(start) - '0');
        if (++start < end) {
            num = (num * 10) + (digitChars.charAt(start) - '0');
            if (++start < end) {
                num = (num * 10) + (digitChars.charAt(start) - '0');
                if (++start < end) {
                    num = (num * 10) + (digitChars.charAt(start) - '0');
                    if (++start < end) {
                        num = (num * 10) + (digitChars.charAt(start) - '0');
                        if (++start < end) {
                            num = (num * 10) + (digitChars.charAt(start) - '0');
                            if (++start < end) {
                                num = (num * 10) + (digitChars.charAt(start) - '0');
                                if (++start < end) {
                                    num = (num * 10) + (digitChars.charAt(start) - '0');
                                }
                            }
                        }
                    }
                }
            }
        }
        return num;
    }

    public final static long parseLong(char[] digitChars, int start, int end)
    {
        // Note: caller must ensure length is [10, 18]
        int start2 = end-9;
        long val = parseInt(digitChars, start, start2) * L_BILLION;
        return val + (long) parseInt(digitChars, start2, end);
    }

    public final static long parseLong(String digitChars, int start, int end)
    {
        // Note: caller must ensure length is [10, 18]
        int start2 = end-9;
        long val = parseInt(digitChars, start, start2) * L_BILLION;
        return val + (long) parseInt(digitChars, start2, end);
    }

    /*
    ///////////////////////////////////////////////
    // Internal methods, low-level string manipulation
    ///////////////////////////////////////////////
     */

    /**
     *<p>
     * Note: final for efficient inlining by JVM/HotSpot. Means
     * one can not redefine it; and instead need to override
     * callers (which just means methods above used for trimming
     * white space)
     *<p>
     * Note, too, that it is assumed that any "weird" white space
     * (xml 1.1 LSEP and NEL) have been replaced by canonical
     * alternatives (linefeed for element content, regular space
     * for attributes)
     */
    private final boolean isSpace(char c)
    {
        return ((int) c) <= 0x0020;
    }
}
