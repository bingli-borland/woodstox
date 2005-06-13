package org.codehaus.staxmate.sw;

/**
 * Namespace that is global and shared for all {@link SMOutputContext})s
 * (~= XML documents or sub-trees). This includes the
 * pre-defined namespaces (ones with "xml" and "xmlns" prefixes as well
 * as the default "empty"/missing namespace, one bound to "" if no explicit
 * declaration is made).
 */
public final class SMGlobalNamespace
    extends SMNamespace
{
    /**
     * Prefix this namespace is (permanently) bound to.
     */
    protected final String mPrefix;

    protected SMGlobalNamespace(String uri, String prefix)
    {
        super(uri);
        mPrefix = prefix;
    }
    
    /*
    ///////////////////////////////////
    // Abstract method implementations
    ///////////////////////////////////
    */
    
    public String getPreferredPrefix() {
        return mPrefix;
    }

    public String getBoundPrefix() {
        return mPrefix;
    }

    public String getLastBoundPrefix() {
        return mPrefix;
    }

    public boolean prefersDefaultNs() {
        return false; // doesn't matter
    }

    public void prefersDefaultNs(boolean state) {
        ; // NOP
    }

    protected boolean isValidIn(SMOutputContext ctxt) {
        // global namespaces are always valid for all contexts
        return true;
    }
    
    /**
     * Global namespaces should never be bound/unbound, so if this
     * gets called, an exception will be thrown (but note that this
     * being an 'internal' method, this is more like an assertion).
     */
    protected void bindAs(String prefix) {
        throw new IllegalArgumentException("Global namespace (prefix '"
                                           +mPrefix+"') can not be bound to a different prefix");
    }

    protected void bindPermanentlyAs(String prefix) {
        bindAs(prefix); // to throw the error
    }

    protected void unbind() {
        throw new IllegalArgumentException("Global namespace (prefix '"
                                           +mPrefix+"') can not be unbound");
    }
}