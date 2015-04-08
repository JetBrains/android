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

package com.android.tools.idea.sdk.remote.internal.packages;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.repository.*;
import com.android.tools.idea.sdk.remote.internal.archives.*;
import org.w3c.dom.Node;

import java.util.Properties;

/**
 * Misc utilities to help extracting elements and attributes out of a repository XML document.
 */
public class PackageParserUtils {

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
        String hos = com.android.tools.idea.sdk.remote.internal.packages.PackageParserUtils
          .getOptionalXmlString(archiveNode, SdkRepoConstants.NODE_HOST_OS);
        String hb  = com.android.tools.idea.sdk.remote.internal.packages.PackageParserUtils
          .getOptionalXmlString(archiveNode, SdkRepoConstants.NODE_HOST_BITS);
        String jb  = com.android.tools.idea.sdk.remote.internal.packages.PackageParserUtils
          .getOptionalXmlString(archiveNode, SdkRepoConstants.NODE_JVM_BITS);
        String mjv = com.android.tools.idea.sdk.remote.internal.packages.PackageParserUtils
          .getOptionalXmlString(archiveNode, SdkRepoConstants.NODE_MIN_JVM_VERSION);

        if (hos != null || hb != null || jb != null || mjv != null) {
            NoPreviewRevision rev = null;
            try {
                rev = NoPreviewRevision.parseRevision(mjv);
            } catch (NumberFormatException ignore) {}

            return new ArchFilter(
                    HostOs.fromXmlName(hos),
                    BitSize.fromXmlName(hb),
                    BitSize.fromXmlName(jb),
                    rev);
        }

        Properties props = new Properties();

        LegacyOs o = (LegacyOs) com.android.tools.idea.sdk.remote.internal.packages.PackageParserUtils
          .getEnumAttribute(archiveNode, SdkRepoConstants.LEGACY_ATTR_OS, LegacyOs.values(), null);
        if (o != null) {
            props.setProperty(ArchFilter.LEGACY_PROP_OS, o.toString());
        }

