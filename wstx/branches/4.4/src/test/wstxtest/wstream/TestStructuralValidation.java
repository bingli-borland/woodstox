package wstxtest.wstream;

import java.io.*;

import javax.xml.stream.*;

import org.codehaus.stax2.*;

import com.ctc.wstx.api.WstxOutputProperties;

/**
 * This unit test suite verifies that output-side content validation
 * works as expected, when enabled.
 */
public class TestStructuralValidation
    extends BaseWriterTest
{
    /*
    ////////////////////////////////////////////////////
    // Main test methods
    ////////////////////////////////////////////////////
     */

    /**
     * Unit test suite for testing violations of structural checks, when
     * trying to output things in prolog/epilog.
     */
    public void testPrologChecks()
        throws Exception
    {
        for (int i = 0; i <= 2; ++i) { // non-ns, simple-ns, repairing-ns
            for (int j = 0; j < 2; ++j) { // prolog / epilog
                boolean epilog = (j > 0);
                final String prologMsg = epilog ? " in epilog" : " in prolog";
                
                for (int op = 0; op <= 4; ++op) {
                    XMLOutputFactory2 f = getFactory(i, true);
                    StringWriter strw = new StringWriter();
                    XMLStreamWriter2 sw = (XMLStreamWriter2) f.createXMLStreamWriter(strw);
                    String failMsg = null;
                    sw.writeStartDocument();

                    if (epilog) {
                        sw.writeStartElement("root");
                        sw.writeEndElement();
                    }
                    
                    try {
                        switch (op) {
                        case 0: // No non-white space text in prolog/epilog
                            failMsg = "when calling writeCharacters() for non-white space text";
                            sw.writeCharacters("test!");
                            break;
                        case 1: // - "" -
                            failMsg = "when calling writeCharacters() for non-white space text";
                            sw.writeCharacters(new char[] { 't', 'e', 's', 't' }, 0, 4);
                            break;
                        case 2: // No CDATA in prolog/epilog
                            failMsg = "when calling writeCData()";
                            sw.writeCData("cdata");
                        case 3: // - "" -
                            failMsg = "when calling writeCData()";
                            sw.writeCData(new char[] { 't', 'e', 's', 't' }, 0, 4);
                        case 4: // no entity refs in prolog/epilog:
                            failMsg = "when calling writeEntityRef()";
                            sw.writeEntityRef("entity");
                        default:
                            fail("Internal error: illegal test index "+op);
                        }
                    } catch (XMLStreamException sex) {
                        // good
                        continue;
                    } catch (Throwable t) {
                        fail("Expected an XMLStreamException for "+failMsg+prologMsg+"; got "+t);
                    } finally {
                        if (epilog) {
                            sw.close();
                        }
                    }

                    fail("Expected an XMLStreamException for "+failMsg+prologMsg+"; no exception thrown");
                }
            }
        }
    }

    /**
     * Unit test that verifies that root element structural problems (no root,
     * that is, an empty doc; more than one root element) are caught.
     */
    public void testRootElementChecks()
        throws XMLStreamException
    {
        for (int i = 0; i <= 2; ++i) { // non-ns, simple-ns, repairing-ns
            for (int op = 0; op < 2; ++op) {
                XMLOutputFactory2 f = getFactory(i, true);
                StringWriter strw = new StringWriter();
                XMLStreamWriter2 sw = (XMLStreamWriter2) f.createXMLStreamWriter(strw);
                String failMsg = null;
                sw.writeStartDocument();

                try {
                    switch (op) {
                    case 0: // No root element?
                        failMsg = "missing root element";
                        sw.writeEndDocument();
                        break;
                    case 1: // Two root elements...
                        failMsg = "second root element";
                        sw.writeStartElement("root1");
                        sw.writeEndElement();
                        sw.writeStartElement("root1");
                        sw.writeEndElement();
                        break;
                    default:
                        throw new Error("Internal error: illegal test index "+op);
                    }
                } catch (XMLStreamException sex) {
                    // good
                    continue;
                } catch (Throwable t) {
                    fail("Expected an XMLStreamException for "+failMsg);
                }
            }
        }
    }

    // @since 4.3
    public void testRelaxedRootElementChecksNoRoot() throws XMLStreamException
    {
        // First, with factory that is configured:
        XMLOutputFactory2 f = getFactory(0, false);
        StringWriter strw = new StringWriter();
        XMLStreamWriter2 sw = (XMLStreamWriter2) f.createXMLStreamWriter(strw);
        sw.writeStartDocument();
        sw.writeEndDocument();
        sw.close();
        // what to check? Writing of XML declaration is sort of optional...
        assertNotNull(strw.toString());

        // and then dynamically via stream writer
        f = getFactory(0, true);
        assertEquals(Boolean.TRUE, f.getProperty(WstxOutputProperties.P_OUTPUT_VALIDATE_STRUCTURE));
        strw = new StringWriter();
        sw = (XMLStreamWriter2) f.createXMLStreamWriter(strw);
        setValidateStructure(sw, false);        
        
        sw.writeStartDocument();
        sw.writeEndDocument();
        sw.close();
        assertNotNull(strw.toString());
    }

    // @since 4.3
    public void testRelaxedRootElementChecksTwoRoots() throws XMLStreamException
    {
        // First, verify with factory default changed:
        XMLOutputFactory2 f = getFactory(0, false);
        StringWriter strw = new StringWriter();
        XMLStreamWriter2 sw = (XMLStreamWriter2) f.createXMLStreamWriter(strw);
        sw.writeStartDocument();
        sw.writeStartElement("root1");
        sw.writeEndElement();
        sw.writeStartElement("root2");
        sw.writeEndElement();
        sw.writeEndDocument();

        String xml = strw.toString();
        assertNotNull(xml);

        if (xml.indexOf("<root1") < 0 || xml.indexOf("<root2") < 0) {
            fail("Expected to see <root1> and <root2>, didn't: "+xml);
        }

        // and then dynamically via stream writer
        f = getFactory(0, true);
        assertEquals(Boolean.TRUE, f.getProperty(WstxOutputProperties.P_OUTPUT_VALIDATE_STRUCTURE));
        strw = new StringWriter();
        sw = (XMLStreamWriter2) f.createXMLStreamWriter(strw);
        setValidateStructure(sw, false);        
    
        sw.writeStartDocument();
        sw.writeStartElement("root1");
        sw.writeEndElement();
        sw.writeStartElement("root2");
        sw.writeEndElement();
        sw.writeEndDocument();

        xml = strw.toString();
        assertNotNull(xml);

        if (xml.indexOf("<root1") < 0 || xml.indexOf("<root2") < 0) {
            fail("Expected to see <root1> and <root2>, didn't: "+xml);
        }
    }
    
    public void testWriteElementChecks()
        throws XMLStreamException
    {
        /*
        for (int i = 0; i <= 2; ++i) {
            // First, checks for prolog:

            for (int op = 0; op < 2; ++op) {
                XMLOutputFactory2 f = getFactory(i, true);
                StringWriter strw = new StringWriter();
                XMLStreamWriter2 sw = (XMLStreamWriter2) f.createXMLStreamWriter(strw);
                String failMsg = null;

                sw.writeStartDocument();

                // No non-white space text in prolog
                try {
                    switch (op) {
                    case 0:
                        failMsg = "when calling writeCharacters() for non-white space text in prolog";
                        sw.writeCharacters("test!");
                        break;
                    default:
                    }
                } catch (XMLStreamException sex) {
                    // good
                } catch (Throwable t) {
                    fail("Expected an XMLStreamException for illegal comment content (contains '--') in checking + non-fixing mode; got: "+t);
                }
                sw.close();
            }
        }
        */
    }

    /*
    ////////////////////////////////////////////////////
    // Helper methods
    ////////////////////////////////////////////////////
     */

    private XMLOutputFactory2 getFactory(int type, boolean checkStruct)
        throws XMLStreamException
    {
        XMLOutputFactory2 f = getOutputFactory();
        // type 0 -> non-ns, 1 -> ns, non-repairing, 2 -> ns, repairing
        setNamespaceAware(f, type > 0); 
        setRepairing(f, type > 1);
        setValidateStructure(f, checkStruct);
        return f;
    }
}
