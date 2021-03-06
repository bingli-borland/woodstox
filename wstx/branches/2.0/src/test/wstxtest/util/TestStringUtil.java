package wstxtest.util;

import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

import junit.framework.TestCase;

import com.ctc.wstx.util.StringUtil;

/**
 * Simple unit tests for testing methods of {@link StringUtil} utility
 * class.
 */
public class TestStringUtil
    extends TestCase
{
    public TestStringUtil(String name) {
        super(name);
    }

    public void testConcatEntries()
    {
        List l = new ArrayList();
        l.add("first");
        l.add("second");
        l.add("third");
        assertEquals("first, second and third",
                     StringUtil.concatEntries(l, ", ", " and "));

        l = new ArrayList();
        l.add("the only");
        assertEquals("the only",
                     StringUtil.concatEntries(l, ", ", " and "));
    }

    public void testIsAllWhitespace()
    {
        assertTrue(StringUtil.isAllWhitespace("  \r   \r\n    \t"));
        assertTrue(StringUtil.isAllWhitespace(" "));
        assertTrue(StringUtil.isAllWhitespace(" ".toCharArray(), 0, 1));
        assertTrue(StringUtil.isAllWhitespace("\r\n\t"));
        assertTrue(StringUtil.isAllWhitespace("\r\n\t".toCharArray(), 0, 3));
        assertTrue(StringUtil.isAllWhitespace("x \t".toCharArray(), 1, 2));
        assertTrue(StringUtil.isAllWhitespace(""));
        assertTrue(StringUtil.isAllWhitespace(new char[0], 0, 0));

        assertFalse(StringUtil.isAllWhitespace("x"));
        assertFalse(StringUtil.isAllWhitespace("                      !"));
    }

    public void testNormalizeSpaces()
    {
        String str = "\tmy   my";
        assertEquals("my my", StringUtil.normalizeSpaces(str.toCharArray(), 0,
                                                         str.length()));

        str = "my_my";
        assertFalse("my my".equals(StringUtil.normalizeSpaces(str.toCharArray(),
                                                              0, str.length())));

        str = "Xoh no  Z!";
        assertEquals("oh no",
                     StringUtil.normalizeSpaces(str.toCharArray(), 1,
                                                str.length() - 3));

    }

    public void testEqualEncodings()
    {
        assertTrue(StringUtil.equalEncodings("utf-8", "utf-8"));
        assertTrue(StringUtil.equalEncodings("UTF-8", "utf-8"));
        assertTrue(StringUtil.equalEncodings("UTF-8", "utf8"));
        assertTrue(StringUtil.equalEncodings("UTF8", "utf_8"));
        assertTrue(StringUtil.equalEncodings("US_ASCII", "us-ascii"));
        assertTrue(StringUtil.equalEncodings("utf 8", "Utf-8"));

        assertFalse(StringUtil.equalEncodings("utf-8", "utf-16"));
        assertFalse(StringUtil.equalEncodings("isolatin", "iso-8859-1"));
        assertFalse(StringUtil.equalEncodings("utf8", "utf"));
    }
}
