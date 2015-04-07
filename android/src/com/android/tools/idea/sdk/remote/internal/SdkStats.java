/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea.sdk.remote.internal;

import com.android.annotations.VisibleForTesting;
import com.android.annotations.VisibleForTesting.Visibility;
import com.android.io.NonClosingInputStream;
import com.android.io.NonClosingInputStream.CloseBehavior;
import com.android.tools.idea.sdk.remote.internal.DownloadCache;
import com.android.tools.idea.sdk.remote.internal.ITaskMonitor;
import com.android.sdklib.repository.SdkStatsConstants;
import com.android.utils.SparseArray;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLKeyException;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;


/**
 * Retrieves stats on platforms.
 * <p/>
 * This returns information stored on the repository in a different XML file
 * and isn't directly tied to the existence of the listed platforms.
 */
public class SdkStats {

    public static class PlatformStatBase {
        private final int mApiLevel;
        private final String mVersionName;
        private final String mCodeName;
        private final float mShare;

        public PlatformStatBase(int apiLevel,
                String versionName,
                String codeName,
                float share) {
            mApiLevel = apiLevel;
            mVersionName = versionName;
            mCodeName = codeName;
            mShare = share;
        }

        /** The Android API Level for the platform. An int > 0. */
        public int getApiLevel() {
            return mApiLevel;
        }

        /** The official codename for this platform, for example "Cupcake". */
        public String getCodeName() {
            return mCodeName;
        }

        /** The official version name of this platform, for example "Android 1.5". */
        public String getVersionName() {
            return mVersionName;
        }

        /** An approximate share percentage of this platform and all the
         *  platforms of lower API level. */
        public float getShare() {
            return mShare;
        }

        /** Returns a string representation of this object, for debugging purposes. */
        @Override
        public String toString() {
            return String.format("api=%d, code=%s, vers=%s, share=%.1f%%",  //$NON-NLS-1$
                    mApiLevel, mCodeName, mVersionName, mShare);
        }
    }

    public static class PlatformStat extends PlatformStatBase {
        private final float mAccumShare;

        public PlatformStat(int apiLevel,
                String versionName,
                String codeName,
                float share,
                float accumShare) {
            super(apiLevel, versionName, codeName, share);
            mAccumShare = accumShare;
        }

        public PlatformStat(PlatformStatBase base, float accumShare) {
            super(base.getApiLevel(),
                    base.getVersionName(),
                    base.getCodeName(),
                    base.getShare());
            mAccumShare = accumShare;
        }

        /** The accumulated approximate share percentage of that platform. */
        public float getAccumShare() {
            return mAccumShare;
        }

        /** Returns a string representation of this object, for debugging purposes. */
        @Override
        public String toString() {
            return String.format("<Stat %s, accum=%.1f%%>", super.toString(), mAccumShare);
        }
    }

    private final SparseArray<PlatformStat> mStats = new SparseArray<com.android.tools.idea.sdk.remote.internal.SdkStats.PlatformStat>();

    public SdkStats() {
    }

    public SparseArray<PlatformStat> getStats() {
        return mStats;
    }

