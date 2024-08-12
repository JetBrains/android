/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.manifest;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.xml.AndroidManifest.ATTRIBUTE_PACKAGE;
import static com.android.xml.AndroidManifest.NODE_ACTIVITY;
import static com.android.xml.AndroidManifest.NODE_ACTIVITY_ALIAS;
import static com.android.xml.AndroidManifest.NODE_APPLICATION;
import static com.android.xml.AndroidManifest.NODE_INSTRUMENTATION;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.run.DefaultActivityLocatorCompat;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.IOException;
import java.io.InputStream;
import javax.annotation.Nullable;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/** Parses manifests from input streams. */
public class ManifestParser {
  private static final Logger log = Logger.getInstance(ManifestParser.class);

  public static ManifestParser getInstance(Project project) {
    return project.getService(ManifestParser.class);
  }

  /** Container class for common manifest attributes required by the blaze plugin. */
  public static class ParsedManifest {
    /**
     * Package name of the application and should always be non-null in complete manifests. The
     * package name can be null for manifests that are meant to be merged together with others to
     * form the final "merged" manifest.
     */
    @Nullable public final String packageName;

    /**
     * Fqcn of instrumentation classes. An empty list indicates there are no instrumentation classes
     * declared.
     */
    public final ImmutableList<String> instrumentationClassNames;

    /**
     * Name of the default activity to launch during startup. A null default activity name indicates
     * there isn't one present. This is common for testing and instrumentation APKs, where there
     * isn't an activity to launch from the APK itself.
     */
    @Nullable public final String defaultActivityClassName;

    public ParsedManifest(
        @Nullable String packageName,
        ImmutableList<String> instrumentationClassNames,
        @Nullable String defaultActivityClassName) {
      this.packageName = packageName;
      this.instrumentationClassNames = instrumentationClassNames;
      this.defaultActivityClassName = defaultActivityClassName;
    }
  }

  /**
   * Returns parsed manifest from the given input stream. Returns null if the manifest is invalid.
   *
   * <p>An invalid manifest is anything that could not be parsed by the parser, such as a malformed
   * manifest.
   */
  @Nullable
  public static ParsedManifest parseManifestFromInputStream(InputStream inputStream)
      throws IOException {
    Element manifestRootElement = getManifestRootElementFromInputStream(inputStream);
    if (manifestRootElement == null) {
      return null;
    }
    return parseManifestElement(manifestRootElement);
  }

  @Nullable
  private static Element getManifestRootElementFromInputStream(InputStream input)
      throws IOException {
    try {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setNamespaceAware(true);
      DocumentBuilder db = dbf.newDocumentBuilder();
      Document doc = db.parse(input);
      return doc.getDocumentElement();
    } catch (ParserConfigurationException e) {
      log.warn("Error in manifest parser: " + e.getMessage());
    } catch (SAXException e) {
      log.warn("Could not parse manifest XML: " + e.getMessage());
    }
    return null;
  }

  @Nullable
  private static ParsedManifest parseManifestElement(Element manifestRootElement) {
    String packageName =
        Strings.emptyToNull(manifestRootElement.getAttributeNS(null, ATTRIBUTE_PACKAGE));

    ImmutableList.Builder<String> instrumentationClassNames = ImmutableList.builder();
    ImmutableList.Builder<Element> activities = new ImmutableList.Builder<>();
    ImmutableList.Builder<Element> activityAliases = new ImmutableList.Builder<>();
    try {
      Node node = manifestRootElement.getFirstChild();
      while (node != null) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
          String nodeName = node.getNodeName();

          if (NODE_APPLICATION.equals(nodeName)) {
            // Extract <activity> and <activity-alias> elements from <application>
            Node child = node.getFirstChild();
            while (child != null) {
              if (child.getNodeType() == Node.ELEMENT_NODE) {
                String childNodeName = child.getNodeName();

                if (NODE_ACTIVITY.equals(childNodeName)) {
                  activities.add((Element) child);
                } else if (NODE_ACTIVITY_ALIAS.equals(childNodeName)) {
                  activityAliases.add((Element) child);
                }
              }
              child = child.getNextSibling();
            }
          } else if (NODE_INSTRUMENTATION.equals(nodeName)) {
            // Extract instrumentation class names from <instrumentation>
            String name = ((Element) node).getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (name != null) {
              instrumentationClassNames.add(name);
            }
          }
        }

        node = node.getNextSibling();
      }
    } catch (DOMException e) {
      log.warn("Could not parse manifest XML: " + e.getMessage());
      return null;
    }

    String defaultActivityClassName =
        DefaultActivityLocatorCompat.computeDefaultActivity(
            DefaultActivityLocatorCompat.ActivityWrapper.get(
                activities.build(), activityAliases.build()));

    return new ParsedManifest(
        packageName, instrumentationClassNames.build(), defaultActivityClassName);
  }
}
