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

package com.android.tools.idea.sdk.legacy.remote.internal.packages;

import com.android.annotations.NonNull;
import com.android.repository.Revision;
import com.android.tools.idea.sdk.legacy.remote.internal.archives.*;
import com.android.tools.idea.sdk.legacy.remote.internal.sources.SdkRepoConstants;
import org.w3c.dom.Node;

import java.util.Properties;

/**
 * Misc utilities to help extracting elements and attributes out of a repository XML document.
 */
public class RemotePackageParserUtils {

  /**
   * Parse the {@link ArchFilter} of an &lt;archive&gt; element..
   * <p/>
   * Starting with repo schema 10, add-on schema 7 and sys-img schema 3, this is done using
   * specific optional elements contained within the &lt;archive&gt; element.
   * <p/>
   * If none of the new element are defined, for backward compatibility we try to find
   * the previous style XML attributes "os" and "arch" in the &lt;archive&gt; element.
   *
   * @param archiveNode
   * @return A new {@link ArchFilter}
   */
  @NonNull
  public static ArchFilter parseArchFilter(@NonNull Node archiveNode) {
    String hos = getOptionalXmlString(archiveNode, SdkRepoConstants.NODE_HOST_OS);
    String hb = getOptionalXmlString(archiveNode, SdkRepoConstants.NODE_HOST_BITS);
    String jb = getOptionalXmlString(archiveNode, SdkRepoConstants.NODE_JVM_BITS);
    String mjv = getOptionalXmlString(archiveNode, SdkRepoConstants.NODE_MIN_JVM_VERSION);

    if (hos != null || hb != null || jb != null || mjv != null) {
      Revision rev = null;
      try {
        rev = Revision.parseRevision(mjv);
      }
      catch (NumberFormatException ignore) {
      }

      return new ArchFilter(HostOs.fromXmlName(hos), BitSize.fromXmlName(hb), BitSize.fromXmlName(jb), rev);
    }

    Properties props = new Properties();

    LegacyOs o = (LegacyOs)getEnumAttribute(archiveNode, SdkRepoConstants.LEGACY_ATTR_OS, LegacyOs.values(), null);
    if (o != null) {
      props.setProperty(ArchFilter.LEGACY_PROP_OS, o.toString());
    }

    LegacyArch a = (LegacyArch)getEnumAttribute(archiveNode, SdkRepoConstants.LEGACY_ATTR_ARCH, LegacyArch.values(), null);
    if (a != null) {
      props.setProperty(ArchFilter.LEGACY_PROP_ARCH, a.toString());
    }

    return new ArchFilter(props);
  }

  /**
   * Parses a full revision element such as <revision> or <min-tools-rev>.
   * This supports both the single-integer format as well as the full revision
   * format with major/minor/micro/preview sub-elements.
   *
   * @param revisionNode The node to parse.
   * @return A new {@link Revision}. If parsing failed, major is set to
   * {@link Revision#MISSING_MAJOR_REV}.
   */
  public static Revision parseRevisionElement(Node revisionNode) {
    // This needs to support two modes:
    // - For repository XSD >= 7, <revision> contains sub-elements such as <major> or <minor>.
    // - Otherwise for repository XSD < 7, <revision> contains an integer.
    // The <major> element is mandatory, so it's easy to distinguish between both cases.
    int major, minor, micro, preview;

    if (revisionNode != null) {
      if (findChildElement(revisionNode, SdkRepoConstants.NODE_MAJOR_REV) != null) {
        // <revision> has a <major> sub-element, so it's a repository XSD >= 7.
        major = getXmlInt(revisionNode, SdkRepoConstants.NODE_MAJOR_REV, -1);
        minor = getXmlInt(revisionNode, SdkRepoConstants.NODE_MINOR_REV, -1);
        if (minor == -1) {
          return new Revision(major);
        }
        micro = getXmlInt(revisionNode, SdkRepoConstants.NODE_MICRO_REV, -1);
        if (micro == -1) {
          return new Revision(major, minor);
        }
        preview = getXmlInt(revisionNode, SdkRepoConstants.NODE_PREVIEW, -1);
        if (preview == -1) {
          return new Revision(major, minor, micro);
        }
        return new Revision(major, minor, micro, preview);
      }
      else {
        try {
          String majorStr = revisionNode.getTextContent().trim();
          major = Integer.parseInt(majorStr);
          return new Revision(major);
        }
        catch (Exception e) {
        }
      }
    }
    return new Revision(Revision.MISSING_MAJOR_REV);
  }

