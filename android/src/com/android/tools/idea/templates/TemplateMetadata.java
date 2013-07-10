/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.templates;

import com.android.build.gradle.BasePlugin;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.*;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.android.tools.idea.templates.Template.*;

/** An ADT template along with metadata */
public class TemplateMetadata {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.templates.TemplateMetadata");

  public static final String ATTR_PARENT_ACTIVITY_CLASS = "parentActivityClass";
  public static final String ATTR_ACTIVITY_TITLE = "activityTitle";
  public static final String ATTR_IS_LAUNCHER = "isLauncher";
  public static final String ATTR_IS_LIBRARY_MODULE = "isLibraryProject";
  public static final String ATTR_CREATE_ICONS = "createIcons";
  public static final String ATTR_COPY_ICONS = "copyIcons";
  public static final String ATTR_TARGET_API = "targetApi";
  public static final String ATTR_MIN_API = "minApi";
  public static final String ATTR_MIN_BUILD_API = "minBuildApi";
  public static final String ATTR_BUILD_API = "buildApi";
  public static final String ATTR_REVISION = "revision";
  public static final String ATTR_MIN_API_LEVEL = "minApiLevel";
  public static final String ATTR_PACKAGE_NAME = "packageName";
  public static final String ATTR_APP_TITLE = "appTitle";
  public static final String ATTR_BASE_THEME = "baseTheme";
  public static final String ATTR_IS_NEW_PROJECT = "isNewProject";
  public static final String ATTR_IS_GRADLE = "isGradle";
  public static final String ATTR_TOP_OUT = "topOut";
  public static final String ATTR_PROJECT_OUT = "projectOut";
  public static final String ATTR_SRC_OUT = "srcOut";
  public static final String ATTR_RES_OUT = "resOut";
  public static final String ATTR_MANIFEST_OUT = "manifestOut";
  public static final String ATTR_MAVEN_URL = "mavenUrl";
  public static final String ATTR_BUILD_TOOLS_VERSION = "buildToolsVersion";
  public static final String ATTR_GRADLE_PLUGIN_VERSION = "gradlePluginVersion";
  public static final String ATTR_V4_SUPPORT_LIBRARY_VERSION = "v4SupportLibraryVersion";
  public static final String ATTR_GRADLE_VERSION = "gradleVersion";

  public static final String V4_SUPPORT_LIBRARY_VERSION = "13.0.+";
  public static final String GRADLE_PLUGIN_VERSION = "0.5.+";
  public static final String GRADLE_VERSION = BasePlugin.GRADLE_MIN_VERSION;
  public static final String GRADLE_DISTRIBUTION_URL = "http://services.gradle.org/distributions/gradle-" +
                                                       GRADLE_VERSION + "-bin.zip";

  private final Document myDocument;
  private final Map<String, Parameter> myParameterMap;

  TemplateMetadata(@NotNull Document document) {
    myDocument = document;

    NodeList parameters = myDocument.getElementsByTagName(TAG_PARAMETER);
    myParameterMap = new LinkedHashMap<String, Parameter>(parameters.getLength());
    for (int index = 0, max = parameters.getLength(); index < max; index++) {
      Element element = (Element) parameters.item(index);
      Parameter parameter = new Parameter(this, element);
      if (parameter.id != null) {
        myParameterMap.put(parameter.id, parameter);
      }
    }
  }

  @Nullable
  public String getTitle() {
    return getAttrNonEmpty(ATTR_NAME);
  }

  @Nullable
  public String getDescription() {
    return getAttrNonEmpty(ATTR_DESCRIPTION);
  }

  public int getMinSdk() {
    return getInteger(ATTR_MIN_API, 1);
  }

  public int getMinBuildApi() {
    return getInteger(ATTR_MIN_BUILD_API, 1);
  }

  public int getRevision() {
    return getInteger(ATTR_REVISION, 1);
  }

  @Nullable
  public String getThumbnailPath() {
    // Apply selector logic. Pick the thumb first thumb that satisfies the largest number
    // of conditions.
    NodeList thumbs = myDocument.getElementsByTagName(TAG_THUMB);
    if (thumbs.getLength() == 0) {
      return null;
    }


    int bestMatchCount = 0;
    Element bestMatch = null;

    for (int i = 0, n = thumbs.getLength(); i < n; i++) {
      Element thumb = (Element) thumbs.item(i);

      NamedNodeMap attributes = thumb.getAttributes();
      if (bestMatch == null && attributes.getLength() == 0) {
        bestMatch = thumb;
      } else if (attributes.getLength() <= bestMatchCount) {
        // Already have a match with this number of attributes, no point checking
        continue;
      } else {
        boolean match = true;
        for (int j = 0, max = attributes.getLength(); j < max; j++) {
          Attr attribute = (Attr) attributes.item(j);
          Parameter parameter = myParameterMap.get(attribute.getName());
          if (parameter == null) {
            LOG.warn("Unexpected parameter in template thumbnail: " +
                          attribute.getName());
            continue;
          }
          String thumbNailValue = attribute.getValue();
          // TODO: have current value passed in?
          String editedValue = "";
          if (!thumbNailValue.equals(editedValue)) {
            match = false;
            break;
          }
        }
        if (match) {
          bestMatch = thumb;
          bestMatchCount = attributes.getLength();
        }
      }
    }

    if (bestMatch != null) {
      NodeList children = bestMatch.getChildNodes();
      for (int i = 0, n = children.getLength(); i < n; i++) {
        Node child = children.item(i);
        if (child.getNodeType() == Node.TEXT_NODE) {
          return child.getNodeValue().trim();
        }
      }
    }

    return null;
  }

  public boolean isSupported() {
    String versionString = myDocument.getDocumentElement().getAttribute(ATTR_FORMAT);
    if (versionString != null && !versionString.isEmpty()) {
      try {
        int version = Integer.parseInt(versionString);
        return version <= CURRENT_FORMAT;
      } catch (NumberFormatException nufe) {
        return false;
      }
    }

    // Older templates without version specified: supported
    return true;
  }

  /** Returns the list of available parameters */
  @NotNull
  public Collection<Parameter> getParameters() {
    return myParameterMap.values();
  }

  /**
   * Returns the parameter of the given id, or null if not found
   *
   * @param id the id of the target parameter
   * @return the corresponding parameter, or null if not found
   */
  @Nullable
  public Parameter getParameter(@NotNull String id) {
    return myParameterMap.get(id);
  }

  @Nullable
  private String getAttrNonEmpty(@NotNull String attrName) {
    String attr = myDocument.getDocumentElement().getAttribute(attrName);
    if (attr != null && !attr.isEmpty()) {
      return attr;
    }
    return null;
  }

  private int getInteger(@NotNull String attrName, int defaultValue) {
    try {
      return Integer.parseInt(myDocument.getDocumentElement().getAttribute(attrName));
    } catch (NumberFormatException nufe) {
      // Templates aren't allowed to contain codenames, should always be an integer
      //LOG.warn(nufe);
      return defaultValue;
    } catch (RuntimeException e) {
      return defaultValue;
    }
  }
}
