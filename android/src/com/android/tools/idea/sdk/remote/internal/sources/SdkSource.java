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

package com.android.tools.idea.sdk.remote.internal.sources;

import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.annotations.VisibleForTesting.Visibility;
import com.android.io.NonClosingInputStream;
import com.android.io.NonClosingInputStream.CloseBehavior;
import com.android.tools.idea.sdk.remote.RemotePkgInfo;
import com.android.tools.idea.sdk.remote.internal.DownloadCache;
import com.android.tools.idea.sdk.remote.internal.ITaskMonitor;
import com.android.tools.idea.sdk.remote.internal.packages.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An sdk-addon or sdk-repository source, i.e. a download site.
 * It may be a full repository or an add-on only repository.
 * A repository describes one or {@link Package}s available for download.
 */
public abstract class SdkSource implements Comparable<SdkSource> {

  private String mUrl;

  private RemotePkgInfo[] mPackages;
  private String mDescription;
  private String mFetchError;
  private final String mUiName;

  private static final SdkSourceProperties sSourcesProps = new SdkSourceProperties();

  /**
   * Constructs a new source for the given repository URL.
   *
   * @param url    The source URL. Cannot be null. If the URL ends with a /, the default
   *               repository.xml filename will be appended automatically.
   * @param uiName The UI-visible name of the source. Can be null.
   */
  public SdkSource(String url, String uiName) {

    // URLs should not be null and should not have whitespace.
    if (url == null) {
      url = "";
    }
    url = url.trim();

    // if the URL ends with a /, it must be "directory" resource,
    // in which case we automatically add the default file that will
    // looked for. This way it will be obvious to the user which
    // resource we are actually trying to fetch.
    if (url.endsWith("/")) {  //$NON-NLS-1$
      String[] names = getDefaultXmlFileUrls();
      if (names.length > 0) {
        url += names[0];
      }
    }

    if (uiName == null) {
      uiName = sSourcesProps.getProperty(SdkSourceProperties.KEY_NAME, url, null);
    }
    else {
      sSourcesProps.setProperty(SdkSourceProperties.KEY_NAME, url, uiName);
    }

    mUrl = url;
    mUiName = uiName;
    setDefaultDescription();
  }

  /**
   * Returns true if this is an addon source.
   * We only load addons and extras from these sources.
   */
  public abstract boolean isAddonSource();

  /**
   * Returns true if this is a system-image source.
   * We only load system-images from these sources.
   */
  public abstract boolean isSysImgSource();


  /**
   * Returns the basename of the default URLs to try to download the
   * XML manifest.
   * E.g. this is typically SdkRepoConstants.URL_DEFAULT_XML_FILE
   * or SdkAddonConstants.URL_DEFAULT_XML_FILE
   */
  protected abstract String[] getDefaultXmlFileUrls();

  /**
   * Returns SdkRepoConstants.NS_LATEST_VERSION or SdkAddonConstants.NS_LATEST_VERSION.
   */
  protected abstract int getNsLatestVersion();

  /**
   * Returns SdkRepoConstants.NS_URI or SdkAddonConstants.NS_URI.
   */
  protected abstract String getNsUri();

  /**
   * Returns SdkRepoConstants.NS_PATTERN or SdkAddonConstants.NS_PATTERN.
   */
  protected abstract String getNsPattern();

  /**
   * Returns SdkRepoConstants.getSchemaUri() or SdkAddonConstants.getSchemaUri().
   */
  protected abstract String getSchemaUri(int version);

  /* Returns SdkRepoConstants.NODE_SDK_REPOSITORY or SdkAddonConstants.NODE_SDK_ADDON. */
  protected abstract String getRootElementName();

  /**
   * Returns SdkRepoConstants.getXsdStream() or SdkAddonConstants.getXsdStream().
   */
  protected abstract StreamSource[] getXsdStream(int version);

  /**
   * In case we fail to load an XML, examine the XML to see if it matches a <b>future</b>
   * schema that as at least a <code>tools</code> node that we could load to update the
   * SDK Manager.
   *
   * @param xml The input XML stream. Can be null.
   * @return Null on failure, otherwise returns an XML DOM with just the tools we
   * need to update this SDK Manager.
   * @null Can return null on failure.
   */
  protected abstract Document findAlternateToolsXml(@Nullable InputStream xml) throws IOException;

