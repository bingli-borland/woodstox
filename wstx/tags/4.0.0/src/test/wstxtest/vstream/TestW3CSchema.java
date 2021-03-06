package wstxtest.vstream;

import java.io.*;

import javax.xml.stream.*;

import org.codehaus.stax2.*;
import org.codehaus.stax2.validation.*;

import wstxtest.stream.BaseStreamTest;

/**
 * This is a simple base-line "smoke test" that checks that W3C Schema
 * validation works at least minimally.
 */
public class TestW3CSchema
    extends BaseStreamTest
{
    /**
     * Sample schema, using sample 'personal.xsd' found from
     * the web
     */
    final static String SIMPLE_NON_NS_SCHEMA =
"<?xml version='1.0' encoding='UTF-8'?>\n"
+"<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>\n"
 +"<xs:element name='personnel'>\n"
  +"<xs:complexType>\n"
   +"<xs:sequence>\n"
     +"<xs:element ref='person' minOccurs='1' maxOccurs='unbounded'/>\n"
   +"</xs:sequence>\n"
  +"</xs:complexType>\n"
  +"<xs:unique name='unique1'>\n"
   +"<xs:selector xpath='person'/>\n"
   +"<xs:field xpath='name/given'/>\n"
   +"<xs:field xpath='name/family'/>\n"
  +"</xs:unique>\n"
 +"</xs:element>\n"
 +"<xs:element name='person'>\n"
  +"<xs:complexType>\n"
   +"<xs:sequence>\n"
     +"<xs:element ref='name'/>\n"
     +"<xs:element ref='email' minOccurs='0' maxOccurs='unbounded'/>\n"
     +"<xs:element ref='url'   minOccurs='0' maxOccurs='unbounded'/>\n"
     +"<xs:element ref='link'  minOccurs='0' maxOccurs='1'/>\n"
   +"</xs:sequence>\n"
   +"<xs:attribute name='id'  type='xs:ID' use='required'/>\n"
   +"<xs:attribute name='note' type='xs:string'/>\n"
   +"<xs:attribute name='contr' default='false'>\n"
    +"<xs:simpleType>\n"
     +"<xs:restriction base = 'xs:string'>\n"
       +"<xs:enumeration value='true'/>\n"
       +"<xs:enumeration value='false'/>\n"
     +"</xs:restriction>\n"
    +"</xs:simpleType>\n"
   +"</xs:attribute>\n"
   +"<xs:attribute name='salary' type='xs:integer'/>\n"
  +"</xs:complexType>\n"
 +"</xs:element>\n"
 +"<xs:element name='name'>\n"
  +"<xs:complexType>\n"
   +"<xs:all>\n"
    +"<xs:element ref='family'/>\n"
    +"<xs:element ref='given'/>\n"
   +"</xs:all>\n"
  +"</xs:complexType>\n"
 +"</xs:element>\n"
 +"<xs:element name='family' type='xs:string'/>\n"
 +"<xs:element name='given' type='xs:string'/>\n"
 +"<xs:element name='email' type='xs:string'/>\n"
 +"<xs:element name='url'>\n"
  +"<xs:complexType>\n"
   +"<xs:attribute name='href' type='xs:string' default='http://'/>\n"
  +"</xs:complexType>\n"
 +"</xs:element>\n"

 +"<xs:element name='link'>\n"
  +"<xs:complexType>\n"
   +"<xs:attribute name='manager' type='xs:IDREF'/>\n"
   +"<xs:attribute name='subordinates' type='xs:IDREFS'/>\n"
  +"</xs:complexType>\n"
 +"</xs:element>\n"
+"</xs:schema>\n"
        ;

    /**
     * Test validation against a simple document valid according
     * to a very simple W3C schema.
     */
    public void testSimpleNonNs()
        throws XMLStreamException
    {
        String XML =
"<personnel>\n"
+"  <person id='a123' contr='true'>"
+"    <name>"
+"<family>Family</family><given>Fred</given>"
+"    </name>"
+"    <url href='urn:something' />"
+"  </person>"
+"  <person id='b12'>"
+"    <name><family>Blow</family><given>Joe</given>"
+"    </name>"
+"    <url />"
+"  </person>"
+"</personnel>"
            ;

        XMLValidationSchema schema = parseSchema(SIMPLE_NON_NS_SCHEMA);
        XMLStreamReader2 sr = getReader(XML);
        sr.validateAgainst(schema);

        try {
            assertTokenType(START_ELEMENT, sr.next());
            assertEquals("personnel", sr.getLocalName());
            
            while (sr.hasNext()) {
                int type = sr.next();
            }
        } catch (XMLValidationException vex) {
            fail("Did not expect validation exception, got: "+vex);
        }

        assertTokenType(END_DOCUMENT, sr.getEventType());
    }

    /**
     * Test validation of a simple document that is invalid according
     * to the simple test schema.
     */
    public void testSimpleNonNsMissingId()
        throws XMLStreamException
    {
        XMLValidationSchema schema = parseSchema(SIMPLE_NON_NS_SCHEMA);
        String XML = "<personnel><person>"
            +"<name><family>F</family><given>G</given>"
            +"</name></person></personnel>";
        verifyFailure(XML, schema, "missing id attribute",
                      "is missing \"id\" attribute");
    }

    public void testSimpleNonNsUndefinedId()
        throws XMLStreamException
    {
        XMLValidationSchema schema = parseSchema(SIMPLE_NON_NS_SCHEMA);
        String XML = "<personnel><person id='a1'>"
            +"<name><family>F</family><given>G</given>"
            +"</name><link manager='m3' /></person></personnel>";
        verifyFailure(XML, schema, "undefined referenced id ('m3')",
                      "Undefined ID 'm3'");
    }

    public void testSimpleDataTypes()
        throws XMLStreamException
    {
        // Another sample schema, from 
        String SCHEMA = 
"<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>\n"
+"<xs:element name='item'>\n"
+" <xs:complexType>\n"
+"  <xs:sequence>\n"
+"   <xs:element name='quantity' type='xs:positiveInteger'/>"
+"   <xs:element name='price' type='xs:decimal'/>"
+"  </xs:sequence>"
+" </xs:complexType>"
+"</xs:element>"
+"</xs:schema>"
            ;

        XMLValidationSchema schema = parseSchema(SCHEMA);

        // First, valid doc:
        String XML = "<item><quantity>3  </quantity><price>\r\n4.05</price></item>";
        XMLStreamReader2 sr = getReader(XML);
        sr.validateAgainst(schema);

        try {
            assertTokenType(START_ELEMENT, sr.next());
            assertEquals("item", sr.getLocalName());

            assertTokenType(START_ELEMENT, sr.next());
            assertEquals("quantity", sr.getLocalName());
            String str = sr.getElementText();
            assertEquals("3", str.trim());

            assertTokenType(START_ELEMENT, sr.next());
            assertEquals("price", sr.getLocalName());
            str = sr.getElementText();
            assertEquals("4.05", str.trim());

            assertTokenType(END_ELEMENT, sr.next());
            assertTokenType(END_DOCUMENT, sr.next());
        } catch (XMLValidationException vex) {
            fail("Did not expect validation exception, got: "+vex);
        }
        sr.close();

        // Then invalid (wrong type for value)
        XML = "<item><quantity>34b</quantity><price>1.00</price></item>";
        sr.validateAgainst(schema);
        verifyFailure(XML, schema, "invalid 'positive integer' datatype",
                      "does not satisfy the \"positiveInteger\"");
        sr.close();

        // Another invalid, empty value
        XML = "<item><quantity>    </quantity><price>1.00</price></item>";
        sr.validateAgainst(schema);
	// 12-Nov-2008, TSa: still having MSV bug here, need to suppress failure
        verifyFailure(XML, schema, "invalid (missing) positive integer value",
                      "does not satisfy the \"positiveInteger\"", false);
        sr.close();

        // Another invalid, missing value
        XML = "<item><quantity></quantity><price>1.00</price></item>";
        sr.validateAgainst(schema);
	// 12-Nov-2008, TSa: still having MSV bug here, need to suppress failure
        verifyFailure(XML, schema, "invalid (missing) positive integer value",
                      "does not satisfy the \"positiveInteger\"", false);
        sr.close();
    }

    /*
    //////////////////////////////////////////////////////////////
    // Helper methods
    //////////////////////////////////////////////////////////////
     */

    XMLValidationSchema parseSchema(String contents)
        throws XMLStreamException
    {
        XMLValidationSchemaFactory schF = XMLValidationSchemaFactory.newInstance(XMLValidationSchema.SCHEMA_ID_W3C_SCHEMA);
        return schF.createSchema(new StringReader(contents));
    }

    XMLStreamReader2 getReader(String contents)
        throws XMLStreamException
    {
        XMLInputFactory2 f = getInputFactory();
        setValidating(f, false);
        return constructStreamReader(f, contents);
    }

    void verifyFailure(String xml, XMLValidationSchema schema, String failMsg,
                       String failPhrase)
        throws XMLStreamException
    {
	// default to strict handling:
	verifyFailure(xml, schema, failMsg, failPhrase, true);
    }

    void verifyFailure(String xml, XMLValidationSchema schema, String failMsg,
                       String failPhrase, boolean strict)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getReader(xml);
        sr.validateAgainst(schema);
        try {
            while (sr.hasNext()) {
                int type = sr.next();
            }
            fail("Expected validity exception for "+failMsg);
        } catch (XMLValidationException vex) {
            String origMsg = vex.getMessage();
            String msg = (origMsg == null) ? "" : origMsg.toLowerCase();
            if (msg.indexOf(failPhrase.toLowerCase()) < 0) {
		String actualMsg = "Expected validation exception for "+failMsg+", containing phrase '"+failPhrase+"': got '"+origMsg+"'";
		if (strict) {
		    fail(actualMsg);
		}
		warn("suppressing failure due to MSV bug, failure: '"+actualMsg+"'");
            }
            // should get this specific type; not basic stream exception
        } catch (XMLStreamException sex) {
            fail("Expected XMLValidationException for "+failMsg+"; instead got "+sex.getMessage());
        }
    }
}
