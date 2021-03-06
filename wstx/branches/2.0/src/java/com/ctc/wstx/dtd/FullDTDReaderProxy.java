package com.ctc.wstx.dtd;

import java.io.Writer;
import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.io.WstxInputSource;
import com.ctc.wstx.sr.StreamScanner;

/**
 * Proxy implementation that will act as a full-featured DTD reader. 
 * Used by stream readers that do support full DTD handling.
 */
public final class FullDTDReaderProxy
    extends DTDReaderProxy
{
    final static FullDTDReaderProxy sInstance = new FullDTDReaderProxy();

    /*
    ////////////////////////////////////////////////
    // Life-cycle
    ////////////////////////////////////////////////
     */

    private FullDTDReaderProxy() { }

    public static FullDTDReaderProxy getInstance() {
        return sInstance;
    }

    /*
    ////////////////////////////////////////////////
    // Public API
    ////////////////////////////////////////////////
     */

    /**
     * Method called to read in the internal subset definition.
     */
    public DTDSubset readInternalSubset(StreamScanner master, WstxInputSource input,
                                        ReaderConfig cfg)
        throws IOException, XMLStreamException
    {
        return FullDTDReader.readInternalSubset(master, input, cfg);
    }
    
    /**
     * Method called to read in the external subset definition.
     */
    public DTDSubset readExternalSubset
        (StreamScanner master, WstxInputSource src, ReaderConfig cfg,
         DTDSubset intSubset)
        throws IOException, XMLStreamException
    {
        return FullDTDReader.readExternalSubset(master, src, cfg, intSubset);
    }
    
    /**
     * Method similar to {@link #readInternalSubset}, in that it skims
     * through structure of internal subset, but without doing any sort
     * of validation, or parsing of contents. Method may still throw an
     * exception, if skipping causes EOF or there's an I/O problem.
     */
    public void skipInternalSubset(StreamScanner master, WstxInputSource input,
                                   ReaderConfig cfg)
        throws IOException, XMLStreamException
    {
        MinimalDTDReader.skipInternalSubset(master, input, cfg);
    }
}

