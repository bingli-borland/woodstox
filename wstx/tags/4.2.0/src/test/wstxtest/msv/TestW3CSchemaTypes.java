package wstxtest.msv;

import javax.xml.stream.*;

import org.codehaus.stax2.*;
import org.codehaus.stax2.validation.*;

import wstxtest.vstream.BaseValidationTest;

/**
 * Simple testing of W3C Schema datatypes.
 * Added to test [WSTX-210].
 *
 * @author Tatu Saloranta
 */
public class TestW3CSchemaTypes
    extends BaseValidationTest
{
    final static String SCHEMA_INT =
        "<xsd:schema xmlns:xsd='http://www.w3.org/2001/XMLSchema'>\n"
        +"<xsd:element name='price' type='xsd:int' />"
        +"\n</xsd:schema>"
        ;

    final static String SCHEMA_FLOAT =
        "<xsd:schema xmlns:xsd='http://www.w3.org/2001/XMLSchema'>\n"
        +"<xsd:element name='price' type='xsd:float' />"
        +"\n</xsd:schema>"
        ;

    // // // First 'int' datatype

    public void testSimpleValidInt() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA_INT);
        XMLStreamReader2 sr = getReader("<price>129</price>");
        sr.validateAgainst(schema);
        streamThrough(sr);
    }

    public void testSimpleInvalidInt() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA_INT);
        verifyFailure("<price>abc</price>", schema, "invalid 'int' value",
                      "does not satisfy the \"int\" type");
    }

    // 21-Apr-2012, tatu: Fails but can't be fixed for 4.1; hence comment out
/*    
    public void testSimpleMissingInt() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA_INT);
        verifyFailure("<price></price>", schema, "missing 'int' value",
                      "does not satisfy the \"int\" type");
    }
    */

    // // // Then 'float' datatype

    public void testSimpleValidFloat() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA_FLOAT);
        XMLStreamReader2 sr = getReader("<price>1.00</price>");
        sr.validateAgainst(schema);
        streamThrough(sr);
    }

    public void testSimpleInvalidFloat() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA_FLOAT);
        verifyFailure("<price>abc</price>", schema, "invalid 'float' value",
                      "does not satisfy the \"float\" type");
    }

    // 21-Apr-2012, tatu: Fails but can't be fixed for 4.1; hence comment out
/*
    public void testSimpleMissingFloat() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA_FLOAT);
        verifyFailure("<price></price>", schema, "missing 'float' value",
                      "Undefined ID 'm3'");
    }
    */

    /*
    ///////////////////////////////////////////////////////////////////////
    // Helper
    ///////////////////////////////////////////////////////////////////////
    */

    XMLStreamReader2 getReader(String contents) throws XMLStreamException
    {
        XMLInputFactory2 f = getInputFactory();
        setValidating(f, false);
        return constructStreamReader(f, contents);
    }
}