  /**
   * Returns the first child element with the given XML local name and the same NS URI.
   * If xmlLocalName is null, returns the very first child element.
   */
  public static Node findChildElement(Node node, String xmlLocalName) {
    if (node != null) {
      String nsUri = node.getNamespaceURI();
      for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (child.getNodeType() == Node.ELEMENT_NODE) {
          String nsUriChild = child.getNamespaceURI();
          if ((nsUri == null && nsUriChild == null) || (nsUri != null && nsUri.equals(nsUriChild))) {
            if (xmlLocalName == null || xmlLocalName.equals(child.getLocalName())) {
              return child;
            }
          }
        }
      }
    }
    return null;
  }

  /**
   * Retrieves the value of that XML element as a string.
   * Returns an empty string whether the element is missing or empty,
   * so you can't tell the difference.
   * <p/>
   * Note: use {@link #getOptionalXmlString(Node, String)} if you need to know when the
   * element is missing versus empty.
   *
   * @param node         The XML <em>parent</em> node to parse.
   * @param xmlLocalName The XML local name to find in the parent node.
   * @return The text content of the element. Returns an empty string whether the element
   * is missing or empty, so you can't tell the difference.
   */
  public static String getXmlString(Node node, String xmlLocalName) {
    return getXmlString(node, xmlLocalName, "");                    //$NON-NLS-1$
  }

  /**
   * Retrieves the value of that XML element as a string.
   * Returns the defaultValue if the element is missing or empty.
   * <p/>
   * Note: use {@link #getOptionalXmlString(Node, String)} if you need to know when the
   * element is missing versus empty.
   *
   * @param node         The XML <em>parent</em> node to parse.
   * @param xmlLocalName The XML local name to find in the parent node.
   * @param defaultValue A default value to return if the element is missing.
   * @return The text content of the element
   * or the defaultValue if the element is missing or empty.
   */
  public static String getXmlString(Node node, String xmlLocalName, String defaultValue) {
    Node child = findChildElement(node, xmlLocalName);
    String content = child == null ? null : child.getTextContent();
    return content == null || content.isEmpty() ? defaultValue : content;
  }

  /**
   * Retrieves the value of that XML element as a string.
   * Returns null when the element is missing, so you can tell between a missing element
   * and an empty one.
   * <p/>
   * Note: use {@link #getXmlString(Node, String)} if you don't need to know when the
   * element is missing versus empty.
   *
   * @param node         The XML <em>parent</em> node to parse.
   * @param xmlLocalName The XML local name to find in the parent node.
   * @return The text content of the element. Returns null when the element is missing.
   * Returns an empty string whether the element is present but empty.
   */
  public static String getOptionalXmlString(Node node, String xmlLocalName) {
    Node child = findChildElement(node, xmlLocalName);
    return child == null ? null : child.getTextContent();  //$NON-NLS-1$
  }

  /**
   * Retrieves the value of that XML element as an integer.
   * Returns the default value when the element is missing or is not an integer.
   */
  public static int getXmlInt(Node node, String xmlLocalName, int defaultValue) {
    String s = getXmlString(node, xmlLocalName);
    try {
      return Integer.parseInt(s);
    }
    catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /**
   * Retrieves the value of that XML element as a long.
   * Returns the default value when the element is missing or is not an integer.
   */
  public static long getXmlLong(Node node, String xmlLocalName, long defaultValue) {
    String s = getXmlString(node, xmlLocalName);
    try {
      return Long.parseLong(s);
    }
    catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /**
   * Retrieve an attribute which value must match one of the given enums using a
   * case-insensitive name match.
   * <p/>
   * Returns defaultValue if the attribute does not exist or its value does not match
   * the given enum values.
   */
  public static Object getEnumAttribute(Node archiveNode, String attrName, Object[] values, Object defaultValue) {

    Node attr = archiveNode.getAttributes().getNamedItem(attrName);
    if (attr != null) {
      String found = attr.getNodeValue();
      for (Object value : values) {
        if (value.toString().equalsIgnoreCase(found)) {
          return value;
        }
      }
    }

    return defaultValue;
  }
}
