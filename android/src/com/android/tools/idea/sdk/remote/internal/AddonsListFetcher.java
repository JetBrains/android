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
import com.android.tools.idea.sdk.remote.internal.sources.SdkAddonsListConstants;
import com.android.tools.idea.sdk.remote.internal.sources.SdkRepoConstants;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.net.ssl.SSLKeyException;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.*;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches and loads an sdk-addons-list XML.
 * <p/>
 * Such an XML contains a simple list of add-ons site that are to be loaded by default by the
 * SDK Manager. <br/>
 * The XML must conform to the sdk-addons-list-N.xsd. <br/>
 * Constants used in the XML are defined in {@link SdkAddonsListConstants}.
 */
public class AddonsListFetcher {

    public enum SiteType {
        ADDON_SITE,
        SYS_IMG_SITE
    }

    /**
     * An immutable structure representing an add-on site.
     */
    public static class Site {
        private final String mUrl;
        private final String mUiName;
        private final SiteType mType;

        private Site(String url, String uiName, SiteType type) {
            mType = type;
            mUrl = url.trim();
            mUiName = uiName;
        }

        public String getUrl() {
            return mUrl;
        }

        public String getUiName() {
            return mUiName;
        }

        public SiteType getType() {
            return mType;
        }

        /** Returns a debug string representation of this object. Not for user display. */
        @Override
        public String toString() {
            return String.format("<%1$s URL='%2$s' Name='%3$s'>",   //$NON-NLS-1$
                                 mType, mUrl, mUiName);
        }
    }

