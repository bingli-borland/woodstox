package org.codehaus.staxbind.twitter;

import java.io.*;

import org.codehaus.staxbind.std.StdConverter;

/**
 * Base class for per-format converters used for "DB Converter" performance
 * test suite.
 */
public abstract class TwitterConverter
    extends StdConverter<TwitterSearch>
{
    //final static String FIELD_TABLE = "table";
    //final static String FIELD_ROW = "row";

    /**
     * Method that is to read all the data and convert it to
     * representation of full database contents
     */
    public abstract TwitterSearch readData(InputStream in) throws Exception;

    /**
     * Method that is to read all the data and convert it to
     * representation of full database contents
     *
     * @return Bogus result value; ideally generated from data, arbitrary
     *   but not random. Need to ensure no dead code elimination occurs
     */
    public abstract int writeData(OutputStream out, TwitterSearch data) throws Exception;
}
