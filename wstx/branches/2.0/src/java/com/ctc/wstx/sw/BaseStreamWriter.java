package com.ctc.wstx.sw;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.text.MessageFormat;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

// unfortunate dependencies to StAX events:
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.StartElement;

import org.codehaus.stax2.DTDInfo;
import org.codehaus.stax2.EscapingWriterFactory;
import org.codehaus.stax2.XMLStreamReader2;
import org.codehaus.stax2.XMLStreamWriter2;

import com.ctc.wstx.api.WriterConfig;
import com.ctc.wstx.api.WstxOutputProperties;
import com.ctc.wstx.cfg.ErrorConsts;
import com.ctc.wstx.cfg.OutputConfigFlags;
import com.ctc.wstx.exc.*;
import com.ctc.wstx.io.TextEscaper;
import com.ctc.wstx.io.WstxInputData;
import com.ctc.wstx.sr.StreamReaderImpl;
import com.ctc.wstx.sr.AttributeCollector;
import com.ctc.wstx.sr.InputElementStack;
import com.ctc.wstx.util.StringUtil;

/**
 * Base class for {@link XMLStreamWriter} implementations Woodstox has.
 * Contains partial stream writer implementation, plus utility methods
 * shared by concrete implementation classes. Main reason for such
 * abstract base class is to allow other parts of Woodstox core to refer
 * to any of stream writer implementations in general way.
 */