    public void load(com.android.tools.idea.sdk.remote.internal.DownloadCache cache, boolean forceHttp, ITaskMonitor monitor) {

        String url = SdkStatsConstants.URL_STATS;

        if (forceHttp) {
            url = url.replaceAll("https://", "http://");  //$NON-NLS-1$ //$NON-NLS-2$
        }

        monitor.setProgressMax(5);
        monitor.setDescription("Fetching %1$s", url);
        monitor.incProgress(1);

        Exception[] exception = new Exception[] { null };
        Boolean[] validatorFound = new Boolean[] { Boolean.FALSE };
        String[] validationError = new String[] { null };
        Document validatedDoc = null;
        String validatedUri = null;

        InputStream xml = fetchXmlUrl(url, cache, monitor.createSubMonitor(1), exception);

        if (xml != null) {
            monitor.setDescription("Validate XML");

            // Explore the XML to find the potential XML schema version
            int version = getXmlSchemaVersion(xml);

            if (version >= 1 && version <= SdkStatsConstants.NS_LATEST_VERSION) {
                // This should be a version we can handle. Try to validate it
                // and report any error as invalid XML syntax,

                String uri = validateXml(xml, url, version, validationError, validatorFound);
                if (uri != null) {
                    // Validation was successful
                    validatedDoc = getDocument(xml, monitor);
                    validatedUri = uri;

                }
            } else if (version > SdkStatsConstants.NS_LATEST_VERSION) {
                // The schema used is more recent than what is supported by this tool.
                // We don't have an upgrade-path support yet, so simply ignore the document.
                closeStream(xml);
                return;
            }
        }

        // If any exception was handled during the URL fetch, display it now.
        if (exception[0] != null) {
            String reason = null;
            if (exception[0] instanceof FileNotFoundException) {
                // FNF has no useful getMessage, so we need to special handle it.
                reason = "File not found";
            } else if (exception[0] instanceof UnknownHostException &&
                    exception[0].getMessage() != null) {
                // This has no useful getMessage yet could really use one
                reason = String.format("Unknown Host %1$s", exception[0].getMessage());
            } else if (exception[0] instanceof SSLKeyException) {
                // That's a common error and we have a pref for it.
                reason = "HTTPS SSL error. You might want to force download through HTTP in the settings.";
            } else if (exception[0].getMessage() != null) {
                reason = exception[0].getMessage();
            } else {
                // We don't know what's wrong. Let's give the exception class at least.
                reason = String.format("Unknown (%1$s)", exception[0].getClass().getName());
            }

            monitor.logError("Failed to fetch URL %1$s, reason: %2$s", url, reason);
        }

        if (validationError[0] != null) {
            monitor.logError("%s", validationError[0]);  //$NON-NLS-1$
        }

        // Stop here if we failed to validate the XML. We don't want to load it.
        if (validatedDoc == null) {
            closeStream(xml);
            return;
        }

        monitor.incProgress(1);

        if (xml != null) {
            monitor.setDescription("Parse XML");
            monitor.incProgress(1);
            parseStatsDocument(validatedDoc, validatedUri, monitor);
        }

        // done
        monitor.incProgress(1);
        closeStream(xml);
    }

    /**
     * Fetches the document at the given URL and returns it as a stream. Returns
     * null if anything wrong happens.
     *
     * @param urlString The URL to load, as a string.
     * @param monitor {@link ITaskMonitor} related to this URL.
     * @param outException If non null, where to store any exception that
     *            happens during the fetch.
     * @see com.android.tools.idea.sdk.remote.internal.UrlOpener UrlOpener, which handles all URL logic.
     */
    private InputStream fetchXmlUrl(String urlString,
            DownloadCache cache,
            ITaskMonitor monitor,
            Exception[] outException) {
        try {
            InputStream xml = cache.openCachedUrl(urlString, monitor);
            if (xml != null) {
                xml.mark(500000);
                xml = new NonClosingInputStream(xml);
                ((NonClosingInputStream) xml).setCloseBehavior(CloseBehavior.RESET);
            }
            return xml;
        } catch (Exception e) {
            if (outException != null) {
                outException[0] = e;
            }
        }

        return null;
    }

    /**
     * Closes the stream, ignore any exception from InputStream.close().
     * If the stream is a NonClosingInputStream, sets it to CloseBehavior.CLOSE first.
     */
    private void closeStream(InputStream is) {
        if (is != null) {
            if (is instanceof NonClosingInputStream) {
                ((NonClosingInputStream) is).setCloseBehavior(CloseBehavior.CLOSE);
            }
            try {
                is.close();
            } catch (IOException ignore) {}
        }
    }

