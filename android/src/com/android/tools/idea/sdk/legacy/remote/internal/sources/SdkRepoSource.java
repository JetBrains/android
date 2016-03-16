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

package com.android.tools.idea.sdk.legacy.remote.internal.sources;

import com.android.annotations.Nullable;
import com.android.tools.idea.sdk.legacy.remote.internal.packages.RemotePackageParserUtils;
import org.w3c.dom.*;
import org.xml.sax.ErrorHandler;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;


/**
 * An sdk-repository source, i.e. a download site.
 * A repository describes one or more {@link com.android.tools.idea.sdk.legacy.remote.internal.packages.Package}s available for download.
 */
public class SdkRepoSource extends SdkSource {

  /**
   * Constructs a new source for the given repository URL.
   *
   * @param url    The source URL. Cannot be null. If the URL ends with a /, the default
   *               repository.xml filename will be appended automatically.
   * @param uiName The UI-visible name of the source. Can be null.
   */
  public SdkRepoSource(String url, String uiName) {
    super(url, uiName);
  }

  /**
   * Returns true if this is an addon source.
   * We only load addons and extras from these sources.
   */
  @Override
  public boolean isAddonSource() {
    return false;
  }

  /**
   * Returns true if this is a system-image source.
   * We only load system-images from these sources.
   */
  @Override
  public boolean isSysImgSource() {
    return false;
  }

  private static String[] sDefaults = null; // lazily allocated in getDefaultXmlFileUrls

  @Override
  protected String[] getDefaultXmlFileUrls() {
    if (sDefaults == null) {
      String[] values = new String[SdkRepoConstants.NS_LATEST_VERSION - SdkRepoConstants.NS_SERVER_MIN_VERSION + 2];
      int k = 0;
      for (int i = SdkRepoConstants.NS_LATEST_VERSION; i >= SdkRepoConstants.NS_SERVER_MIN_VERSION; i--) {
        values[k++] = String.format(SdkRepoConstants.URL_FILENAME_PATTERN, i);
      }
      values[k++] = SdkRepoConstants.URL_DEFAULT_FILENAME;
      assert k == values.length;
      sDefaults = values;
    }

    return sDefaults;
  }

  @Override
  protected int getNsLatestVersion() {
    return SdkRepoConstants.NS_LATEST_VERSION;
  }

  @Override
  protected String getNsUri() {
    return SdkRepoConstants.NS_URI;
  }

  @Override
  protected String getNsPattern() {
    return SdkRepoConstants.NS_PATTERN;
  }

  @Override
  protected String getSchemaUri(int version) {
    return SdkRepoConstants.getSchemaUri(version);
  }

  @Override
  protected String getRootElementName() {
    return SdkRepoConstants.NODE_SDK_REPOSITORY;
  }

  @Override
  protected StreamSource[] getXsdStream(int version) {
    return SdkRepoConstants.getXsdStream(version);
  }

  /**
   * The purpose of this method is to support forward evolution of our schema.
   * <p/>
   * At this point, we know that xml does not point to any schema that this version of
   * the tool knows how to process, so it's not one of the possible 1..N versions of our
   * XSD schema.
   * <p/>
   * We thus try to interpret the byte stream as a possible XML stream. It may not be
   * one at all in the first place. If it looks anything line an XML schema, we try to
   * find its &lt;tool&gt; and the &lt;platform-tools&gt; elements. If we find any,
   * we recreate a suitable document that conforms to what we expect from our XSD schema
   * with only those elements.
   * <p/>
   * To be valid, the &lt;tool&gt; and the &lt;platform-tools&gt; elements must have at
   * least one &lt;archive&gt; compatible with this platform.
   * <p/>
   * Starting the sdk-repository schema v3, &lt;tools&gt; has a &lt;min-platform-tools-rev&gt;
   * node, so technically the corresponding XML schema will be usable only if there's a
   * &lt;platform-tools&gt; with the request revision number. We don't enforce that here, as
   * this is done at install time.
   * <p/>
   * If we don't find anything suitable, we drop the whole thing.
   *
   * @param xml The input XML stream. Can be null.
   * @return Either a new XML document conforming to our schema with at least one &lt;tool&gt;
   * and &lt;platform-tools&gt; element or null.
   * @throws IOException if InputStream.reset() fails
   * @null Can return null on failure.
   */
  @Override
  protected Document findAlternateToolsXml(@Nullable InputStream xml) throws IOException {
    return findAlternateToolsXml(xml, null /*errorHandler*/);
  }

