package org.codehaus.staxbind.dbconv;

import org.codehaus.staxbind.std.StdJaxbConverter;

public final class JaxbDriver
    extends DbconvDriver
{
    public JaxbDriver() throws Exception
    {
        super(new StdJaxbConverter(DbData.class));
    }

    @Override
    public void initializeDriver() {
        ((StdJaxbConverter) _converter).initStaxFactories
            (getParam("javax.xml.stream.XMLInputFactory"),
             getParam("javax.xml.stream.XMLOutputFactory")
             );
    }
}
