package com.ctc.wstx.io;

import com.ctc.wstx.util.StringUtil;

/**
 * Simple utility class that normalizes given character input character
 * set names into canonical (within Woodstox, anyways) names.
 */
public final class CharsetNames
{
    /*
    //////////////////////////////////////////////////
    // Canonical names used internally
    //////////////////////////////////////////////////
     */

    // // // Unicode variants:

    public final static String CS_US_ASCII = "US-ASCII";
    public final static String CS_UTF8 = "UTF-8";

    /**
     * This constants is intentionally vague, so that some other information
     * will be needed to determine the endianness.
     */
    public final static String CS_UTF16 = "UTF-16";

    public final static String CS_UTF16BE = "UTF-16BE";
    public final static String CS_UTF16LE = "UTF-16LE";
    public final static String CS_UTF32 = "UTF-32";
    public final static String CS_UTF32BE = "UTF-32BE";
    public final static String CS_UTF32LE = "UTF-32LE";

    // // // 8-bit ISO encodings:

    public final static String CS_ISO_LATIN1 = "ISO-8859-1";

    // // // Japanese non-unicode encodings:

    public final static String CS_SHIFT_JIS = "Shift_JIS";

    // // // Other oddities:

    public final static String CS_EBCDIC = "EBCDIC";

    /*
    //////////////////////////////////////////////////
    // Utility methods
    //////////////////////////////////////////////////
     */

    public static String normalize(String csName)
    {
        if (csName == null || csName.length() < 3) {
            return csName;
        }

        /* Canonical charset names here are from IANA recommendation:
         *   http://www.iana.org/assignments/character-sets
         * but comparison is done loosely (case-insensitive, ignoring
         * spacing, underscore vs. hyphen etc) to try to make detection
         * as extensive as possible.
         */

        /* But first bit of pre-filtering: it seems like 'cs' prefix
         * is applicable to pretty much all actual encodings (as per
         * IANA recommendations; csASCII, csUcs4 etc). So, let's just
         * strip out the prefix if so
         */
        boolean gotCsPrefix = false;
        char c = csName.charAt(0);
        if (c == 'c' || c == 'C'){
            char d = csName.charAt(1);
            if (d == 's' || d == 'S') {
                csName = csName.substring(2);
                c = csName.charAt(0);
                gotCsPrefix = true;
            }
        }

        switch (c) {
        case 'a':
        case 'A':
            if (StringUtil.equalEncodings(csName, "ASCII")) {
                return CS_US_ASCII;
            }
            break;

        case 'c':
        case 'C':
            // Hmmh. There are boatloads of these... but what to do with them?
            if (StringUtil.encodingStartsWith(csName, "cs")) {
                // !!! TBI
            }
            break;

        case 'e':
        case 'E':
            if (csName.startsWith("EBCDIC") ||
                csName.startsWith("ebcdic")) {
                return CS_EBCDIC;
            }
            break;
        case 'i':
        case 'I':
            if (StringUtil.equalEncodings(csName, "ISO-8859-1")
                || StringUtil.equalEncodings(csName, "ISO-Latin1")) {
                return CS_ISO_LATIN1;
            }
            if (StringUtil.encodingStartsWith(csName, "ISO-10646")) {
                /* Hmmh. There are boatloads of alternatives here, it
                 * seems (see http://www.iana.org/assignments/character-sets
                 * for details)
                 */
                int ix = csName.indexOf("10646");
                String suffix = csName.substring(ix+5);
                if (StringUtil.equalEncodings(suffix, "UCS-Basic")) {
                    return CS_US_ASCII;
                }
                if (StringUtil.equalEncodings(suffix, "Unicode-Latin1")) {
                    return CS_ISO_LATIN1;
                }
                if (StringUtil.equalEncodings(suffix, "UCS-2")) {
                    return CS_UTF16; // endianness?
                }
                if (StringUtil.equalEncodings(suffix, "UCS-4")) {
                    return CS_UTF32; // endianness?
                }
                if (StringUtil.equalEncodings(suffix, "UTF-1")) {
                    // "Universal Transfer Format (1), this is the multibyte encoding, that subsets ASCII-7"???
                    return CS_US_ASCII;
                }
                if (StringUtil.equalEncodings(suffix, "J-1")) {
                    // Name: ISO-10646-J-1, Source: ISO 10646 Japanese, see RFC 1815.
                    // ... so what does that really mean? let's consider it ascii
                    return CS_US_ASCII;
                }
                if (StringUtil.equalEncodings(suffix, "US-ASCII")) {
                    return CS_US_ASCII;
                }
            }
            break;
        case 'j':
        case 'J':
            if (StringUtil.equalEncodings(csName, "JIS_Encoding")) {
                return CS_SHIFT_JIS;
            }
            break;
        case 's':
        case 'S':
            if (StringUtil.equalEncodings(csName, "Shift_JIS")) {
                return CS_SHIFT_JIS;
            }
            break;
        case 'u':
        case 'U':
            if (csName.length() < 2) { // sanity check
                break;
            }
            switch (csName.charAt(1)) {
            case 'c':
            case 'C':
                if (StringUtil.equalEncodings(csName, "UCS-2")) {
                    return CS_UTF16;
                }
                if (StringUtil.equalEncodings(csName, "UCS-4")) {
                    return CS_UTF32;
                }
                break;
            case 'n': // csUnicodeXxx, 
            case 'N':
                if (gotCsPrefix) {
                    if (StringUtil.equalEncodings(csName, "Unicode")) {
                        return CS_UTF16; // need BOM
                    }
                    if (StringUtil.equalEncodings(csName, "UnicodeAscii")) {
                        return CS_ISO_LATIN1;
                    }
                    if (StringUtil.equalEncodings(csName, "UnicodeAscii")) {
                        return CS_US_ASCII;
                    }
                }
                break;
            case 's':
            case 'S':
                if (StringUtil.equalEncodings(csName, "US-ASCII")) {
                    return CS_US_ASCII;
                }
                break;
            case 't': 
            case 'T':
                if (StringUtil.equalEncodings(csName, "UTF-8")) {
                    return CS_UTF8;
                }
                if (StringUtil.equalEncodings(csName, "UTF-16BE")) {
                    return CS_UTF16BE;
                }
                if (StringUtil.equalEncodings(csName, "UTF-16LE")) {
                    return CS_UTF16LE;
                }
                if (StringUtil.equalEncodings(csName, "UTF-16")) {
                    return CS_UTF16;
                }
                if (StringUtil.equalEncodings(csName, "UTF-32BE")) {
                    return CS_UTF32BE;
                }
                if (StringUtil.equalEncodings(csName, "UTF-32LE")) {
                    return CS_UTF32LE;
                }
                if (StringUtil.equalEncodings(csName, "UTF-32")) {
                    return CS_UTF32;
                }
                if (StringUtil.equalEncodings(csName, "UTF")) {
                    // 21-Jan-2006, TSa: ??? What is this to do... ?
                    return CS_UTF16;
                }
            }
            break;
        }

        return csName;
    }
}