  /**
   * An alternate version of {@link #findAlternateToolsXml(InputStream)} that allows
   * the caller to specify the XML error handler. The default from the underlying Java
   * XML Xerces parser will dump to stdout/stderr, which is not convenient during unit tests.
   *
   * @param xml          The input XML stream. Can be null.
   * @param errorHandler An optional XML error handler. If null, the default will be used.
   * @return Either a new XML document conforming to our schema with at least one &lt;tool&gt;
   * and &lt;platform-tools&gt; element or null.
   * @throws IOException if InputStream.reset() fails
   * @null Can return null on failure.
   * @see #findAlternateToolsXml(InputStream) findAlternateToolsXml() provides more details.
   */
  protected Document findAlternateToolsXml(@Nullable InputStream xml, @Nullable ErrorHandler errorHandler) throws IOException {
    if (xml == null) {
      return null;
    }

    // Reset the stream if it supports that operation.
    assert xml.markSupported();
    xml.reset();

    // Get an XML document

    Document oldDoc = null;
    Document newDoc = null;
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setIgnoringComments(false);
      factory.setValidating(false);

      // Parse the old document using a non namespace aware builder
      factory.setNamespaceAware(false);
      DocumentBuilder builder = factory.newDocumentBuilder();

      if (errorHandler != null) {
        builder.setErrorHandler(errorHandler);
      }

      oldDoc = builder.parse(xml);

      // Prepare a new document using a namespace aware builder
      factory.setNamespaceAware(true);
      builder = factory.newDocumentBuilder();
      newDoc = builder.newDocument();

    }
    catch (Exception e) {
      // Failed to get builder factor
      // Failed to create XML document builder
      // Failed to parse XML document
      // Failed to read XML document
    }

    if (oldDoc == null || newDoc == null) {
      return null;
    }


    // Check the root element is an XML with at least the following properties:
    // <sdk:sdk-repository
    //    xmlns:sdk="http://schemas.android.com/sdk/android/repository/$N">
    //
    // Note that we don't have namespace support enabled, we just do it manually.

    Pattern nsPattern = Pattern.compile(getNsPattern());

