/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.android.dom.manifest;

import com.android.SdkConstants;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.XmlName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AndroidManifestUtils {
  private AndroidManifestUtils() {
  }

  @Nullable
  public static String getStyleableNameForElement(@NotNull ManifestElement element) {
    if (element instanceof CompatibleScreensScreen) {
      return "AndroidManifestCompatibleScreensScreen";
    }
    return null;
  }

  @NotNull
  public static String getStyleableNameByTagName(@NotNull String tagName) {
    String prefix = "AndroidManifest";
    if (tagName.equals("manifest")) return prefix;
    String[] parts = tagName.split("-");
    StringBuilder builder = new StringBuilder(prefix);
    for (String part : parts) {
      char first = part.charAt(0);
      String remained = part.substring(1);
      builder.append(Character.toUpperCase(first)).append(remained);
    }
    return builder.toString();
  }

  @NotNull
  public static String[] getStaticallyDefinedAttrs(@NotNull ManifestElement element) {
    List<String> strings = new ArrayList<String>();
    if (element instanceof ManifestElementWithName) {
      strings.add("name");
    }
    if (element instanceof ApplicationComponent || element instanceof Application) {
      strings.add("label");
    }
    if (element instanceof Application) {
      strings.add("name");
      strings.add("manageSpaceActivity");
      strings.add("backupAgent");
      strings.add("debuggable");
    }
    if (element instanceof Provider) {
      strings.add("authorities");
    }
    if (element instanceof Instrumentation) {
      strings.add("targetPackage");
    }
    else if (element instanceof UsesSdk) {
      strings.add("minSdkVersion");
      strings.add("targetSdkVersion");
      strings.add("maxSdkVersion");
    }
    return ArrayUtil.toStringArray(strings);
  }

  @NotNull
  public static String[] getStaticallyDefinedSubtags(@NotNull ManifestElement element) {
    List<String> strings = new ArrayList<String>();
    if (element instanceof Manifest) {
      Collections.addAll(strings, "application", "instrumentation", "permission", "permission-group",
                         "permission-tree", "uses-permission", "compatible-screens", "uses-sdk", "uses-feature");
    }
    else if (element instanceof Application) {
      Collections.addAll(strings, "activity", "activity-alias", "service", "provider", "receiver", "uses-library", "meta-data");
    }
    else if (element instanceof Activity || element instanceof ActivityAlias || element instanceof Receiver || element instanceof Service) {
      Collections.addAll(strings, "intent-filter", "meta-data");
    }
    else if (element instanceof Provider) {
      Collections.addAll(strings, "meta-data");
    }
    else if (element instanceof IntentFilter) {
      strings.add("action");
      strings.add("category");
    }
    return ArrayUtil.toStringArray(strings);
  }

  @Nullable
  public static String getTagNameByStyleableName(@NotNull String styleableName) {
    String prefix = "AndroidManifest";
    if (!styleableName.startsWith(prefix)) {
      return null;
    }
    String remained = styleableName.substring(prefix.length());
    if (remained.length() == 0) return "manifest";
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < remained.length(); i++) {
      char c = remained.charAt(i);
      if (builder.length() > 0 && Character.isUpperCase(c)) {
        builder.append('-');
      }
      builder.append(Character.toLowerCase(c));
    }
    return builder.toString();
  }

  public static boolean isRequiredAttribute(XmlName attrName, DomElement element) {
    if (element instanceof CompatibleScreensScreen &&
        SdkConstants.NS_RESOURCES.equals(attrName.getNamespaceKey())) {
      final String localName = attrName.getLocalName();
      return "screenSize".equals(localName) || "screenDensity".equals(localName);
    }
    return false;
  }
}
