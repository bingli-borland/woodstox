/* Woodstox XML processor
 *
 * Copyright (c) 2004 Tatu Saloranta, tatu.saloranta@iki.fi
 *
 * Licensed under the License specified in the file LICENSE which is
 * included with the source code.
 * You may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ctc.wstx.stax;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.HashMap;

import javax.xml.transform.Result;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamResult;

import javax.xml.stream.*;

import com.ctc.wstx.api.WriterConfig;
import com.ctc.wstx.cfg.OutputConfigFlags;
import com.ctc.wstx.sw.BaseStreamWriter;
import com.ctc.wstx.sw.NonNsStreamWriter;
import com.ctc.wstx.sw.RepairingNsStreamWriter;
import com.ctc.wstx.sw.SimpleNsStreamWriter;
import com.ctc.wstx.util.ArgUtil;

/**
 * Minimalistic input factory, which implements the suggested J2ME
 * subset of {@link javax.xml.stream.XMLOutputFactory} API: basically
 * just the cursor-based iteration, and classes it needs.
 *<p>
 * Unfortunately, the way StAX 1.0 is defined, this class can NOT be
 * the base class of the full input factory, without getting references
 * to most of StAX event classes. It does however have lots of shared
 * (cut'n pasted code) with {@link com.ctc.wstx.stax.WstxOutputFactory}.
 * Hopefully in future this problem can be resolved.
 */
public final class MinimalOutputFactory
    //extends XMLOutputFactory
    implements OutputConfigFlags
{
    /**
     * Flag used to distinguish "real" minimal implementations and
     * extending non-minimal ones (currently there's such distinction
     * for input factories, for minimal <= validating <= event-based,
     * but not for ouput)
     */
    protected final boolean mIsMinimal;

    /*
    /////////////////////////////////////////////////////
    // Actual storage of configuration settings
    /////////////////////////////////////////////////////
     */

    protected final WriterConfig mConfig;

    /*
    /////////////////////////////////////////////////////
    // Life-cycle
    /////////////////////////////////////////////////////
     */

    protected MinimalOutputFactory(boolean isMinimal) {
        mIsMinimal = isMinimal;
        mConfig = WriterConfig.createJ2MEDefaults();
    }

    /**
     * Need to add this method, since we have no base class to do it...
     */
    public static MinimalOutputFactory newMinimalInstance() {
        return new MinimalOutputFactory(true);
    }

    /*
    /////////////////////////////////////////////////////
    // XMLOutputFactory API
    /////////////////////////////////////////////////////
     */

    //public XMLEventWriter createXMLEventWriter(OutputStream out);

    public XMLStreamWriter createXMLStreamWriter(OutputStream out)
        throws XMLStreamException
    {
        return createSW(out, null, null);
    }

    public XMLStreamWriter createXMLStreamWriter(OutputStream out, String enc)
        throws XMLStreamException
    {
        return createSW(out, null, enc);
    }

    public XMLStreamWriter createXMLStreamWriter(javax.xml.transform.Result result)
        throws XMLStreamException
    {
        return createSW(result);
    }

    public XMLStreamWriter createXMLStreamWriter(Writer w)
        throws XMLStreamException
    {
        return createSW(null, w, null);    
    }

    public XMLStreamWriter createXMLStreamWriter(Writer w, String enc)
        throws XMLStreamException
    {
        return createSW(null, w, enc);
    }
    
    public Object getProperty(String name)
    {
        return mConfig.getProperty(name);
    }
    
    public boolean isPropertySupported(String name) {
        return mConfig.isPropertySupported(name);
    }
    
    public void setProperty(String name, Object value)
    {
        mConfig.setProperty(name, value);
    }

    /*
    /////////////////////////////////////////
    // Woodstox-specific configuration access
    /////////////////////////////////////////
     */

    public WriterConfig getConfig() {
        return mConfig;
    }

    /*
    /////////////////////////////////////////
    // Internal methods:
    /////////////////////////////////////////
     */

    /**
     * Factory method used internally; needs to take care of passing
     * proper settings to stream writer.
     */
    private BaseStreamWriter createSW(OutputStream out, Writer w, String enc)
        throws XMLStreamException
    {
        /* 03-May-2005, TSa: For now, let's just use JDK-provided encoders...
         *   for 3.x, maybe we can try to provide optimized ones -- these
         *   would mostly help with quoted content (can do quoting along
         *   with encoding).
         */
        if (w == null) {
            try {
                if (enc == null) {
                    w = new OutputStreamWriter(out);
                } else {
                    w = new OutputStreamWriter(out, enc);
                }
            } catch (UnsupportedEncodingException ex) {
                throw new XMLStreamException(ex);
            }
        }

        if (mConfig.willSupportNamespaces()) {
	    if (mConfig.automaticNamespacesEnabled()) {
		return new RepairingNsStreamWriter(w, enc, mConfig);
	    }
            return new SimpleNsStreamWriter(w, enc, mConfig);
        }
        return new NonNsStreamWriter(w, enc, mConfig);
    }

    private BaseStreamWriter createSW(Result res)
        throws XMLStreamException
    {
        if (res instanceof StreamResult) {
            StreamResult sr = (StreamResult) res;
            Writer w = sr.getWriter();
            if (w == null) {
                OutputStream out = sr.getOutputStream();
                if (out == null) {
                    throw new XMLStreamException("Can not create StAX writer for a StreamResult -- neither writer nor output stream was set.");
                }
                // ... any way to define encoding?
                return createSW(out, null, null);
            }
            return createSW(null, w, null);
        }

        if (res instanceof SAXResult) {
            SAXResult sr = (SAXResult) res;
            // !!! TBI
            throw new XMLStreamException("Can not create a STaX writer for a SAXResult -- not (yet) implemented.");
        }

        if (res instanceof DOMResult) {
            DOMResult sr = (DOMResult) res;
            // !!! TBI
            throw new XMLStreamException("Can not create a STaX writer for a DOMResult -- not (yet) implemented.");
        }

        throw new IllegalArgumentException("Can not instantiate a writer for XML result type "+res.getClass()+" (unknown type)");
    }
}