    /**
     * Fetches the addons list from the given URL.
     *
     * @param url The URL of an XML file resource that conforms to the latest sdk-addons-list-N.xsd.
     *   For the default operation, use {@link SdkAddonsListConstants#URL_ADDON_LIST}.
     *   Cannot be null.
     * @param cache The {@link DownloadCache} instance to use. Cannot be null.
     * @param monitor A monitor to report errors. Cannot be null.
     * @return An array of {@link Site} on success (possibly empty), or null on error.
     */
    public Site[] fetch(String url, DownloadCache cache, ITaskMonitor monitor) {

        url = url == null ? "" : url.trim();

        monitor.setProgressMax(6);
        monitor.setDescription("Fetching %1$s", url);
        monitor.incProgress(1);

        Exception[] exception = new Exception[] { null };
        Boolean[] validatorFound = new Boolean[] { Boolean.FALSE };
        String[] validationError = new String[] { null };
        Document validatedDoc = null;
        String validatedUri = null;

        String[] defaultNames = new String[SdkAddonsListConstants.NS_LATEST_VERSION];
        for (int version = SdkAddonsListConstants.NS_LATEST_VERSION, i = 0;
                version >= 1;
                version--, i++) {
            defaultNames[i] = SdkAddonsListConstants.getDefaultName(version);
        }

        InputStream xml = fetchXmlUrl(url, cache, monitor.createSubMonitor(1), exception);
        if (xml != null) {
            int version = getXmlSchemaVersion(xml);
            if (version == 0) {
                closeStream(xml);
                xml = null;
            }
        }

        String baseUrl = url;
        if (!baseUrl.endsWith("/")) {                       //$NON-NLS-1$
            int pos = baseUrl.lastIndexOf('/');
            if (pos > 0) {
                baseUrl = baseUrl.substring(0, pos + 1);
            }
        }

        // If we can't find the latest version, try earlier schema versions.
        if (xml == null && defaultNames.length > 0) {
            ITaskMonitor subMonitor = monitor.createSubMonitor(1);
            subMonitor.setProgressMax(defaultNames.length);

            for (String name : defaultNames) {
                String newUrl = baseUrl + name;
                if (newUrl.equals(url)) {
                    continue;
                }
                xml = fetchXmlUrl(newUrl, cache, subMonitor.createSubMonitor(1), exception);
                if (xml != null) {
                    int version = getXmlSchemaVersion(xml);
                    if (version == 0) {
                        closeStream(xml);
                        xml = null;
                    } else {
                        url = newUrl;
                        subMonitor.incProgress(
                                subMonitor.getProgressMax() - subMonitor.getProgress());
                        break;
                    }
                }
            }
        } else {
            monitor.incProgress(1);
        }

        if (xml != null) {
            monitor.setDescription("Validate XML");

            // Explore the XML to find the potential XML schema version
            int version = getXmlSchemaVersion(xml);

            if (version >= 1 && version <= SdkAddonsListConstants.NS_LATEST_VERSION) {
                // This should be a version we can handle. Try to validate it
                // and report any error as invalid XML syntax,

                String uri = validateXml(xml, url, version, validationError, validatorFound);
                if (uri != null) {
                    // Validation was successful
                    validatedDoc = getDocument(xml, monitor);
                    validatedUri = uri;

                }
            } else if (version > SdkAddonsListConstants.NS_LATEST_VERSION) {
                // The schema used is more recent than what is supported by this tool.
                // We don't have an upgrade-path support yet, so simply ignore the document.
                closeStream(xml);
                return null;
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
            return null;
        }

        monitor.incProgress(1);

        Site[] result = null;

        if (xml != null) {
            monitor.setDescription("Parse XML");
            monitor.incProgress(1);
            result = parseAddonsList(validatedDoc, validatedUri, baseUrl, monitor);
        }

        // done
        monitor.incProgress(1);

        closeStream(xml);
        return result;
    }

    /**
     * Fetches the document at the given URL and returns it as a stream. Returns
     * null if anything wrong happens.
     *
     * @param urlString The URL to load, as a string.
     * @param monitor {@link ITaskMonitor} related to this URL.
     * @param outException If non null, where to store any exception that
     *            happens during the fetch.
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
     * @return 1..{@link SdkAddonsListConstants#NS_LATEST_VERSION} for a valid schema version
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
            assert xml.markSupported();
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
            //--For debug--System.err.println("getXmlSchemaVersion exception: " + e.toString());
        }

        if (doc == null) {
            return 0;
        }

        // Check the root element is an XML with at least the following properties:
        // <sdk:sdk-addons-list
        //    xmlns:sdk="http://schemas.android.com/sdk/android/addons-list/$N">
        //
        // Note that we don't have namespace support enabled, we just do it manually.

        Pattern nsPattern = Pattern.compile(SdkAddonsListConstants.NS_PATTERN);

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
                if (SdkAddonsListConstants.NODE_SDK_ADDONS_LIST.equals(name)) {
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
            assert xml.markSupported();
            xml.reset();

            // Validation throws a bunch of possible Exceptions on failure.
            validator.validate(new StreamSource(xml));
            return SdkAddonsListConstants.getSchemaUri(version);

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
     *        See {@link SdkAddonsListConstants#getXsdStream(int)}
     */
    private Validator getValidator(int version) throws SAXException {
        StreamSource[] xsdStreams = SdkAddonsListConstants.getXsdStream(version);
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

        if (factory == null) {
            return null;
        }

        // This may throw a SAX Exception if the schema itself is not a valid XSD
        Schema schema = factory.newSchema(xsdStreams);

        Validator validator = schema == null ? null : schema.newValidator();

        return validator;
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
            assert xml.markSupported();
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
     * Parse all sites defined in the Addons list XML and returns an array of sites.
     *
     * @param doc The XML DOM to parse.
     * @param nsUri The addons-list schema URI of the document.
     * @param baseUrl The base URL of the caller (e.g. where addons-list-N.xml was fetched from.)
     * @param monitor A non-null monitor to print to.
     */
    @VisibleForTesting(visibility=Visibility.PRIVATE)
    protected Site[] parseAddonsList(
            Document doc,
            String nsUri,
            String baseUrl,
            ITaskMonitor monitor) {

        String testBaseUrl = System.getenv("SDK_TEST_BASE_URL");                //$NON-NLS-1$
        if (testBaseUrl != null) {
            if (testBaseUrl.length() <= 0 || !testBaseUrl.endsWith("/")) {      //$NON-NLS-1$
                testBaseUrl = null;
            }
        }

        Node root = getFirstChild(doc, nsUri, SdkAddonsListConstants.NODE_SDK_ADDONS_LIST);
        if (root != null) {
            ArrayList<Site> sites = new ArrayList<Site>();

            for (Node child = root.getFirstChild();
                 child != null;
                 child = child.getNextSibling()) {
                if (child.getNodeType() == Node.ELEMENT_NODE &&
                        nsUri.equals(child.getNamespaceURI())) {

                    String elementName = child.getLocalName();
                    SiteType type = null;

                    if (SdkAddonsListConstants.NODE_SYS_IMG_SITE.equals(elementName)) {
                        type = SiteType.SYS_IMG_SITE;

                    } else if (SdkAddonsListConstants.NODE_ADDON_SITE.equals(elementName)) {
                        type = SiteType.ADDON_SITE;
                    }

                    // Not an addon-site nor a sys-img-site, don't process this.
                    if (type == null) {
                        continue;
                    }

                    Node url = getFirstChild(child, nsUri, SdkAddonsListConstants.NODE_URL);
                    Node name = getFirstChild(child, nsUri, SdkAddonsListConstants.NODE_NAME);

                    if (name != null && url != null) {
                        String strUrl  = url.getTextContent().trim();
                        String strName = name.getTextContent().trim();

                        if (testBaseUrl != null &&
                                strUrl.startsWith(SdkRepoConstants.URL_GOOGLE_SDK_SITE)) {
                            strUrl = testBaseUrl +
                                   strUrl.substring(SdkRepoConstants.URL_GOOGLE_SDK_SITE.length());
                        } else if (!strUrl.startsWith("http://") &&             //$NON-NLS-1$
                                !strUrl.startsWith("https://")) {               //$NON-NLS-1$
                            // This looks like a relative URL, add the fetcher's base URL to it.
                            strUrl = baseUrl + strUrl;
                        }

                        if (strUrl.length() > 0 && strName.length() > 0) {
                            sites.add(new Site(strUrl, strName, type));
                        }
                    }
                }
            }

            return sites.toArray(new Site[sites.size()]);
        }

        return null;
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