        LegacyArch a = (LegacyArch) com.android.tools.idea.sdk.remote.internal.packages.PackageParserUtils
          .getEnumAttribute(archiveNode, SdkRepoConstants.LEGACY_ATTR_ARCH, LegacyArch.values(), null);
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
     * @return A new {@link FullRevision}. If parsing failed, major is set to
     *  {@link FullRevision#MISSING_MAJOR_REV}.
     */
    public static FullRevision parseFullRevisionElement(Node revisionNode) {
        // This needs to support two modes:
        // - For repository XSD >= 7, <revision> contains sub-elements such as <major> or <minor>.
        // - Otherwise for repository XSD < 7, <revision> contains an integer.
        // The <major> element is mandatory, so it's easy to distinguish between both cases.
        int major = FullRevision.MISSING_MAJOR_REV,
            minor = FullRevision.IMPLICIT_MINOR_REV,
            micro = FullRevision.IMPLICIT_MICRO_REV,
            preview = FullRevision.NOT_A_PREVIEW;

        if (revisionNode != null) {
            if (com.android.tools.idea.sdk.remote.internal.packages.PackageParserUtils
                  .findChildElement(revisionNode, SdkRepoConstants.NODE_MAJOR_REV) != null) {
                // <revision> has a <major> sub-element, so it's a repository XSD >= 7.
                major = com.android.tools.idea.sdk.remote.internal.packages.PackageParserUtils
                  .getXmlInt(revisionNode, SdkRepoConstants.NODE_MAJOR_REV, FullRevision.MISSING_MAJOR_REV);
                minor = com.android.tools.idea.sdk.remote.internal.packages.PackageParserUtils
                  .getXmlInt(revisionNode, SdkRepoConstants.NODE_MINOR_REV, FullRevision.IMPLICIT_MINOR_REV);
                micro = com.android.tools.idea.sdk.remote.internal.packages.PackageParserUtils
                  .getXmlInt(revisionNode, SdkRepoConstants.NODE_MICRO_REV, FullRevision.IMPLICIT_MICRO_REV);
                preview = com.android.tools.idea.sdk.remote.internal.packages.PackageParserUtils
                  .getXmlInt(revisionNode, SdkRepoConstants.NODE_PREVIEW, FullRevision.NOT_A_PREVIEW);
            } else {
                try {
                    String majorStr = revisionNode.getTextContent().trim();
                    major = Integer.parseInt(majorStr);
                } catch (Exception e) {
                }
            }
        }

        return new FullRevision(major, minor, micro, preview);
    }

    /**
     * Parses a no-preview revision element such as <revision>>.
     * This supports both the single-integer format as well as the full revision
     * format with major/minor/micro sub-elements.
     *
     * @param revisionNode The node to parse.
     * @return A new {@link NoPreviewRevision}. If parsing failed, major is set to
     *  {@link FullRevision#MISSING_MAJOR_REV}.
     */
    public static NoPreviewRevision parseNoPreviewRevisionElement(Node revisionNode) {
        // This needs to support two modes:
        // - For addon XSD >= 6, <revision> contains sub-elements such as <major> or <minor>.
        // - Otherwise for addon XSD < 6, <revision> contains an integer.
        // The <major> element is mandatory, so it's easy to distinguish between both cases.
        int major = FullRevision.MISSING_MAJOR_REV,
            minor = FullRevision.IMPLICIT_MINOR_REV,
            micro = FullRevision.IMPLICIT_MICRO_REV;

        if (revisionNode != null) {
            if (com.android.tools.idea.sdk.remote.internal.packages.PackageParserUtils
                  .findChildElement(revisionNode, SdkRepoConstants.NODE_MAJOR_REV) != null) {
                // <revision> has a <major> sub-element, so it's a repository XSD >= 7.
                major = com.android.tools.idea.sdk.remote.internal.packages.PackageParserUtils
                  .getXmlInt(revisionNode, SdkRepoConstants.NODE_MAJOR_REV, FullRevision.MISSING_MAJOR_REV);
                minor = com.android.tools.idea.sdk.remote.internal.packages.PackageParserUtils
                  .getXmlInt(revisionNode, SdkRepoConstants.NODE_MINOR_REV, FullRevision.IMPLICIT_MINOR_REV);
                micro = com.android.tools.idea.sdk.remote.internal.packages.PackageParserUtils
                  .getXmlInt(revisionNode, SdkRepoConstants.NODE_MICRO_REV, FullRevision.IMPLICIT_MICRO_REV);
            } else {
                try {
                    String majorStr = revisionNode.getTextContent().trim();
                    major = Integer.parseInt(majorStr);
                } catch (Exception e) {
                }
            }
        }

        return new NoPreviewRevision(major, minor, micro);
    }

    /**
     * Returns the first child element with the given XML local name and the same NS URI.
     * If xmlLocalName is null, returns the very first child element.
     */
    public static Node findChildElement(Node node, String xmlLocalName) {
        if (node != null) {
            String nsUri = node.getNamespaceURI();
            for(Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    String nsUriChild = child.getNamespaceURI();
                    if ((nsUri == null && nsUriChild == null) ||
                            (nsUri != null && nsUri.equals(nsUriChild))) {
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
     * @param node The XML <em>parent</em> node to parse.
     * @param xmlLocalName The XML local name to find in the parent node.
     * @return The text content of the element. Returns an empty string whether the element
     *         is missing or empty, so you can't tell the difference.
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
     * @param node The XML <em>parent</em> node to parse.
     * @param xmlLocalName The XML local name to find in the parent node.
     * @param defaultValue A default value to return if the element is missing.
     * @return The text content of the element
     *         or the defaultValue if the element is missing or empty.
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
     * @param node The XML <em>parent</em> node to parse.
     * @param xmlLocalName The XML local name to find in the parent node.
     * @return The text content of the element. Returns null when the element is missing.
     *         Returns an empty string whether the element is present but empty.
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
        } catch (NumberFormatException e) {
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
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Retrieve an attribute which value must match one of the given enums using a
     * case-insensitive name match.
     *
     * Returns defaultValue if the attribute does not exist or its value does not match
     * the given enum values.
     */
    public static Object getEnumAttribute(
            Node archiveNode,
            String attrName,
            Object[] values,
            Object defaultValue) {

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

    /**
     * Utility method that returns a property from a {@link Properties} object.
     * Returns the default value if props is null or if the property is not defined.
     *
     * @param props The {@link Properties} to search into.
     *   If null, the default value is returned.
     * @param propKey The name of the property. Must not be null.
     * @param defaultValue The default value to return if {@code props} is null or if the
     *   key is not found. Can be null.
     * @return The string value of the given key in the properties, or null if the key
     *   isn't found or if {@code props} is null.
     */
    @Nullable
    public static String getProperty(
            @Nullable Properties props,
            @NonNull String propKey,
            @Nullable String defaultValue) {
        if (props == null) {
            return defaultValue;
        }
        return props.getProperty(propKey, defaultValue);
    }

    /**
     * Utility method that returns an integer property from a {@link Properties} object.
     * Returns the default value if props is null or if the property is not defined or
     * cannot be parsed to an integer.
     *
     * @param props The {@link Properties} to search into.
     *   If null, the default value is returned.
     * @param propKey The name of the property. Must not be null.
     * @param defaultValue The default value to return if {@code props} is null or if the
     *   key is not found. Can be null.
     * @return The integer value of the given key in the properties, or the {@code defaultValue}.
     */
    public static int getPropertyInt(
            @Nullable Properties props,
            @NonNull String propKey,
            int defaultValue) {
        String s = props != null ? props.getProperty(propKey, null) : null;
        if (s != null) {
            try {
                return Integer.parseInt(s);
            } catch (Exception ignore) {}
        }
        return defaultValue;
    }

    /**
     * Utility method to parse the {@link PkgProps#PKG_REVISION} property as a full
     * revision (major.minor.micro.preview).
     *
     * @param props The properties to parse.
     * @return A {@link FullRevision} or null if there is no such property or it couldn't be parsed.
     * @param propKey The name of the property. Must not be null.
     */
    @Nullable
    public static FullRevision getPropertyFull(
            @Nullable Properties props,
            @NonNull String propKey) {
        String revStr = getProperty(props, propKey, null);

        FullRevision rev = null;
        if (revStr != null) {
            try {
                rev = FullRevision.parseRevision(revStr);
            } catch (NumberFormatException ignore) {}
        }

        return rev;
    }

    /**
     * Utility method to parse the {@link PkgProps#PKG_REVISION} property as a major
     * revision (major integer, no minor/micro/preview parts.)
     *
     * @param props The properties to parse.
     * @return A {@link MajorRevision} or null if there is no such property or it couldn't be parsed.
     * @param propKey The name of the property. Must not be null.
     */
    @Nullable
    public static MajorRevision getPropertyMajor(
            @Nullable Properties props,
            @NonNull String propKey) {
        String revStr = getProperty(props, propKey, null);

        MajorRevision rev = null;
        if (revStr != null) {
            try {
                rev = MajorRevision.parseRevision(revStr);
            } catch (NumberFormatException ignore) {}
        }

        return rev;
    }

    /**
     * Utility method to parse the {@link PkgProps#PKG_REVISION} property as a no-preview
     * revision (major.minor.micro integers but no preview part.)
     *
     * @param props The properties to parse.
     * @return A {@link NoPreviewRevision} or
     *         null if there is no such property or it couldn't be parsed.
     * @param propKey The name of the property. Must not be null.
     */
    @Nullable
    public static NoPreviewRevision getPropertyNoPreview(
            @Nullable Properties props,
            @NonNull String propKey) {
        String revStr = getProperty(props, propKey, null);

        NoPreviewRevision rev = null;
        if (revStr != null) {
            try {
                rev = NoPreviewRevision.parseRevision(revStr);
            } catch (NumberFormatException ignore) {}
        }

        return rev;
    }

}