  /**
   * Two repo source are equal if they have the same URL.
   */
  @Override
  public boolean equals(Object obj) {
    if (obj instanceof SdkSource) {
      SdkSource rs = (SdkSource)obj;
      return rs.getUrl().equals(this.getUrl());
    }
    return false;
  }

  @Override
  public int hashCode() {
    return mUrl.hashCode();
  }

  /**
   * Implementation of the {@link Comparable} interface.
   * Simply compares the URL using the string's default ordering.
   */
  @Override
  public int compareTo(SdkSource rhs) {
    return this.getUrl().compareTo(rhs.getUrl());
  }

  /**
   * Returns the UI-visible name of the source. Can be null.
   */
  public String getUiName() {
    return mUiName;
  }

  /**
   * Returns the URL of the XML file for this source.
   */
  public String getUrl() {
    return mUrl;
  }

  /**
   * Returns the list of known packages found by the last call to load().
   * This is null when the source hasn't been loaded yet -- caller should
   * then call {@link #load} to load the packages.
   */
  public RemotePkgInfo[] getPackages() {
    return mPackages;
  }

  @VisibleForTesting(visibility = Visibility.PRIVATE)
  protected void setPackages(RemotePkgInfo[] packages) {
    mPackages = packages;

    if (mPackages != null) {
      // Order the packages.
      Arrays.sort(mPackages, null);
    }
  }

  /**
   * Clear the internal packages list. After this call, {@link #getPackages()} will return
   * null till load() is called.
   */
  public void clearPackages() {
    setPackages(null);
  }

  /**
   * Indicates if the source is enabled.
   * <p/>
   * A 3rd-party add-on source can be disabled by the user to prevent from loading it.
   *
   * @return True if the source is enabled (default is true).
   */
  public boolean isEnabled() {
    // A URL is enabled if it's not in the disabled list.
    return sSourcesProps.getProperty(SdkSourceProperties.KEY_DISABLED, mUrl, null) == null;
  }

  /**
   * Changes whether the source is marked as enabled.
   * <p/>
   * When <em>changing</em> the enable state, the current package list is purged
   * and the next {@code load} will either return an empty list (if disabled) or
   * the actual package list (if enabled.)
   *
   * @param enabled True for the source to be enabled (can be loaded), false otherwise.
   */
  public void setEnabled(boolean enabled) {
    if (enabled != isEnabled()) {
      // First we clear the current package list, which will force the
      // next load() to actually set the package list as desired.
      clearPackages();

      sSourcesProps.setProperty(SdkSourceProperties.KEY_DISABLED, mUrl, enabled ? null /*remove*/ : "disabled"); //$NON-NLS-1$
    }
  }

  /**
   * Returns the short description of the source, if not null.
   * Otherwise returns the default Object toString result.
   * <p/>
   * This is mostly helpful for debugging.
   * For UI display, use the {@link IDescription} interface.
   */
  @Override
  public String toString() {
    String s = getShortDescription();
    if (s != null) {
      return s;
    }
    return super.toString();
  }

  public String getShortDescription() {

    if (mUiName != null && mUiName.length() > 0) {

      String host = "malformed URL";

      try {
        URL u = new URL(mUrl);
        host = u.getHost();
      }
      catch (MalformedURLException e) {
      }

      return String.format("%1$s (%2$s)", mUiName, host);

    }
    return mUrl;
  }

  /**
   * Returns the last fetch error description.
   * If there was no error, returns null.
   */
  public String getFetchError() {
    return mFetchError;
  }

