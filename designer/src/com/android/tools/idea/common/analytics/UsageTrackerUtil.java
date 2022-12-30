/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.common.analytics;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.CONSTRAINT_LAYOUT_LIB_ARTIFACT_ID;
import static com.android.SdkConstants.FLEXBOX_LAYOUT_LIB_ARTIFACT_ID;
import static com.android.SdkConstants.TOOLS_NS_NAME_PREFIX;
import static com.android.SdkConstants.TOOLS_URI;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.AndroidAttribute;
import com.google.wireless.android.sdk.stats.AndroidView;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class UsageTrackerUtil {

  // Identifies a custom tag name or attribute name i.e. a placeholder for a name we do NOT want to log.
  @VisibleForTesting
  public static final String CUSTOM_NAME = "CUSTOM";

  // Prevent instantiation
  private UsageTrackerUtil() {
  }

  @NotNull
  public static AndroidAttribute convertAttribute(@NotNull String attributeName, @NotNull AndroidFacet facet) {
    AndroidAttribute.AttributeNamespace namespace = null;
    if (attributeName.startsWith(TOOLS_NS_NAME_PREFIX)) {
      namespace = AndroidAttribute.AttributeNamespace.TOOLS;
      attributeName = StringUtil.trimStart(attributeName, TOOLS_NS_NAME_PREFIX);
    }
    NamespaceAndLibraryNamePair lookup = lookupAttributeResource(facet, attributeName);
    if (namespace == null) {
      namespace = lookup.getNamespace();
    }
    return AndroidAttribute.newBuilder()
      .setAttributeName(convertAttributeName(attributeName, lookup.getNamespace(), lookup.getLibraryName(), facet))
      .setAttributeNamespace(namespace)
      .build();
  }

  @NotNull
  public static AndroidAttribute.AttributeNamespace convertNamespace(@Nullable String namespace) {
    if (StringUtil.isEmpty(namespace)) {
      return AndroidAttribute.AttributeNamespace.ANDROID;
    }
    switch (namespace) {
      case TOOLS_URI:
        return AndroidAttribute.AttributeNamespace.TOOLS;
      case ANDROID_URI:
        return AndroidAttribute.AttributeNamespace.ANDROID;
      default:
        return AndroidAttribute.AttributeNamespace.APPLICATION;
    }
  }

  @NotNull
  public static String convertAttributeName(@NotNull String attributeName,
                                     @NotNull AndroidAttribute.AttributeNamespace namespace,
                                     @Nullable String libraryName,
                                     @NotNull AndroidFacet facet) {
    switch (namespace) {
      case ANDROID:
        return attributeName;
      case APPLICATION:
        return libraryName != null && acceptedGoogleLibraryNamespace(libraryName) ? attributeName : CUSTOM_NAME;
      case TOOLS:
        NamespaceAndLibraryNamePair lookup = lookupAttributeResource(facet, attributeName);
        assert lookup.getNamespace() != AndroidAttribute.AttributeNamespace.TOOLS;
        return convertAttributeName(attributeName, lookup.getNamespace(), lookup.getLibraryName(), facet);
      default:
        return CUSTOM_NAME;
    }
  }

  @NotNull
  public static AndroidView convertTagName(@NotNull String tagName) {
    tagName = acceptedGoogleTagNamespace(tagName) ? StringUtil.getShortName(tagName, '.') : CUSTOM_NAME;
    return AndroidView.newBuilder()
      .setTagName(tagName)
      .build();
  }

  @VisibleForTesting
  static boolean acceptedGoogleLibraryNamespace(@NotNull String libraryName) {
    return libraryName.startsWith("com.android.") ||
           libraryName.startsWith("com.google.") ||

           // The following lines are temporary.
           // Remove these when we consistently get the full library names.
           // Currently the library names loaded by Intellij does NOT contain the package / group name.
           libraryName.startsWith(CONSTRAINT_LAYOUT_LIB_ARTIFACT_ID) ||
           libraryName.startsWith(FLEXBOX_LAYOUT_LIB_ARTIFACT_ID) ||
           libraryName.startsWith("design-") ||
           libraryName.startsWith("appcompat-v7-") ||
           libraryName.startsWith("cardview-v7-") ||
           libraryName.startsWith("gridlayout-v7") ||
           libraryName.startsWith("recyclerview-v7") ||
           libraryName.startsWith("coordinatorlayout-v7") ||
           libraryName.startsWith("play-services-maps-") ||
           libraryName.startsWith("play-services-ads-") ||
           libraryName.startsWith("leanback-v17-");
  }

  @VisibleForTesting
  static boolean acceptedGoogleTagNamespace(@NotNull String fullyQualifiedTagName) {
    return fullyQualifiedTagName.indexOf('.') < 0 ||
           fullyQualifiedTagName.startsWith("com.android.") ||
           fullyQualifiedTagName.startsWith("com.google.") ||
           fullyQualifiedTagName.startsWith("android.support.") ||
           fullyQualifiedTagName.startsWith("android.databinding.");
  }

  @NotNull
  @VisibleForTesting
  static NamespaceAndLibraryNamePair lookupAttributeResource(@NotNull AndroidFacet facet, @NotNull String attributeName) {
    ModuleResourceManagers resourceManagers = ModuleResourceManagers.getInstance(facet);
    ResourceManager frameworkResourceManager = resourceManagers.getFrameworkResourceManager();
    if (frameworkResourceManager == null) {
      return new NamespaceAndLibraryNamePair(AndroidAttribute.AttributeNamespace.APPLICATION);
    }

    ResourceManager localResourceManager = resourceManagers.getLocalResourceManager();
    AttributeDefinitions localAttributeDefinitions = localResourceManager.getAttributeDefinitions();
    AttributeDefinitions systemAttributeDefinitions = frameworkResourceManager.getAttributeDefinitions();

    if (systemAttributeDefinitions != null &&
        systemAttributeDefinitions.getAttrs().contains(ResourceReference.attr(ResourceNamespace.ANDROID, attributeName))) {
      return new NamespaceAndLibraryNamePair(AndroidAttribute.AttributeNamespace.ANDROID);
    }
    if (localAttributeDefinitions == null) {
      return new NamespaceAndLibraryNamePair(AndroidAttribute.AttributeNamespace.APPLICATION);
    }
    AttributeDefinition definition =
        localAttributeDefinitions.getAttrDefinition(ResourceReference.attr(ResourceNamespace.TODO(), attributeName));
    if (definition == null) {
      return new NamespaceAndLibraryNamePair(AndroidAttribute.AttributeNamespace.APPLICATION);
    }
    return new NamespaceAndLibraryNamePair(AndroidAttribute.AttributeNamespace.APPLICATION, definition.getLibraryName());
  }

  @VisibleForTesting
  static class NamespaceAndLibraryNamePair {
    private final AndroidAttribute.AttributeNamespace myNamespace;
    private final String myLibraryName;

    @VisibleForTesting
    NamespaceAndLibraryNamePair(@NotNull AndroidAttribute.AttributeNamespace namespace) {
      this(namespace, null);
    }

    @VisibleForTesting
    NamespaceAndLibraryNamePair(@NotNull AndroidAttribute.AttributeNamespace namespace, @Nullable String libraryName) {
      myNamespace = namespace;
      myLibraryName = libraryName;
    }

    @NotNull
    public AndroidAttribute.AttributeNamespace getNamespace() {
      return myNamespace;
    }

    @Nullable
    public String getLibraryName() {
      return myLibraryName;
    }
  }
}
