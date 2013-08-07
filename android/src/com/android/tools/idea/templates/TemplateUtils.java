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

import com.android.ide.common.sdk.SdkVersionInfo;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkManager;
import com.android.sdklib.repository.PkgProps;
import com.android.utils.SparseArray;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


/** Utility methods for ADT */
@SuppressWarnings("restriction") // WST API
public class TemplateUtils {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.templates.DomUtilities");

  /**
  * Creates a Java class name out of the given string, if possible. For
  * example, "My Project" becomes "MyProject", "hello" becomes "Hello",
  * "Java's" becomes "Java", and so on.
  *
  * @param string the string to be massaged into a Java class
  * @return the string as a Java class, or null if a class name could not be
  *         extracted
  */
  @Nullable
  public static String extractClassName(@NotNull String string) {
    StringBuilder sb = new StringBuilder(string.length());
    int n = string.length();

    int i = 0;
    for (; i < n; i++) {
      char c = Character.toUpperCase(string.charAt(i));
      if (Character.isJavaIdentifierStart(c)) {
        sb.append(c);
        i++;
        break;
      }
    }
    if (sb.length() > 0) {
      for (; i < n; i++) {
        char c = string.charAt(i);
        if (Character.isJavaIdentifierPart(c)) {
          sb.append(c);
        }
      }

      return sb.toString();
    }

    return null;
  }

  /**
  * Strips the given suffix from the given string, provided that the string ends with
  * the suffix.
  *
  * @param string the full string to strip from
  * @param suffix the suffix to strip out
  * @return the string without the suffix at the end
  */
  public static String stripSuffix(@NotNull String string, @NotNull String suffix) {
    if (string.endsWith(suffix)) {
      return string.substring(0, string.length() - suffix.length());
    }

    return string;
  }

  /**
  * Converts a CamelCase word into an underlined_word
  *
  * @param string the CamelCase version of the word
  * @return the underlined version of the word
  */
  public static String camelCaseToUnderlines(String string) {
    if (string.isEmpty()) {
      return string;
    }

    StringBuilder sb = new StringBuilder(2 * string.length());
    int n = string.length();
    boolean lastWasUpperCase = Character.isUpperCase(string.charAt(0));
    for (int i = 0; i < n; i++) {
      char c = string.charAt(i);
      boolean isUpperCase = Character.isUpperCase(c);
      if (isUpperCase && !lastWasUpperCase) {
        sb.append('_');
      }
      lastWasUpperCase = isUpperCase;
      c = Character.toLowerCase(c);
      sb.append(c);
    }

    return sb.toString();
  }

  /**
  * Converts an underlined_word into a CamelCase word
  *
  * @param string the underlined word to convert
  * @return the CamelCase version of the word
  */
  public static String underlinesToCamelCase(String string) {
    StringBuilder sb = new StringBuilder(string.length());
    int n = string.length();

    int i = 0;
    boolean upcaseNext = true;
    for (; i < n; i++) {
      char c = string.charAt(i);
      if (c == '_') {
        upcaseNext = true;
      } else {
        if (upcaseNext) {
          c = Character.toUpperCase(c);
        }
        upcaseNext = false;
        sb.append(c);
      }
    }

    return sb.toString();
  }

  /**
  * Returns a string label for the given target, of the form
  * "API 16: Android 4.1 (Jelly Bean)".
  *
  * @param target the target to generate a string from
  * @return a suitable display string
  */
  @NotNull
  public static String getTargetLabel(@NotNull IAndroidTarget target) {
    if (target.isPlatform()) {
      AndroidVersion version = target.getVersion();
      String codename = target.getProperty(PkgProps.PLATFORM_CODENAME);
      String release = target.getProperty("ro.build.version.release"); //$NON-NLS-1$
      if (codename != null) {
        return String.format("API %1$d: Android %2$s (%3$s)",
                             version.getApiLevel(),
                             release,
                             codename);
      }
      return String.format("API %1$d: Android %2$s", version.getApiLevel(),
                           release);
    }

    return String.format("%1$s (API %2$s)", target.getFullName(),
                         target.getVersion().getApiString());
  }


  /**
  * Returns a list of known API names
  *
  * @return a list of string API names, starting from 1 and up through the
  *         maximum known versions (with no gaps)
  */
  public static String[] getKnownVersions() {
    final SdkManager sdkManager = AndroidSdkUtils.tryToChooseAndroidSdk();
    assert sdkManager != null;
    int max = SdkVersionInfo.HIGHEST_KNOWN_API;
    IAndroidTarget[] targets = sdkManager.getTargets();
    SparseArray<IAndroidTarget> apiTargets = null;
    for (IAndroidTarget target : targets) {
      if (target.isPlatform()) {
        AndroidVersion version = target.getVersion();
        if (!version.isPreview()) {
          int apiLevel = version.getApiLevel();
          max = Math.max(max, apiLevel);
          if (apiLevel > SdkVersionInfo.HIGHEST_KNOWN_API) {
            if (apiTargets == null) {
              apiTargets = new SparseArray<IAndroidTarget>();
            }
            apiTargets.put(apiLevel, target);
          }
        }
      }
    }

    String[] versions = new String[max];
    for (int api = 1; api <= max; api++) {
      String name = SdkVersionInfo.getAndroidName(api);
      if (name == null) {
        if (apiTargets != null) {
          IAndroidTarget target = apiTargets.get(api);
          if (target != null) {
            name = getTargetLabel(target);
          }
        }
        if (name == null) {
          name = String.format("API %1$d", api);
        }
      }
      versions[api-1] = name;
    }

    return versions;
  }

  /**
  * Lists the files of the given directory and returns them as an array which
  * is never null. This simplifies processing file listings from for each
  * loops since {@link File#listFiles} can return null. This method simply
  * wraps it and makes sure it returns an empty array instead if necessary.
  *
  * @param dir the directory to list
  * @return the children, or empty if it has no children, is not a directory,
  *         etc.
  */
  @NotNull
  public static File[] listFiles(File dir) {
    File[] files = dir.listFiles();
    if (files != null) {
      return files;
    } else {
      return new File[0];
    }
  }

  /**
  * Returns the element children of the given element
  *
  * @param element the parent element
  * @return a list of child elements, possibly empty but never null
  */
  @NotNull
  public static List<Element> getChildren(@NotNull Element element) {
    // Convenience to avoid lots of ugly DOM access casting
    NodeList children = element.getChildNodes();
    // An iterator would have been more natural (to directly drive the child list
    // iteration) but iterators can't be used in enhanced for loops...
    List<Element> result = new ArrayList<Element>(children.getLength());
    for (int i = 0, n = children.getLength(); i < n; i++) {
      Node node = children.item(i);
      if (node.getNodeType() == Node.ELEMENT_NODE) {
        Element child = (Element) node;
        result.add(child);
      }
    }

    return result;
  }
}