    /**
     * Manually parses the root element of the XML to extract the schema version
     * at the end of the xmlns:sdk="http://schemas.android.com/sdk/android/addons-list/$N"
     * declaration.
     *
     * @return 1..{@link SdkStatsConstants#NS_LATEST_VERSION} for a valid schema version
     *         or 0 if no schema could be found.
     */
    @VisibleForTesting(visibility=Visibility.PRIVATE)
    protected int getXmlSchemaVersion(InputStream xml) {
        if (xml == null) {
            return 0;
        }

        // Get an XML document
        Document doc = null;
        try {
            xml.reset();

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setIgnoringComments(false);
            factory.setValidating(false);

            // Parse the old document using a non namespace aware builder
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();

            // We don't want the default handler which prints errors to stderr.
            builder.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException e) throws SAXException {
                // pass
                }
                @Override
                public void fatalError(SAXParseException e) throws SAXException {
                    throw e;
                }
                @Override
                public void error(SAXParseException e) throws SAXException {
                    throw e;
                }
            });

            doc = builder.parse(xml);

            // Prepare a new document using a namespace aware builder
            factory.setNamespaceAware(true);
            builder = factory.newDocumentBuilder();

        } catch (Exception e) {
            // Failed to reset XML stream
            // Failed to get builder factor
            // Failed to create XML document builder
            // Failed to parse XML document
            // Failed to read XML document
        }

        if (doc == null) {
            return 0;
        }

        // Check the root element is an XML with at least the following properties:
        // <sdk:sdk-addons-list
        //    xmlns:sdk="http://schemas.android.com/sdk/android/addons-list/$N">
        //
        // Note that we don't have namespace support enabled, we just do it manually.

        Pattern nsPattern = Pattern.compile(SdkStatsConstants.NS_PATTERN);

        String prefix = null;
        for (Node child = doc.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                prefix = null;
                String name = child.getNodeName();
                int pos = name.indexOf(':');
                if (pos > 0 && pos < name.length() - 1) {
                    prefix = name.substring(0, pos);
                    name = name.substring(pos + 1);
                }
                if (SdkStatsConstants.NODE_SDK_STATS.equals(name)) {
                    NamedNodeMap attrs = child.getAttributes();
                    String xmlns = "xmlns";                                         //$NON-NLS-1$
                    if (prefix != null) {
                        xmlns += ":" + prefix;                                      //$NON-NLS-1$
                    }
                    Node attr = attrs.getNamedItem(xmlns);
                    if (attr != null) {
                        String uri = attr.getNodeValue();
                        if (uri != null) {
                            Matcher m = nsPattern.matcher(uri);
                            if (m.matches()) {
                                String version = m.group(1);
                                try {
                                    return Integer.parseInt(version);
                                } catch (NumberFormatException e) {
                                    return 0;
                                }
                            }
                        }
                    }
                }
            }
        }

        return 0;
    }

    /**
     * Validates this XML against one of the requested SDK Repository schemas.
     * If the XML was correctly validated, returns the schema that worked.
     * If it doesn't validate, returns null and stores the error in outError[0].
     * If we can't find a validator, returns null and set validatorFound[0] to false.
     */
    @VisibleForTesting(visibility=Visibility.PRIVATE)
    protected String validateXml(InputStream xml, String url, int version,
            String[] outError, Boolean[] validatorFound) {

        if (xml == null) {
            return null;
        }

        try {
            Validator validator = getValidator(version);

            if (validator == null) {
                validatorFound[0] = Boolean.FALSE;
                outError[0] = String.format(
                        "XML verification failed for %1$s.\nNo suitable XML Schema Validator could be found in your Java environment. Please consider updating your version of Java.",
                        url);
                return null;
            }

            validatorFound[0] = Boolean.TRUE;

            // Reset the stream if it supports that operation.
            xml.reset();

            // Validation throws a bunch of possible Exceptions on failure.
            validator.validate(new StreamSource(xml));
            return SdkStatsConstants.getSchemaUri(version);

        } catch (SAXParseException e) {
            outError[0] = String.format(
                    "XML verification failed for %1$s.\nLine %2$d:%3$d, Error: %4$s",
                    url,
                    e.getLineNumber(),
                    e.getColumnNumber(),
                    e.toString());

        } catch (Exception e) {
            outError[0] = String.format(
                    "XML verification failed for %1$s.\nError: %2$s",
                    url,
                    e.toString());
        }
        return null;
    }

    /**
     * Helper method that returns a validator for our XSD, or null if the current Java
     * implementation can't process XSD schemas.
     *
     * @param version The version of the XML Schema.
     *        See {@link SdkStatsConstants#getXsdStream(int)}
     */
    private Validator getValidator(int version) throws SAXException {
        InputStream xsdStream = SdkStatsConstants.getXsdStream(version);
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

            if (factory == null) {
                return null;
            }

            // This may throw a SAX Exception if the schema itself is not a valid XSD
            Schema schema = factory.newSchema(new StreamSource(xsdStream));

            Validator validator = schema == null ? null : schema.newValidator();

            return validator;
        } finally {
            if (xsdStream != null) {
                try {
                    xsdStream.close();
                } catch (IOException ignore) {}
            }
        }
    }

    /**
     * Takes an XML document as a string as parameter and returns a DOM for it.
     *
     * On error, returns null and prints a (hopefully) useful message on the monitor.
     */
    @VisibleForTesting(visibility=Visibility.PRIVATE)
    protected Document getDocument(InputStream xml, ITaskMonitor monitor) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setIgnoringComments(true);
            factory.setNamespaceAware(true);

            DocumentBuilder builder = factory.newDocumentBuilder();
            xml.reset();
            Document doc = builder.parse(new InputSource(xml));

            return doc;
        } catch (ParserConfigurationException e) {
            monitor.logError("Failed to create XML document builder");

        } catch (SAXException e) {
            monitor.logError("Failed to parse XML document");

        } catch (IOException e) {
            monitor.logError("Failed to read XML document");
        }

        return null;
    }

    /**
     * Parses all valid platforms found in the XML.
     * Changes the stats array returned by {@link #getStats()}
     * (also returns the value directly, useful for unit tests.)
     */
    @VisibleForTesting(visibility=Visibility.PRIVATE)
    protected SparseArray<PlatformStat> parseStatsDocument(
            Document doc,
            String nsUri,
            ITaskMonitor monitor) {

        String baseUrl = System.getenv("SDK_TEST_BASE_URL");            //$NON-NLS-1$
        if (baseUrl != null) {
            if (baseUrl.length() <= 0 || !baseUrl.endsWith("/")) {      //$NON-NLS-1$
                baseUrl = null;
            }
        }

        SparseArray<PlatformStatBase> platforms = new SparseArray<com.android.tools.idea.sdk.remote.internal.SdkStats.PlatformStatBase>();
        int maxApi = 0;

        Node root = getFirstChild(doc, nsUri, SdkStatsConstants.NODE_SDK_STATS);
        if (root != null) {
            for (Node child = root.getFirstChild();
                 child != null;
                 child = child.getNextSibling()) {
                if (child.getNodeType() == Node.ELEMENT_NODE &&
                        nsUri.equals(child.getNamespaceURI()) &&
                        child.getLocalName().equals(SdkStatsConstants.NODE_PLATFORM)) {

                    try {
                        Node node = getFirstChild(child, nsUri, SdkStatsConstants.NODE_API_LEVEL);
                        int apiLevel = Integer.parseInt(node.getTextContent().trim());

                        if (apiLevel < 1) {
                            // bad API level, ignore it.
                            continue;
                        }

                        if (platforms.indexOfKey(apiLevel) >= 0) {
                            // if we already loaded that API, ignore duplicates
                            continue;
                        }

                        String codeName =
                            getFirstChild(child, nsUri, SdkStatsConstants.NODE_CODENAME).
                                getTextContent().trim();
                        String versName =
                            getFirstChild(child, nsUri, SdkStatsConstants.NODE_VERSION).
                                getTextContent().trim();

                        if (codeName == null || versName == null ||
                                codeName.length() == 0 || versName.length() == 0) {
                            // bad names. ignore.
                            continue;
                        }

                        node = getFirstChild(child, nsUri, SdkStatsConstants.NODE_SHARE);
                        float percent = Float.parseFloat(node.getTextContent().trim());

                        if (percent < 0 || percent > 100) {
                            // invalid percentage. ignore.
                            continue;
                        }

                        PlatformStatBase p = new PlatformStatBase(
                                apiLevel, versName, codeName, percent);
                        platforms.put(apiLevel, p);

                        maxApi = apiLevel > maxApi ? apiLevel : maxApi;

                    } catch (Exception ignore) {
                        // Error parsing this platform. Ignore it.
                        continue;
                    }
                }
            }
        }

        mStats.clear();

        // Compute cumulative share percents & fill in final map
        for (int api = 1; api <= maxApi; api++) {
            PlatformStatBase p = platforms.get(api);
            if (p == null) {
                continue;
            }

            float sum = p.getShare();
            for (int j = api + 1; j <= maxApi; j++) {
                PlatformStatBase pj = platforms.get(j);
                if (pj != null) {
                    sum += pj.getShare();
                }
            }

            mStats.put(api, new PlatformStat(p, sum));
        }

        return mStats;
    }

    /**
     * Returns the first child element with the given XML local name.
     * If xmlLocalName is null, returns the very first child element.
     */
    private Node getFirstChild(Node node, String nsUri, String xmlLocalName) {

        for(Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE &&
                    nsUri.equals(child.getNamespaceURI())) {
                if (xmlLocalName == null || child.getLocalName().equals(xmlLocalName)) {
                    return child;
                }
            }
        }

        return null;
    }

}