    Node oldRoot = null;
    String prefix = null;
    for (Node child = oldDoc.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child.getNodeType() == Node.ELEMENT_NODE) {
        prefix = null;
        String name = child.getNodeName();
        int pos = name.indexOf(':');
        if (pos > 0 && pos < name.length() - 1) {
          prefix = name.substring(0, pos);
          name = name.substring(pos + 1);
        }
        if (SdkRepoConstants.NODE_SDK_REPOSITORY.equals(name)) {
          NamedNodeMap attrs = child.getAttributes();
          String xmlns = "xmlns";                                         //$NON-NLS-1$
          if (prefix != null) {
            xmlns += ":" + prefix;                                      //$NON-NLS-1$
          }
          Node attr = attrs.getNamedItem(xmlns);
          if (attr != null) {
            String uri = attr.getNodeValue();
            if (uri != null && nsPattern.matcher(uri).matches()) {
              oldRoot = child;
              break;
            }
          }
        }
      }
    }

    // we must have found the root node, and it must have an XML namespace prefix.
    if (oldRoot == null || prefix == null || prefix.length() == 0) {
      return null;
    }

    final String ns = getNsUri();
    Element newRoot = newDoc.createElementNS(ns, getRootElementName());
    newRoot.setPrefix(prefix);
    newDoc.appendChild(newRoot);
    int numTool = 0;

    // Find any inner <tool> or <platform-tool> nodes and extract their required parameters

    String[] elementNames = {SdkRepoConstants.NODE_TOOL, SdkRepoConstants.NODE_PLATFORM_TOOL, SdkRepoConstants.NODE_LICENSE};

    Element element = null;
    while ((element = findChild(oldRoot, element, prefix, elementNames)) != null) {
      boolean isElementValid = false;

      String name = element.getLocalName();
      if (name == null) {
        name = element.getNodeName();

        int pos = name.indexOf(':');
        if (pos > 0 && pos < name.length() - 1) {
          name = name.substring(pos + 1);
        }
      }

      // To be valid, the tool or platform-tool element must have:
      // - a <revision> element with a number
      // - a <min-platform-tools-rev> element with a number for a <tool> element
      // - an <archives> element with one or more <archive> elements inside
      // - one of the <archive> elements must have an "os" and "arch" attributes
      //   compatible with the current platform. Only keep the first such element found.
      // - the <archive> element must contain a <size>, a <checksum> and a <url>.
      // - none of the above for a license element

      if (SdkRepoConstants.NODE_LICENSE.equals(name)) {
        isElementValid = true;

      }
      else {
        try {
          Node revision = findChild(element, null, prefix, RepoConstants.NODE_REVISION);
          Node archives = findChild(element, null, prefix, RepoConstants.NODE_ARCHIVES);

          if (revision == null || archives == null) {
            continue;
          }

          // check revision contains a number
          try {
            String content = revision.getTextContent();
            content = content.trim();
            int rev = Integer.parseInt(content);
            if (rev < 1) {
              continue;
            }
          }
          catch (NumberFormatException ignore) {
            continue;
          }

          if (SdkRepoConstants.NODE_TOOL.equals(name)) {
            Node minPTRev = findChild(element, null, prefix, RepoConstants.NODE_MIN_PLATFORM_TOOLS_REV);

            if (minPTRev == null) {
              continue;
            }

            // check min-platform-tools-rev contains a number
            try {
              String content = minPTRev.getTextContent();
              content = content.trim();
              int rev = Integer.parseInt(content);
              if (rev < 1) {
                continue;
              }
            }
            catch (NumberFormatException ignore) {
              continue;
            }
          }

          Node archive = null;
          while ((archive = findChild(archives, archive, prefix, RepoConstants.NODE_ARCHIVE)) != null) {
            try {
              com.android.tools.idea.sdk.legacy.remote.internal.archives.ArchFilter af = RemotePackageParserUtils.parseArchFilter(archive);
              if (af == null || !af.isCompatibleWith(com.android.tools.idea.sdk.legacy.remote.internal.archives.ArchFilter.getCurrent())) {
                continue;
              }

              Node node = findChild(archive, null, prefix, RepoConstants.NODE_URL);
              String url = node == null ? null : node.getTextContent().trim();
              if (url == null || url.length() == 0) {
                continue;
              }

              node = findChild(archive, null, prefix, RepoConstants.NODE_SIZE);
              long size = 0;
              try {
                size = Long.parseLong(node.getTextContent());
              }
              catch (Exception e) {
                // pass
              }
              if (size < 1) {
                continue;
              }

              node = findChild(archive, null, prefix, RepoConstants.NODE_CHECKSUM);
              // double check that the checksum element contains a type=sha1 attribute
              if (node == null) {
                continue;
              }
              NamedNodeMap attrs = node.getAttributes();
              Node typeNode = attrs.getNamedItem(RepoConstants.ATTR_TYPE);
              if (typeNode == null ||
                  !RepoConstants.ATTR_TYPE.equals(typeNode.getNodeName()) ||
                  !RepoConstants.SHA1_TYPE.equals(typeNode.getNodeValue())) {
                continue;
              }
              String sha1 = node == null ? null : node.getTextContent().trim();
              if (sha1 == null || sha1.length() != RepoConstants.SHA1_CHECKSUM_LEN) {
                continue;
              }

              isElementValid = true;

            }
            catch (Exception ignore1) {
              // For debugging it is useful to re-throw the exception.
              // For end-users, not so much. It would be nice to make it
              // happen automatically during unit tests.
              if (System.getenv("TESTING") != null || System.getProperty("THROW_DEEP_EXCEPTION_DURING_TESTING") != null) {
                throw new RuntimeException(ignore1);
              }
            }
          } // while <archive>
        }
        catch (Exception ignore2) {
          // For debugging it is useful to re-throw the exception.
          // For end-users, not so much. It would be nice to make it
          // happen automatically during unit tests.
          if (System.getenv("TESTING") != null || System.getProperty("THROW_DEEP_EXCEPTION_DURING_TESTING") != null) {
            throw new RuntimeException(ignore2);
          }
        }
      }

      if (isElementValid) {
        duplicateNode(newRoot, element, SdkRepoConstants.NS_URI, prefix);
        numTool++;
      }
    } // while <tool>

    return numTool > 0 ? newDoc : null;
  }

  /**
   * Helper method used by {@link #findAlternateToolsXml(InputStream)} to find a given
   * element child in a root XML node.
   */
  private Element findChild(Node rootNode, Node after, String prefix, String[] nodeNames) {
    for (int i = 0; i < nodeNames.length; i++) {
      if (nodeNames[i].indexOf(':') < 0) {
        nodeNames[i] = prefix + ":" + nodeNames[i];
      }
    }
    Node child = after == null ? rootNode.getFirstChild() : after.getNextSibling();
    for (; child != null; child = child.getNextSibling()) {
      if (child.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      for (String nodeName : nodeNames) {
        if (nodeName.equals(child.getNodeName())) {
          return (Element)child;
        }
      }
    }
    return null;
  }

  /**
   * Helper method used by {@link #findAlternateToolsXml(InputStream)} to find a given
   * element child in a root XML node.
   */
  private Node findChild(Node rootNode, Node after, String prefix, String nodeName) {
    return findChild(rootNode, after, prefix, new String[]{nodeName});
  }

  /**
   * Helper method used by {@link #findAlternateToolsXml(InputStream)} to duplicate a node
   * and attach it to the given root in the new document.
   */
  private Element duplicateNode(Element newRootNode, Element oldNode, String namespaceUri, String prefix) {
    // The implementation here is more or less equivalent to
    //
    //    newRoot.appendChild(newDoc.importNode(oldNode, deep=true))
    //
    // except we can't just use importNode() since we need to deal with the fact
    // that the old document is not namespace-aware yet the new one is.

    Document newDoc = newRootNode.getOwnerDocument();
    Element newNode = null;

    String nodeName = oldNode.getNodeName();
    int pos = nodeName.indexOf(':');
    if (pos > 0 && pos < nodeName.length() - 1) {
      nodeName = nodeName.substring(pos + 1);
      newNode = newDoc.createElementNS(namespaceUri, nodeName);
      newNode.setPrefix(prefix);
    }
    else {
      newNode = newDoc.createElement(nodeName);
    }

    newRootNode.appendChild(newNode);

    // Merge in all the attributes
    NamedNodeMap attrs = oldNode.getAttributes();
    for (int i = 0; i < attrs.getLength(); i++) {
      Attr attr = (Attr)attrs.item(i);
      Attr newAttr = null;

      String attrName = attr.getNodeName();
      pos = attrName.indexOf(':');
      if (pos > 0 && pos < attrName.length() - 1) {
        attrName = attrName.substring(pos + 1);
        newAttr = newDoc.createAttributeNS(namespaceUri, attrName);
        newAttr.setPrefix(prefix);
      }
      else {
        newAttr = newDoc.createAttribute(attrName);
      }

      newAttr.setNodeValue(attr.getNodeValue());

      if (pos > 0) {
        newNode.getAttributes().setNamedItemNS(newAttr);
      }
      else {
        newNode.getAttributes().setNamedItem(newAttr);
      }
    }

    // Merge all child elements and texts
    for (Node child = oldNode.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child.getNodeType() == Node.ELEMENT_NODE) {
        duplicateNode(newNode, (Element)child, namespaceUri, prefix);

      }
      else if (child.getNodeType() == Node.TEXT_NODE) {
        Text newText = newDoc.createTextNode(child.getNodeValue());
        newNode.appendChild(newText);
      }
    }

    return newNode;
  }
}