  /**
   * Tries to fetch the repository index for the given URL and updates the package list.
   * When a source is disabled, this create an empty non-null package list.
   * <p/>
   * Callers can get the package list using {@link #getPackages()} after this. It will be
   * null in case of error, in which case {@link #getFetchError()} can be used to an
   * error message.
   *
   * TODO(jbakermalone): clean up below once validation in UI can match most restrictive case.
   */
  public void load(DownloadCache cache, ITaskMonitor logger, boolean forceHttp) {

    setDefaultDescription();
    logger.setProgressMax(7);

    if (!isEnabled()) {
      setPackages(new RemotePkgInfo[0]);
      mDescription += "\nSource is disabled.";
      logger.incProgress(7);
      return;
    }

    String url = mUrl;
    if (forceHttp) {
      url = url.replaceAll("https://", "http://");  //$NON-NLS-1$ //$NON-NLS-2$
    }

    logger.setDescription("Fetching URL: %1$s", url);
    logger.incProgress(1);

    mFetchError = null;
    Boolean[] validatorFound = new Boolean[]{Boolean.FALSE};
    String[] validationError = new String[]{null};
    Exception[] exception = new Exception[]{null};
    Document validatedDoc = null;
    boolean usingAlternateXml = false;
    boolean usingAlternateUrl = false;
    String validatedUri = null;

    String[] defaultNames = getDefaultXmlFileUrls();
    String firstDefaultName = defaultNames.length > 0 ? defaultNames[0] : "";

    InputStream xml = fetchXmlUrl(url, cache, logger.createSubMonitor(1), exception);
    if (xml != null) {
      int version = getXmlSchemaVersion(xml);
      if (version == 0) {
        closeStream(xml);
        xml = null;
      }
    }

    // FIXME: this is a quick fix to support an alternate upgrade path.
    // The whole logic below needs to be updated.
    if (xml == null && defaultNames.length > 0) {
      ITaskMonitor subMonitor = logger.createSubMonitor(1);
      subMonitor.setProgressMax(defaultNames.length);

      String baseUrl = url;
      if (!baseUrl.endsWith("/")) {
        int pos = baseUrl.lastIndexOf('/');
        if (pos > 0) {
          baseUrl = baseUrl.substring(0, pos + 1);
        }
      }

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
          }
          else {
            url = newUrl;
            subMonitor.incProgress(subMonitor.getProgressMax() - subMonitor.getProgress());
            break;
          }
        }
      }
    }
    else {
      logger.incProgress(1);
    }

    // If the original URL can't be fetched
    // and the URL doesn't explicitly end with our filename
    // and it wasn't an HTTP authentication operation canceled by the user
    // then make another tentative after changing the URL.
    if (xml == null && !url.endsWith(firstDefaultName) && !(exception[0] instanceof ProcessCanceledException)) {
      if (!url.endsWith("/")) {       //$NON-NLS-1$
        url += "/";                 //$NON-NLS-1$
      }
      url += firstDefaultName;

      xml = fetchXmlUrl(url, cache, logger.createSubMonitor(1), exception);
      usingAlternateUrl = true;
    }
    else {
      logger.incProgress(1);
    }

    // FIXME this needs to revisited.
    if (xml != null) {
      logger.setDescription("Validate XML: %1$s", url);

      ITaskMonitor subMonitor = logger.createSubMonitor(2);
      subMonitor.setProgressMax(2);
      for (int tryOtherUrl = 0; tryOtherUrl < 2; tryOtherUrl++) {
        // Explore the XML to find the potential XML schema version
        int version = getXmlSchemaVersion(xml);

        if (version >= 1 && version <= getNsLatestVersion()) {
          // This should be a version we can handle. Try to validate it
          // and report any error as invalid XML syntax,

          String uri = validateXml(xml, url, version, validationError, validatorFound);
          if (uri != null) {
            // Validation was successful
            validatedDoc = getDocument(xml, logger);
            validatedUri = uri;

            if (usingAlternateUrl && validatedDoc != null) {
              // If the second tentative succeeded, indicate it in the console
              // with the URL that worked.
              logger.log("Repository found at %1$s", url);

              // Keep the modified URL
              mUrl = url;
            }
          }
          else if (validatorFound[0].equals(Boolean.FALSE)) {
            // Validation failed because this JVM lacks a proper XML Validator
            mFetchError = validationError[0];
          }
          else {
            // We got a validator but validation failed. We know there's
            // what looks like a suitable root element with a suitable XMLNS
            // so it must be a genuine error of an XML not conforming to the schema.
          }
        }
        else if (version > getNsLatestVersion()) {
          // The schema used is more recent than what is supported by this tool.
          // Tell the user to upgrade, pointing him to the right version of the tool
          // package.

          try {
            validatedDoc = findAlternateToolsXml(xml);
          }
          catch (IOException e) {
            // Failed, will be handled below.
          }
          if (validatedDoc != null) {
            validationError[0] = null;  // remove error from XML validation
            validatedUri = getNsUri();
            usingAlternateXml = true;
          }

        }
        else if (version < 1 && tryOtherUrl == 0 && !usingAlternateUrl) {
          // This is obviously not one of our documents.
          mFetchError = String.format("Failed to validate the XML for the repository at URL '%1$s'", url);

          // If we haven't already tried the alternate URL, let's do it now.
          // We don't capture any fetch exception that happen during the second
          // fetch in order to avoid hiding any previous fetch errors.
          if (!url.endsWith(firstDefaultName)) {
            if (!url.endsWith("/")) {       //$NON-NLS-1$
              url += "/";                 //$NON-NLS-1$
            }
            url += firstDefaultName;

            closeStream(xml);
            xml = fetchXmlUrl(url, cache, subMonitor.createSubMonitor(1), null /* outException */);
            subMonitor.incProgress(1);
            // Loop to try the alternative document
            if (xml != null) {
              usingAlternateUrl = true;
              continue;
            }
          }
        }
        else if (version < 1 && usingAlternateUrl && mFetchError == null) {
          // The alternate URL is obviously not a valid XML either.
          // We only report the error if we failed to produce one earlier.
          mFetchError = String.format("Failed to validate the XML for the repository at URL '%1$s'", url);
        }

        // If we get here either we succeeded or we ran out of alternatives.
        break;
      }
    }

    // If any exception was handled during the URL fetch, display it now.
    if (exception[0] != null) {
      mFetchError = "Failed to fetch URL";

      String reason = null;
      if (exception[0] instanceof FileNotFoundException) {
        // FNF has no useful getMessage, so we need to special handle it.
        reason = "File not found";
        mFetchError += ": " + reason;
      }
      else if (exception[0] instanceof SSLKeyException) {
        // That's a common error and we have a pref for it.
        reason = "HTTPS SSL error. You might want to force download through HTTP in the settings.";
        mFetchError += ": HTTPS SSL error";
      }
      else if (exception[0].getMessage() != null) {
        reason = exception[0].getClass().getSimpleName().replace("Exception", "") //$NON-NLS-1$ //$NON-NLS-2$
                 + ' ' + exception[0].getMessage();
      }
      else {
        reason = exception[0].toString();
      }

      logger.warning("Failed to fetch URL %1$s, reason: %2$s", url, reason);
    }

    if (validationError[0] != null) {
      logger.logError("%s", validationError[0]);  //$NON-NLS-1$
    }

    // Stop here if we failed to validate the XML. We don't want to load it.
    if (validatedDoc == null) {
      return;
    }

    if (usingAlternateXml) {
      // We found something using the "alternate" XML schema (that is the one made up
      // to support schema upgrades). That means the user can only install the tools
      // and needs to upgrade them before it download more stuff.

      // Is the manager running from inside ADT?
      // We check that com.android.ide.eclipse.adt.AdtPlugin exists using reflection.

      boolean isADT = false;
      try {
        Class<?> adt = Class.forName("com.android.ide.eclipse.adt.AdtPlugin");  //$NON-NLS-1$
        isADT = (adt != null);
      }
      catch (ClassNotFoundException e) {
        // pass
      }

      String info;
      if (isADT) {
        info = "This repository requires a more recent version of ADT. Please update the Eclipse Android plugin.";
        mDescription =
          "This repository requires a more recent version of ADT, the Eclipse Android plugin.\n" +
          "You must update it before you can see other new packages.";

      }
      else {
        info = "This repository requires a more recent version of the Tools. Please update.";
        mDescription =
          "This repository requires a more recent version of the Tools.\nYou must update it before you can see other new packages.";
      }

      mFetchError = mFetchError == null ? info : mFetchError + ". " + info;
    }

    logger.incProgress(1);

    if (xml != null) {
      logger.setDescription("Parse XML:    %1$s", url);
      logger.incProgress(1);
      parsePackages(validatedDoc, validatedUri, logger);
      if (mPackages == null || mPackages.length == 0) {
        mDescription += "\nNo packages found.";
      }
      else if (mPackages.length == 1) {
        mDescription += "\nOne package found.";
      }
      else {
        mDescription += String.format("\n%1$d packages found.", mPackages.length);
      }
    }

    // done
    logger.incProgress(1);
    closeStream(xml);
  }

  private void setDefaultDescription() {
    if (isAddonSource()) {
      String desc = "";

      if (mUiName != null) {
        desc += "Add-on Provider: " + mUiName;
        desc += "\n";
      }
      desc += "Add-on URL: " + mUrl;

      mDescription = desc;
    }
    else {
      mDescription = String.format("SDK Source: %1$s", mUrl);
    }
  }

  /**
   * Fetches the document at the given URL and returns it as a string. Returns
   * null if anything wrong happens and write errors to the monitor.
   *
   * @param urlString    The URL to load, as a string.
   * @param monitor      {@link ITaskMonitor} related to this URL.
   * @param outException If non null, where to store any exception that
   *                     happens during the fetch.
   */
  private InputStream fetchXmlUrl(String urlString, DownloadCache cache, ITaskMonitor monitor, Exception[] outException) {
    try {
      InputStream xml = cache.openCachedUrl(urlString, monitor);
      if (xml != null) {
        xml.mark(500000);
        xml = new NonClosingInputStream(xml);
        ((NonClosingInputStream)xml).setCloseBehavior(CloseBehavior.RESET);
      }
      return xml;
    }
    catch (Exception e) {
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
        ((NonClosingInputStream)is).setCloseBehavior(CloseBehavior.CLOSE);
      }
      try {
        is.close();
      }
      catch (IOException ignore) {
      }
    }
  }

  /**
   * Validates this XML against one of the requested SDK Repository schemas.
   * If the XML was correctly validated, returns the schema that worked.
   * If it doesn't validate, returns null and stores the error in outError[0].
   * If we can't find a validator, returns null and set validatorFound[0] to false.
   */
  @VisibleForTesting(visibility = Visibility.PRIVATE)
  protected String validateXml(InputStream xml, String url, int version, String[] outError, Boolean[] validatorFound) {

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
      return getSchemaUri(version);

    }
    catch (SAXParseException e) {
      outError[0] = String
        .format("XML verification failed for %1$s.\nLine %2$d:%3$d, Error: %4$s", url, e.getLineNumber(), e.getColumnNumber(),
                e.toString());

    }
    catch (Exception e) {
      outError[0] = String.format("XML verification failed for %1$s.\nError: %2$s", url, e.toString());
    }
    return null;
  }

  /**
   * Manually parses the root element of the XML to extract the schema version
   * at the end of the xmlns:sdk="http://schemas.android.com/sdk/android/repository/$N"
   * declaration.
   *
   * @return 1..{@link SdkRepoConstants#NS_LATEST_VERSION} for a valid schema version
   * or 0 if no schema could be found.
   */
  @VisibleForTesting(visibility = Visibility.PRIVATE)
  protected int getXmlSchemaVersion(InputStream xml) {
    if (xml == null) {
      return 0;
    }

    // Get an XML document
    // TODO(jbakermalone): no need for full DOM parse here
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

    }
    catch (Exception e) {
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
    // <sdk:sdk-repository
    //    xmlns:sdk="http://schemas.android.com/sdk/android/repository/$N">
    //
    // Note that we don't have namespace support enabled, we just do it manually.

    Pattern nsPattern = Pattern.compile(getNsPattern());

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
        if (getRootElementName().equals(name)) {
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
                }
                catch (NumberFormatException e) {
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
   * Helper method that returns a validator for our XSD, or null if the current Java
   * implementation can't process XSD schemas.
   *
   * @param version The version of the XML Schema.
   *                See {@link SdkRepoConstants#getXsdStream(int)}
   */
  private Validator getValidator(int version) throws SAXException {
    SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

    if (factory == null) {
      return null;
    }

    StreamSource[] xsdStreams = getXsdStream(version);
    // This may throw a SAX Exception if the schema itself is not a valid XSD
    Schema schema = factory.newSchema(xsdStreams);

    Validator validator = schema == null ? null : schema.newValidator();

    // We don't want the default handler, which by default dumps errors to stderr.
    validator.setErrorHandler(new ErrorHandler() {
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

    return validator;
  }

  /**
   * Parse all packages defined in the SDK Repository XML and creates
   * a new mPackages array with them.
   */
  @VisibleForTesting(visibility = Visibility.PRIVATE)
  protected boolean parsePackages(Document doc, String nsUri, ITaskMonitor monitor) {

    Node root = getFirstChild(doc, nsUri, getRootElementName());
    if (root != null) {

      ArrayList<RemotePkgInfo> packages = new ArrayList<RemotePkgInfo>();

      // Parse license definitions
      HashMap<String, String> licenses = new HashMap<String, String>();
      for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (child.getNodeType() == Node.ELEMENT_NODE &&
            nsUri.equals(child.getNamespaceURI()) &&
            child.getLocalName().equals(RepoConstants.NODE_LICENSE)) {
          Node id = child.getAttributes().getNamedItem(RepoConstants.ATTR_ID);
          if (id != null) {
            licenses.put(id.getNodeValue(), child.getTextContent());
          }
        }
      }

      // Parse packages
      for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (child.getNodeType() == Node.ELEMENT_NODE && nsUri.equals(child.getNamespaceURI())) {
          String name = child.getLocalName();
          RemotePkgInfo p = null;

          try {
            // We can load add-on and extra packages from all sources, either
            // internal or user sources.
            if (SdkAddonConstants.NODE_ADD_ON.equals(name)) {
              p = new RemoteAddonPkgInfo(this, child, nsUri, licenses);

            }
            else if (SdkAddonConstants.NODE_EXTRA.equals(name)) {
              p = new RemoteExtraPkgInfo(this, child, nsUri, licenses);

            }
            else if (!isAddonSource()) {
              // We only load platform, doc and tool packages from internal
              // sources, never from user sources.
              if (SdkRepoConstants.NODE_PLATFORM.equals(name)) {
                p = new RemotePlatformPkgInfo(this, child, nsUri, licenses);
              }
              else if (SdkRepoConstants.NODE_DOC.equals(name)) {
                p = new RemoteDocPkgInfo(this, child, nsUri, licenses);
              }
              else if (SdkRepoConstants.NODE_TOOL.equals(name)) {
                p = new RemoteToolPkgInfo(this, child, nsUri, licenses);
              }
              else if (SdkRepoConstants.NODE_PLATFORM_TOOL.equals(name)) {
                p = new PlatformToolRemotePkgInfo(this, child, nsUri, licenses);
              }
              else if (SdkRepoConstants.NODE_BUILD_TOOL.equals(name)) {
                p = new RemoteBuildToolPkgInfo(this, child, nsUri, licenses);
              }
              else if (SdkRepoConstants.NODE_SAMPLE.equals(name)) {
                p = new RemoteSamplePkgInfo(this, child, nsUri, licenses);
              }
              else if (SdkRepoConstants.NODE_SYSTEM_IMAGE.equals(name)) {
                p = new RemoteSystemImagePkgInfo(this, child, nsUri, licenses);
              }
              else if (SdkRepoConstants.NODE_SOURCE.equals(name)) {
                p = new RemoteSourcePkgInfo(this, child, nsUri, licenses);
              }
              else if (SdkRepoConstants.NODE_NDK.equals(name)) {
                p = new RemoteNdkPkgInfo(this, child, nsUri, licenses);
              }
              else if (SdkRepoConstants.NODE_LLDB.equals(name)) {
                p = new RemoteLLDBPkgInfo(this, child, nsUri, licenses);
              }
            }

            if (p != null) {
              packages.add(p);
              monitor.logVerbose("Found %1$s", p.getShortDescription());
            }
          }
          catch (Exception e) {
            // Ignore invalid packages
            String msg = String.format("Ignoring invalid %1$s element: %2$s", name, e.toString());
            monitor.logError(msg);
            Logger.getInstance(getClass()).error(msg, e);
          }
        }
      }

      setPackages(packages.toArray(new RemotePkgInfo[packages.size()]));

      return true;
    }

    return false;
  }

  /**
   * Returns the first child element with the given XML local name.
   * If xmlLocalName is null, returns the very first child element.
   */
  private Node getFirstChild(Node node, String nsUri, String xmlLocalName) {

    for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child.getNodeType() == Node.ELEMENT_NODE && nsUri.equals(child.getNamespaceURI())) {
        if (xmlLocalName == null || child.getLocalName().equals(xmlLocalName)) {
          return child;
        }
      }
    }

    return null;
  }

  /**
   * Takes an XML document as a string as parameter and returns a DOM for it.
   * <p/>
   * On error, returns null and prints a (hopefully) useful message on the monitor.
   */
  @VisibleForTesting(visibility = Visibility.PRIVATE)
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
    }
    catch (ParserConfigurationException e) {
      monitor.logError("Failed to create XML document builder");

    }
    catch (SAXException e) {
      monitor.logError("Failed to parse XML document");

    }
    catch (IOException e) {
      monitor.logError("Failed to read XML document");
    }

    return null;
  }
}
