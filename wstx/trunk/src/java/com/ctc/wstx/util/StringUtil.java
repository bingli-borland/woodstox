package com.ctc.wstx.util;

import java.util.Collection;
import java.util.Iterator;

public final class StringUtil
{
    final static char CHAR_SPACE = ' '; // 0x0020

    static String sLF = null;

    public static String getLF()
    {
        String lf = sLF;
        if (lf == null) {
            try {
                lf = (String) System.getProperty("line.separator");
                sLF = (lf == null) ? "\n" : lf;
            } catch (Throwable t) {
                // Doh.... whatever; most likely SecurityException
                sLF = lf = "\n";
            }
        }
        return lf;
    }

    public static void appendLF(StringBuffer sb) {
        sb.append(getLF());
    }

    public static String concatEntries(Collection coll, String sep, String lastSep) {
        if (lastSep == null) {
            lastSep = sep;
        }
        int len = coll.size();
        StringBuffer sb = new StringBuffer(16 + (len << 3));
        Iterator it = coll.iterator();
        int i = 0;
        while (it.hasNext()) {
            if (i == 0) {
                ;
            } else if (i == (len - 1)) {
                sb.append(lastSep);
            } else {
                sb.append(sep);
            }
            ++i;
            sb.append(it.next());
        }
        return sb.toString();
    }

    /**
     * Method that will check character array passed, and remove all
     * "extra" spaces (leading and trailing white space), and normalize
     * other white space (more than one consequtive space character
     * replaced with a single space).
     *
     * @param buf Buffer that contains the String to check
     * @param origStart Offset of the first character of the text to check
     *   in the buffer
     * @param origEnd Offset of the character following the last character
     *   of the text (as per usual Java API convention)
     *
     * @return Normalized String, if any white space was removed or
     *   normalized; null if no changes were necessary.
     */
    public static String normalizeSpaces(char[] buf, int origStart, int origEnd)
    {
        --origEnd;

        int start = origStart;
        int end = origEnd;

        // First let's trim start...
        while (start <= end && buf[start] <= CHAR_SPACE) {
            ++start;
        }
        // Was it all empty?
        if (start > end) {
            return "";
        }

        /* Nope, need to trim from the end then (note: it's known that char
         * at index 'start' is not a space, at this point)
         */
        while (end > start && buf[end] <= CHAR_SPACE) {
            --end;
        }

        /* Ok, may have changes or not: now need to normalize
         * intermediate duplicate spaces. We also now that the
         * first and last characters can not be spaces.
         */
        int i = start+1;

        while (i < end) {
            if (buf[i] <= CHAR_SPACE) {
                if (buf[i+1] <= CHAR_SPACE) {
                    break;
                }
                // Nah; no hole for these 2 chars!
                i += 2;
            } else {
                ++i;
            }
        }

        // Hit the end?
        if (i >= end) {
            // Any changes?
            if (start == origStart && end == origEnd) {
                return null; // none
            }
            return new String(buf, start, (end-start)+1);
        }

        /* Nope, got a hole, need to constuct the damn thing. Shouldn't
         * happen too often... so let's just use StringBuffer()
         */
        StringBuffer sb = new StringBuffer(end-start); // can't be longer
        sb.append(buf, start, i-start); // won't add the starting space

        while (i <= end) {
            char c = buf[i++];
            if (c <= CHAR_SPACE) {
                sb.append(CHAR_SPACE);
                // Need to skip dups
                while (true) {
                    c = buf[i++];
                    if (c != CHAR_SPACE) {
                        sb.append(c);
                        break;
                    }
                }
            } else {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    public static boolean isAllWhitespace(String str) {
        for (int i = 0, len = str.length(); i < len; ++i) {
            if (str.charAt(i) > 0x0020) {
                return false;
            }
        }
        return true;
    }

    public static boolean isAllWhitespace(char[] ch, int start, int len) {
        len += start;
        for (; start < len; ++start) {
            if (ch[start] > 0x0020) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args)
    {
        if (args.length < 1) {
            System.err.println("Usage: java .... [word1] .... [wordN]");
            System.exit(1);
        }
        for (int i = 0; i < args.length; ++i) {
            String str = args[i];
            char[] buf = str.toCharArray();
            System.out.println("String in '"+str+"'");
            System.out.println("   -> out '"+normalizeSpaces(buf, 0, buf.length)+"'");
        }
    }
}