public abstract class BaseStreamWriter
    implements XMLStreamWriter2,
               XMLStreamConstants, OutputConfigFlags
{
    protected final static int STATE_PROLOG = 1;
    protected final static int STATE_TREE = 2;
    protected final static int STATE_EPILOG = 3;

    protected final static char CHAR_SPACE = ' ';

    protected final static char DEFAULT_QUOTE_CHAR = '"';

    /**
     * Default encoding we assume, if nothing is passed explicitly.
     */
    protected final static String DEFAULT_ENCODING = "UTF-8";

    /*
    ////////////////////////////////////////////////////
    // Output objects
    ////////////////////////////////////////////////////
     */

    /**
     * Actual physical writer to output serialized XML content to
     */
    protected final Writer mWriter;

    /**
     * Lazy-constructed writer that will properly escape characters of text
     * content that need escaping ('&lt;', '&amp;' etc).
     * It will be created
     * when needed for the first time. Instances are usually chained to use
     * {@link #mWriter} for actual outputting.
     */
    protected Writer mTextWriter;

    /**
     * Lazy-constructed writer that will properly escape characters of
     * attribute values
     * that need escaping ('&lt;', '&amp;', '&quot;').
     * It will be created
     * when needed for the first time. Instances are usually chained to use
     * {@link #mWriter} for actual outputting.
     */
    protected Writer mAttrValueWriter;
    
    /*
    ////////////////////////////////////////////////////
    // Per-factory configuration (options, features)
    ////////////////////////////////////////////////////
     */

    protected final WriterConfig mConfig;

    // // // Operating mode: base class needs to know whether
    // // // namespaces are support (for entity/PI target validation)

    protected final boolean mNsAware;

    // // // Specialized configuration flags, extracted from config flags:

    protected final boolean mCfgAutomaticEmptyElems;
    protected final boolean mCfgCDataAsText;
    protected final boolean mCfgCopyDefaultAttrs;

    protected final boolean mCheckStructure;
    protected final boolean mCheckContent;
    protected final boolean mCheckAttr;
    protected final boolean mCheckNames;
    protected final boolean mFixContent;

    /*
    ////////////////////////////////////////////////////
    // Per-writer configuration
    ////////////////////////////////////////////////////
     */

    /**
     * Encoding to use; may be passed from the factory (when
     * a method that defines encoding is used), updated by
     * a call to {@link #writeStartDocument}, or null if
     * neither. Is passed to the escaping writer factory to
     * allow escaping writers to do additional escaping if
     * necessary (like encapsulating non-ascii chars in a doc
     * encoded usig ascii).
     */
    protected String mEncoding;

    /*
    ////////////////////////////////////////////////////
    // State information
    ////////////////////////////////////////////////////
     */

    protected int mState = STATE_PROLOG;

    /**
     * Flag that is set to true first time something has been output.
     * Generally needed to keep track of whether XML declaration
     * (START_DOCUMENT) can be output or not.
     */
    protected boolean mAnyOutput = false;

    /**
     * Flag that is set during time that a start element is "open", ie.
     * START_ELEMENT has been output (and possibly zero or more name
     * space declarations and attributes), before other main-level
     * constructs have been output.
     */
    protected boolean mStartElementOpen = false;

    /**
     * Flag that indicates that current element is an empty element (one
     * that is explicitly defined as one, by calling a method -- NOT one
     * that just happens to be empty).
     * This is needed to know what to do when next non-ns/attr node
     * is output; normally a new context is opened, but for empty
     * elements not.
     */
    protected boolean mEmptyElement = false;

    /*
    ////////////////////////////////////////////////////
    // State needed for efficient copy-through output
    // (copyEventFromReader)
    ////////////////////////////////////////////////////
     */

    /**
     * Reader that was last used for copy-through operation;
     * used in conjunction with the other copy-through state
     * variables.
     */
    protected XMLStreamReader2 mLastReader = null;

    protected StreamReaderImpl mLastReaderImpl = null;

    protected AttributeCollector mAttrCollector = null;

    protected InputElementStack mInputElemStack = null;

    /*
    ////////////////////////////////////////////////////
    // Life-cycle (ctors)
    ////////////////////////////////////////////////////
     */

    protected BaseStreamWriter(Writer w, String enc, WriterConfig cfg)
    {
        mWriter = w;
        mEncoding = enc;
        mConfig = cfg;

        int flags = cfg.getConfigFlags();
        mNsAware = (flags & CFG_ENABLE_NS) != 0;

        mCheckStructure = (flags & CFG_VALIDATE_STRUCTURE) != 0;
        mCheckContent = (flags & CFG_VALIDATE_CONTENT) != 0;
        mCheckAttr = (flags & CFG_VALIDATE_ATTR) != 0;
        mCheckNames = (flags & CFG_VALIDATE_NAMES) != 0;
        mFixContent = (flags & CFG_FIX_CONTENT) != 0;

        mCfgAutomaticEmptyElems = (flags & CFG_AUTOMATIC_EMPTY_ELEMS) != 0;
        mCfgCDataAsText = (flags & CFG_OUTPUT_CDATA_AS_TEXT) != 0;
        mCfgCopyDefaultAttrs = (flags & CFG_COPY_DEFAULT_ATTRS) != 0;
    }

    protected Writer constructAttributeValueWriter()
        throws UnsupportedEncodingException
    {
        EscapingWriterFactory f = mConfig.getAttrValueEscaperFactory();
        String enc = (mEncoding == null) ? DEFAULT_ENCODING : mEncoding;
        if (f == null) {
            return TextEscaper.constructAttrValueWriter(mWriter, enc, '"');
        }
        return f.createEscapingWriterFor(mWriter, enc);
    }

    protected Writer constructTextWriter()
        throws UnsupportedEncodingException
    {
        EscapingWriterFactory f = mConfig.getTextEscaperFactory();
        String enc = (mEncoding == null) ? DEFAULT_ENCODING : mEncoding;
        if (f == null) {
            return TextEscaper.constructTextWriter(mWriter, enc);
        }
        return f.createEscapingWriterFor(mWriter, enc);
    }

    /*
    ////////////////////////////////////////////////////
    // XMLStreamWriter API
    ////////////////////////////////////////////////////
     */

    public void close()
        throws XMLStreamException
    {
        /* 19-Jul-2004, TSa: Hmmh. Let's actually close all still open
         *    elements, starting with currently open start (-> empty)
         *    element, if one exists, and then closing scopes by adding
         *    matching end elements.
         */
        if (mState != STATE_EPILOG) {
            writeEndDocument();
        }
        safeFlushStream();
    }

    public void flush()
        throws XMLStreamException
    {
        /* 27-Apr-2005, TSa: As per discussions on stax-dev list, flush()
         *    is to not only flush the underlying stream, but also to
         *    'close' events like START_ELEMENT (empty or not). This is
         *   a state change, meaning no 'writeAttribute' calls can be
         *   made for the element.
         */
        if (mStartElementOpen) {
            closeStartElement(mEmptyElement);
        }
        safeFlushStream();
    }

    public abstract NamespaceContext getNamespaceContext();

    public abstract String getPrefix(String uri);

    public Object getProperty(String name) {
        return mConfig.getProperty(name);
    }

    public abstract void setDefaultNamespace(String uri)
        throws XMLStreamException;

    public abstract void setNamespaceContext(NamespaceContext context);

    public abstract void setPrefix(String prefix, String uri)
        throws XMLStreamException;

    public abstract void writeAttribute(String localName, String value)
        throws XMLStreamException;
    
    public abstract void writeAttribute(String nsURI, String localName,
                                        String value)
        throws XMLStreamException;

    public abstract void writeAttribute(String prefix, String nsURI,
                                        String localName, String value)
        throws XMLStreamException;

    public void writeCData(String data)
        throws XMLStreamException
    {
        /* 02-Dec-2004, TSa: Maybe the writer is to "re-direct" these
         *   writes as normal text? (sometimes useful to deal with broken
         *   XML parsers, for example)
         */
        if (mCfgCDataAsText) {
            writeCharacters(data);
            return;
        }

        mAnyOutput = true;
        // Need to finish an open start element?
        if (mStartElementOpen) {
            closeStartElement(mEmptyElement);
        }

        // Not legal outside main element tree:
        if (mCheckStructure) {
            if (inPrologOrEpilog()) {
                throw new IllegalStateException(ErrorConsts.WERR_PROLOG_CDATA);
            }
        }

        try {
            if (mCheckContent) {
                int ix = verifyCDataContent(data);
                if (ix >= 0) {
                    // Can we fix it?
                    if (mFixContent) { // Yes we can! (...Bob the Builder...)
                        writeSegmentedCData(data, ix);
                        return;
                    }
                    // nope, let's err out
                    throwOutputError(ErrorConsts.WERR_CDATA_CONTENT, new Integer(ix));
                }
            }
            mWriter.write("<![CDATA[");
            if (data != null) {
                /* 20-Nov-2004, TSa: Should we try to validate content,
                 *   and/or handle embedded end marker?
             */
                mWriter.write(data);
            }
            mWriter.write("]]>");
        } catch (IOException ioe) {
            throw new WstxIOException(ioe);
        }
    }

    public void writeCharacters(char[] text, int start, int len)
        throws XMLStreamException
    {
        /* Not legal outside main element tree, except if it's all
         * white space
         */
        if (mCheckStructure) {
            if (inPrologOrEpilog()) {
                if (!StringUtil.isAllWhitespace(text, start, len)) {
                    throw new IllegalStateException(ErrorConsts.WERR_PROLOG_NONWS_TEXT);
                }
            }
        }

        mAnyOutput = true;
        // Need to finish an open start element?
        if (mStartElementOpen) {
            closeStartElement(mEmptyElement);
        }

        if (len > 0) { // minor optimization
            try {
                if (mTextWriter == null) {
                    mTextWriter = constructTextWriter();
                }
                mTextWriter.write(text, start, len);
            } catch (IOException ioe) {
                throw new WstxIOException(ioe);
            }
        }
    }

    public void writeCharacters(String text)
        throws XMLStreamException
    {
        // Need to validate structure?
        if (mCheckStructure) {
            // Not valid in prolog/epilog, except if it's all white space:
            if (inPrologOrEpilog()) {
                if (!StringUtil.isAllWhitespace(text)) {
                    throw new IllegalStateException(ErrorConsts.WERR_PROLOG_NONWS_TEXT);
                }
            }
        }

        mAnyOutput = true;
        // Need to finish an open start element?
        if (mStartElementOpen) {
            closeStartElement(mEmptyElement);
        }

        // Ok, let's just write it out (if there's any text)
        try {
            if (mTextWriter == null) {
                mTextWriter = constructTextWriter();
            }
            mTextWriter.write(text);
        } catch (IOException ioe) {
            throw new WstxIOException(ioe);
        }
    }

    public void writeComment(String data)
        throws XMLStreamException
    {
        mAnyOutput = true;
        // Need to finish an open start element?
        if (mStartElementOpen) {
            closeStartElement(mEmptyElement);
        }

        try {
	    /* No structural validation needed per se, for comments; they are
	     * allowed anywhere in XML content. However, content may need to
	     * be checked, to see it has no embedded '--'s.
	     */
	    if (mCheckContent) {
		int ix = verifyCommentContent(data);
		if (ix >= 0) {
		    // Can we fix it?
		    if (mFixContent) { // Yes we can! (...Bob the Builder...)
			writeSegmentedComment(data, ix);
			return;
		    }
		    // nope, let's err out
		    throw new XMLStreamException(ErrorConsts.formatMessage(ErrorConsts.WERR_COMMENT_CONTENT, new Integer(ix)));
		}
	    }
            mWriter.write("<!--");
            mWriter.write(data);
            mWriter.write("-->");
        } catch (IOException ioe) {
            throw new WstxIOException(ioe);
        }
    }

    public abstract void writeDefaultNamespace(String nsURI)
        throws XMLStreamException;

    public void writeDTD(String dtd)
        throws XMLStreamException
    {
        verifyWriteDTD();
        try {
            mWriter.write(dtd);
        } catch (IOException ioe) {
            throw new WstxIOException(ioe);
        }
    }

    public abstract void writeEmptyElement(String localName)
        throws XMLStreamException;

    public abstract void writeEmptyElement(String nsURI, String localName)
        throws XMLStreamException;

    public abstract void writeEmptyElement(String prefix, String localName, String nsURI)
        throws XMLStreamException;

    public void writeEndDocument() throws XMLStreamException
    {
        // Is tree still open?
        if (mState != STATE_EPILOG) {
            if (mCheckStructure  && mState == STATE_PROLOG) {
                throw new IllegalStateException("Trying to write END_DOCUMENT when document has no root (ie. trying to output empty document).");
            }
            // 20-Jul-2004, TSa: Need to close the open sub-tree, if it exists...
            // First, do we have an open start element?
            if (mStartElementOpen) {
                closeStartElement(mEmptyElement);
            }
            // Then, one by one, need to close open scopes:
            while (mState != STATE_EPILOG) {
                writeEndElement();
            }
        }
    }

    public abstract void writeEndElement() throws XMLStreamException;

    public void writeEntityRef(String name)
        throws XMLStreamException
    {
        mAnyOutput = true;
        // Need to finish an open start element?
        if (mStartElementOpen) {
            closeStartElement(mEmptyElement);
        }

        // Structurally, need to check we are not in prolog/epilog.
        if (mCheckStructure) {
            if (inPrologOrEpilog()) {
                throw new IllegalStateException("Trying to output an entity reference outside main element tree (in prolog or epilog)");
            }
        }
        if (mCheckNames) {
	    verifyNameValidity(name, mNsAware);
        }

        try {
            mWriter.write('&');
            mWriter.write(name);
            mWriter.write(';');
        } catch (IOException ioe) {
            throw new WstxIOException(ioe);
        }
    }

    public abstract void writeNamespace(String prefix, String nsURI)
        throws XMLStreamException;

    public void writeProcessingInstruction(String target)
        throws XMLStreamException
    {
        writeProcessingInstruction(target, null);
    }

    public void writeProcessingInstruction(String target, String data)
        throws XMLStreamException
    {
        mAnyOutput = true;
        // Need to finish an open start element?
        if (mStartElementOpen) {
            closeStartElement(mEmptyElement);
        }

        // Structurally, PIs are always ok. But content may need to be checked.
        if (mCheckNames) {
	    // As per namespace specs, can not have colon(s)
	    verifyNameValidity(target, mNsAware);
        }
        if (mCheckContent) {
            if (data != null && data.length() > 1) {
                int ix = data.indexOf('?');
                if (ix >= 0) {
                    ix = data.indexOf("?>", ix);
                    if (ix >= 0) {
                        throw new XMLStreamException("Illegal input: processing instruction content has embedded '?>' in it (index "+ix+")");
                    }
                }
            }
        }

        try {
            mWriter.write("<?");
            mWriter.write(target);
            if (data != null && data.length() > 0) {
                /* 11-Nov-2004, TSa: Let's see if it starts with a space:
                 *  if so, no need to add extra space(s).
                 */
                if (data.charAt(0) > CHAR_SPACE) {
                    mWriter.write(' ');
                }
                mWriter.write(data);
            }
            mWriter.write("?>");
        } catch (IOException ioe) {
            throw new WstxIOException(ioe);
        }
    }

    public void writeStartDocument()
        throws XMLStreamException
    {
        /* 03-Feb-2005, TSa: As per StAX 1.0 specs, version should
         *   be "1.0", and encoding "utf-8" (yes, lower case... it's
         *   wrong, but specs mandate it)
         */
        writeStartDocument("utf-8", "1.0");
    }

    public void writeStartDocument(String version)
        throws XMLStreamException
    {
        writeStartDocument(null, version);
    }

    public void writeStartDocument(String encoding, String version)
        throws XMLStreamException
    {
        doWriteStartDocument(version, encoding, null);
    }

    protected void doWriteStartDocument(String version, String encoding,
                                        String standAlone)
        throws XMLStreamException
    {
        /* Not legal to output XML declaration if there has been ANY
         * output prior... that is, if we validate the structure.
         */
        if (mCheckStructure) {
            if (mAnyOutput) {
                throw new IllegalStateException("Can not output XML declaration, after other output has already been done.");
            }
        }

        mAnyOutput = true;

        if (mCheckContent) {
            // !!! 06-May-2004, TSa: Should validate version and encoding?
            if (encoding != null) {
            }
            if (version != null) {
            }
        }

        if (encoding != null && encoding.length() > 0) {
            /* 03-May-2005, TSa: But what about conflicting encoding? Let's
             *   just update encoding, if it wasn't set.
             */
            if (mEncoding == null || mEncoding.length() == 0) {
                mEncoding = encoding;
            }
        }

        try {
            mWriter.write("<?xml version='");
            mWriter.write((version == null || version.length() == 0)
                          ? WstxOutputProperties.DEFAULT_XML_VERSION : version);
            mWriter.write('\'');

            if (encoding != null && encoding.length() > 0) {
                mWriter.write(" encoding='");
                mWriter.write(encoding);
                mWriter.write('\'');
            }
            if (standAlone != null) {
                mWriter.write(" standalone='");
                mWriter.write(standAlone);
                mWriter.write('\'');
            }
            mWriter.write("?>");
        } catch (IOException ioe) {
            throw new WstxIOException(ioe);
        }
    }

    public abstract void writeStartElement(String localName)
        throws XMLStreamException;

    public abstract void writeStartElement(String nsURI, String localName)
        throws XMLStreamException;

    public abstract void writeStartElement(String prefix, String localName,
                                           String nsURI)
        throws XMLStreamException;
    
    /*
    ////////////////////////////////////////////////////
    // XMLStreamWriter2 methods (StAX2)
    ////////////////////////////////////////////////////
     */

    public Object getFeature(String name)
    {
        // !!! TBI
        return null;
    }

    public void setFeature(String name, Object value)
    {
        // !!! TBI
    }

    public void writeCData(char[] c, int start, int len)
        throws XMLStreamException
    {
        /* 02-Dec-2004, TSa: Maybe the writer is to "re-direct" these
         *   writes as normal text? (sometimes useful to deal with broken
         *   XML parsers, for example)
         */
        if (mCfgCDataAsText) {
            writeCharacters(c, start, len);
            return;
        }

        mAnyOutput = true;
        if (mStartElementOpen) { // need to close start element?
            closeStartElement(mEmptyElement);
        }

        // Not legal outside main element tree:
        if (mCheckStructure) {
            if (inPrologOrEpilog()) {
                throw new IllegalStateException(ErrorConsts.WERR_PROLOG_CDATA);
            }
        }

        try {
            if (mCheckContent && c != null) {
                int ix = verifyCDataContent(c, start, len);
                if (ix >= 0) { // problem?
                    if (mFixContent) { // can we fix it?
                        writeSegmentedCData(c, start, len, ix);
                        return;
                    }
                    // nope, let's err out
                    throwOutputError(ErrorConsts.WERR_CDATA_CONTENT, new Integer(ix));
                }
            }
            mWriter.write("<![CDATA[");
            if (c != null) {
                mWriter.write(c, start, len);
            }
            mWriter.write("]]>");
        } catch (IOException ioe) {
            throw new WstxIOException(ioe);
        }
    }

    public void writeDTD(DTDInfo info)
        throws XMLStreamException
    {
        writeDTD(info.getDTDRootName(), info.getDTDSystemId(),
                 info.getDTDPublicId(), info.getDTDInternalSubset());
    }

    public void writeDTD(String rootName, String systemId, String publicId,
                         String internalSubset)
        throws XMLStreamException
    {
        verifyWriteDTD();
        try {
            mWriter.write("<!DOCTYPE ");
            if (mCheckNames) {
                /* 20-Apr-2005, TSa: Can only really verify that it has at most
                 *    one colon in ns-aware mode (and not even that in non-ns
                 *    mode)... so let's just ignore colon count, and check
                 *    that other chars are valid at least
                 */
                verifyNameValidity(rootName, false);
            }
            mWriter.write(rootName);
            if (systemId != null) {
                if (publicId != null) {
                    mWriter.write(" PUBLIC \"");
                    mWriter.write(publicId);
                    mWriter.write("\" \"");
                } else {
                    mWriter.write(" SYSTEM \"");
                }
                mWriter.write(systemId);
                mWriter.write('"');
            }
            // Hmmh. Should we out empty internal subset?
            if (internalSubset != null && internalSubset.length() > 0) {
                mWriter.write(" [");
                mWriter.write(internalSubset);
                mWriter.write(']');
            }
            mWriter.write('>');
        } catch (IOException ioe) {
            throw new WstxIOException(ioe);
        }
    }

    public abstract void writeFullEndElement() throws XMLStreamException;

    public void writeStartDocument(String version, String encoding,
                                   boolean standAlone)
        throws XMLStreamException
    {
        doWriteStartDocument(version, encoding, standAlone ? "yes" : "no");
    }

    public void writeRaw(String text)
        throws XMLStreamException
    {
        mAnyOutput = true;
        if (mStartElementOpen) {
            closeStartElement(mEmptyElement);
        }
        try {
            mWriter.write(text);
        } catch (IOException ioe) {
            throw new WstxIOException(ioe);
        }
    }

    public void writeRaw(char[] text, int offset, int length)
        throws XMLStreamException
    {
        mAnyOutput = true;
        if (mStartElementOpen) {
            closeStartElement(mEmptyElement);
        }
        try {
            mWriter.write(text, offset, length);
        } catch (IOException ioe) {
            throw new WstxIOException(ioe);
        }
    }

    /**
     * Method that essentially copies event that the specified reader has
     * just read.
     *
     * @param sr Stream reader to use for accessing event to copy
     * @param preserveEventData If true, writer is not allowed to change
     *   the state of the reader (so that all the data associated with the
     *   current event has to be preserved); if false, writer is allowed
     *   to use methods that may cause some data to be discarded. Setting
     *   this to false may improve the performance, since it may allow
     *   full no-copy streaming of data, especially textual contents.
     */
    public void copyEventFromReader(XMLStreamReader2 sr, boolean preserveEventData)
        throws XMLStreamException
    {
        try {
// Uncomment for debugging:
//System.err.println("EVENT -> "+sr.getEventType());
            switch (sr.getEventType()) {
                /* Document start/end events:
                 */
            case START_DOCUMENT:
                {
                    String version = sr.getVersion();
                    /* No real declaration? If so, we don't want to output
                     * anything, to replicate as closely as possible the
                     * source document
                     */
                    if (version == null || version.length() == 0) {
                        ; // no output if no real input
                    } else {
                        if (sr.standaloneSet()) {
                            writeStartDocument(sr.getVersion(),
                                               sr.getCharacterEncodingScheme(),
                                               sr.isStandalone());
                        } else {
                            writeStartDocument(sr.getCharacterEncodingScheme(),
                                               sr.getVersion());
                        }
                    }
                }
                return;
                
            case END_DOCUMENT:
                writeEndDocument();
                return;
                
                /* Element start/end events:
                 */
            case START_ELEMENT:
                {
                    if (sr != mLastReader) {
                        mLastReader = sr;
                        /* !!! Should probably work with non-Woodstox stream
                         * readers too... but that's not implemented yet
                         */
                        if (!(sr instanceof StreamReaderImpl)) {
                            throw new XMLStreamException("Can not yet copy START_ELEMENT events from non-Woodstox stream readers (class "+sr.getClass()+")");
                        }
                        mLastReaderImpl = (StreamReaderImpl) sr;
                        mAttrCollector = mLastReaderImpl.getAttributeCollector();
                        mInputElemStack = mLastReaderImpl.getInputElementStack();
                    }
                    copyStartElement(mInputElemStack, mAttrCollector);
                }
                return;

            case END_ELEMENT:
                writeEndElement();
                return;
                
                /* Textual events:
                 */
                
            case CDATA:
                // First; is this to be changed to 'normal' text output?
                if (!mCfgCDataAsText) {
                    mAnyOutput = true;
                    // Need to finish an open start element?
                    if (mStartElementOpen) {
                        closeStartElement(mEmptyElement);
                    }

                    // Not legal outside main element tree:
                    if (mCheckStructure) {
                        if (inPrologOrEpilog()) {
                            throw new IllegalStateException(ErrorConsts.WERR_PROLOG_CDATA);
                        }
                    }
                    /* Note: no need to check content, since reader is assumed
                     * to have verified it to be valid XML.
                     */

                    /* No encoding necessary for CDATA... but we do need start
                     * and end markers
                     */
                    mWriter.write("<![CDATA[");
                    sr.getText(mWriter, preserveEventData);
                    mWriter.write("]]>");
                    return;
                }
                // fall down if it is to be converted...
                
            case SPACE:
                {
                    mAnyOutput = true;
                    // Need to finish an open start element?
                    if (mStartElementOpen) {
                        closeStartElement(mEmptyElement);
                    }
                    // No need to use mTextWriter, should be pure space
                    sr.getText(mWriter, preserveEventData);
                }
                return;

            case CHARACTERS:
                {
                    /* Let's just assume content is fine... not 100% reliably
                     * true, but usually is (not true if input had a root
                     * element surrounding text, but omitted for output)
                     */
                    mAnyOutput = true;
                    // Need to finish an open start element?
                    if (mStartElementOpen) {
                        closeStartElement(mEmptyElement);
                    }

                    /* Need to pass mTextWriter, to make sure encoding is done
                     * properly; but no start/end markers are needed
                     */
                    if (mTextWriter == null) {
                        mTextWriter = constructTextWriter();
                    }
                    sr.getText(mTextWriter, preserveEventData);
                }
                return;
                
            case COMMENT:
                {
                    mAnyOutput = true;
                    if (mStartElementOpen) {
                        closeStartElement(mEmptyElement);
                    }
                    /* No need to check for content (embedded '--'); reader
                     * is assumed to have verified it's ok (otherwise should
                     * have thrown an exception for non-well-formed XML)
                     */
                    mWriter.write("<!--");
                    sr.getText(mWriter, preserveEventData);
                    mWriter.write("-->");
                }
                return;

            case PROCESSING_INSTRUCTION:
                {
                    // No streaming alternative for PI (yet)?
                    String target = sr.getPITarget();
                    String data = sr.getPIData();
                    if (data == null) {
                        writeProcessingInstruction(target);
                    } else {
                        writeProcessingInstruction(target, data);
                    }
                }
                return;
                
            case DTD:
                {
                    DTDInfo info = sr.getDTDInfo();
                    if (info == null) {
                        /* Hmmmh. It is legal for this to happen, for
                         * non-DTD-aware readers. But what is the right
                         * thing to do here? DOCTYPE can not be output
                         * without the root name, and if we can not access
                         * it, there's no way to write a valid thing.
                         * So, let's just throw an exception.
                         */
                        throw new IllegalArgumentException("Current state DOCTYPE, but not DTDInfo Object returned -- reader doesn't support DTDs?");
                    }
                    /* Could optimize this a bit (stream the int. subset
                     * possible), but it's never going to occur more than
                     * once per document, so it's probably not much of a
                     * bottleneck, ever
                     */
                    writeDTD(info);
                }
                return;
                
            case ENTITY_REFERENCE:
                writeEntityRef(sr.getLocalName());
                return;
                
                /* Weird ones..
                 */
            case ATTRIBUTE:
            case NAMESPACE:
            case ENTITY_DECLARATION:
            case NOTATION_DECLARATION:
                // Let's just fall back to throw the exception
            }
        } catch (IOException ioe) {
            throw new WstxIOException(ioe);
        }

        throw new XMLStreamException("Unrecognized event type ("
                                     +sr.getEventType()+"); not sure how to copy");
    }

    /*
    ////////////////////////////////////////////////////
    // Package methods (ie not part of public API)
    ////////////////////////////////////////////////////
     */

    /**
     * Method needed by {@link com.ctc.wstx.evt.WstxEventWriter}, when it
     * needs/wants to
     * do direct output, without calling methods in this class (not often).
     */
    public Writer getWriter() {
        return mWriter;
    }

    /**
     * Convenience method needed by {@link com.ctc.wstx.evt.WstxEventWriter}, to use when
     * writing a start element, and possibly its attributes and namespace
     * declarations.
     */
    public abstract void writeStartElement(StartElement elem)
        throws XMLStreamException;

    /**
     * Method called by {@link com.ctc.wstx.evt.WstxEventWriter} (instead of the version
     * that takes no argument), so that we can verify it does match the
     * start element, if necessary
     */
    public abstract void writeEndElement(QName name)
        throws XMLStreamException;

    /**
     * Method called by {@link com.ctc.wstx.evt.WstxEventWriter} (instead of
     * more generic
     * text output methods), so that we can verify (if necessary) that
     * this character output type is legal in this context. Specifically,
     * it's not acceptable to add non-whitespace content outside root
     * element (in prolog/epilog).
     */

    public void writeCharacters(Characters ch)
        throws XMLStreamException
    {
        /* Not legal outside main element tree, except if it's all
         * white space
         */
        if (mCheckStructure) {
            if (inPrologOrEpilog()) {
                if (!ch.isIgnorableWhiteSpace() && !ch.isWhiteSpace()) {
                    throw new IllegalStateException(ErrorConsts.WERR_PROLOG_NONWS_TEXT);
                }
            }
        }

        // Need to finish an open start element?
        if (mStartElementOpen) {
            closeStartElement(mEmptyElement);
        }

        // Ok, let's just write it out:
        try {
            if (mTextWriter == null) {
                mTextWriter = constructTextWriter();
            }
            mTextWriter.write(ch.getData());
        } catch (IOException ioe) {
            throw new WstxIOException(ioe);
        }
    }

    /**
     * Method called to close an open start element, when another
     * main-level element (not namespace declaration or attribute)
     * is being output; except for end element which is handled differently.
     */
    protected abstract void closeStartElement(boolean emptyElem)
        throws XMLStreamException;

    public boolean inPrologOrEpilog() {
        return (mState != STATE_TREE);
    }

    /**
     * Implementation-dependant method called to fully copy START_ELEMENT
     * event that the passed-in stream reader points to
     */
    public abstract void copyStartElement(InputElementStack elemStack,
                                          AttributeCollector attrCollector)
        throws IOException, XMLStreamException;

    public void flushStream()
        throws IOException
    {
        mWriter.flush();
    }

    public void safeFlushStream()
        throws XMLStreamException
    {
        try {
            flushStream();
        } catch (IOException ie) {
            throw new WstxIOException(ie);
        }
    }

    /*
    ////////////////////////////////////////////////////
    // Package methods, validation
    ////////////////////////////////////////////////////
     */

    /**
     * Method called to verify that the name is a legal XML name.
     */
    public static void verifyNameValidity(String name, boolean nsAware)
    {
	/* No empty names... caller must have dealt with optional arguments
	 * prior to calling this method
	 */
	if (name == null || name.length() == 0) {
	    throwIllegalArg(ErrorConsts.WERR_NAME_EMPTY);
	}
	char c = name.charAt(0);

	if (c == ':' && !nsAware) { // ok, but only in non-ns mode
	    ;
	} else {
	    if (!WstxInputData.is11NameStartChar(c)) {		
		throwIllegalArg(ErrorConsts.WERR_NAME_ILLEGAL_FIRST_CHAR,
				WstxInputData.getCharDesc(c));
	    }
	}

	for (int i = 1, len = name.length(); i < len; ++i) {
            c = name.charAt(i);
            if (c == ':' && !nsAware) {
                ; // is ok, but has to be explicitly checked...
            } else if (!WstxInputData.is11NameChar(c)) {
		throwIllegalArg(ErrorConsts.WERR_NAME_ILLEGAL_CHAR,
				WstxInputData.getCharDesc(c));
	    }
	}
    }

    protected void verifyWriteDTD()
        throws XMLStreamException
    {
        // 20-Nov-2004, TSa: can check that we are in prolog
        if (mCheckStructure) {
            if (mState != STATE_PROLOG) {
                throw new XMLStreamException("Can not write DOCTYPE declaration (DTD) when not in prolog any more (state "+mState+"; start element(s) written)");
            }
        }
    }

    /**
     * @return Index at which a problem was found, if any; -1 if there's
     *   no problem.
     */
    protected int verifyCDataContent(String content)
    {
        if (content != null && content.length() >= 3) {
            int ix = content.indexOf(']');
            if (ix >= 0) {
                return content.indexOf("]]>", ix);
            }
        }
        return -1;
    }

    protected int verifyCDataContent(char[] c, int start, int end)
    {
        if (c != null) {
            start += 2;
            /* Let's do simple optimization for search...
             * (bayer-moore search algorithm)
             */
            while (start < end) {
                char ch = c[start];
                if (ch == ']') {
                    ++start; // let's just move by one in this case
                    continue;
                }
                if (ch == '>') { // match?
                    if (c[start-1] == ']' 
                        && c[start-2] == ']') {
                        return start-2;
                    }
                }
                start += 2;
            }
        }
        return -1;
    }
    
    protected int verifyCommentContent(String content)
    {
        int ix = content.indexOf('-');
        if (ix >= 0) {
            /* actually, it's illegal to just end with '-' too, since 
             * that would cause invalid end marker '--->'
             */
            if (ix < (content.length() - 1)) {
                ix = content.indexOf("--", ix);
            }
        }
        return ix;
    }

    protected void writeSegmentedCData(String content, int index)
        throws IOException
    {
        /* It's actually fairly easy, just split "]]>" into 2 pieces;
         * for each ']]>'; first one containing "]]", second one ">"
         * (as long as necessary)
         */
        int start = 0;
        while (index >= 0) {
            mWriter.write("<![CDATA[");
            mWriter.write(content, start, (index+2) - start);
            mWriter.write("]]>");
            start = index+2;
            index = content.indexOf("]]>", start);
        }
        // Ok, then the last segment
        mWriter.write("<![CDATA[");
        mWriter.write(content, start, content.length()-start);
        mWriter.write("]]>");
    }

    protected void writeSegmentedCData(char[] c, int start, int len, int index)
        throws IOException
    {
        int end = start + len;
        while (index >= 0) {
            mWriter.write("<![CDATA[");
            mWriter.write(c, start, (index+2) - start);
            mWriter.write("]]>");
            start = index+2;
            index = verifyCDataContent(c, start, end);
        }
        // Ok, then the last segment
        mWriter.write("<![CDATA[");
        mWriter.write(c, start, end-start);
        mWriter.write("]]>");
    }

    protected void writeSegmentedComment(String content, int index)
        throws IOException
    {
        int len = content.length();
        // First the special case (last char is hyphen):
        if (index == (len-1)) {
            mWriter.write("<!--");
            mWriter.write(content);
            // we just need to inject one space in there
            mWriter.write(" -->");
            return;
        }
        
        /* Fixing comments is more difficult than that of CDATA segments';
         * this because CDATA can still contain embedded ']]'s, but
         * comment neither allows '--' nor ending with '-->'; which means
         * that it's impossible to just split segments. Instead we'll do
         * something more intrusive, and embed single spaces between all
         * '--' character pairs... it's intrusive, but comments are not
         * supposed to contain any data, so that should be fine (plus
         * at least result is valid, unlike contents as is)
         */
        int start = 0;
        while (index >= 0) {
            mWriter.write("<!--");
            // first, content prior to '--' and the first hyphen
            mWriter.write(content, start, (index+1) - start);
            // and an obligatory trailing space to split double-hyphen
            mWriter.write(' ');
            // still need to handle rest of consequtive double'-'s if any
            start = index+1;
            index = content.indexOf("--", start);
        }
        // Ok, then the last segment
        mWriter.write(content, start, len-start);
        // ends with a hyphen? that needs to be fixed, too
        if (content.charAt(len-1) == '-') {
            mWriter.write(' ');
        }
        mWriter.write("]]>");
    }

    /*
    ////////////////////////////////////////////////////
    // Package methods, logging, exception handling
    ////////////////////////////////////////////////////
     */

    protected static void throwOutputError(String msg)
        throws XMLStreamException
    {
        throw new XMLStreamException(msg);
    }

    protected static void throwOutputError(String format, Object arg)
        throws XMLStreamException
    {
        String msg = MessageFormat.format(format, new Object[] { arg });
        throw new XMLStreamException(msg);
    }

    protected static void throwFromIOE(IOException ioe)
        throws XMLStreamException
    {
        throw new WstxIOException(ioe);
    }

    protected static void throwIllegalArg(String msg)
        throws IllegalArgumentException
    {
        throw new IllegalArgumentException(msg);
    }

    protected static void throwIllegalArg(String format, Object arg)
        throws IllegalArgumentException
    {
        String msg = MessageFormat.format(format, new Object[] { arg });
        throw new IllegalArgumentException(msg);
    }
}
