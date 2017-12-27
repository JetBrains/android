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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.uibuilder.palette.PaletteMode;
import com.android.tools.idea.uibuilder.property.NlPropertiesPanel.PropertiesViewMode;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableMap;
import com.google.wireless.android.sdk.stats.*;
import com.google.wireless.android.sdk.stats.LayoutPaletteEvent.ViewGroup;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.*;

public class UsageTrackerUtil {

  private static final Pattern STYLE_PATTERN = Pattern.compile("style=\"(.*)\"");
  private static final Pattern INPUT_STYLE_PATTERN = Pattern.compile("android:inputType=\"(.*)\"");
  private static final Pattern ORIENTATION_PATTERN = Pattern.compile("android:orientation=\"(.*)\"");
  private static final Map<String, LayoutPaletteEvent.ViewOption> PALETTE_VIEW_OPTION_MAP =
    ImmutableMap.<String, LayoutPaletteEvent.ViewOption>builder()
      .put("textPassword", LayoutPaletteEvent.ViewOption.PASSWORD)
      .put("numberPassword", LayoutPaletteEvent.ViewOption.PASSWORD_NUMERIC)
      .put("textEmailAddress", LayoutPaletteEvent.ViewOption.EMAIL)
      .put("phone", LayoutPaletteEvent.ViewOption.PHONE)
      .put("textPostalAddress", LayoutPaletteEvent.ViewOption.POSTAL_ADDRESS)
      .put("textMultiLine", LayoutPaletteEvent.ViewOption.MULTILINE_TEXT)
      .put("time", LayoutPaletteEvent.ViewOption.TIME_EDITOR)
      .put("date", LayoutPaletteEvent.ViewOption.DATE_EDITOR)
      .put("number", LayoutPaletteEvent.ViewOption.NUMBER)
      .put("numberSigned", LayoutPaletteEvent.ViewOption.SIGNED_NUMBER)
      .put("numberDecimal", LayoutPaletteEvent.ViewOption.DECIMAL_NUMBER)
      .build();

  // Identifies a custom tag name or attribute name i.e. a placeholder for a name we do NOT want to log.
  @VisibleForTesting
  static final String CUSTOM_NAME = "CUSTOM";

  // Prevent instantiation
  private UsageTrackerUtil() {
  }

  @NotNull
  static AndroidAttribute convertAttribute(@NotNull NlProperty property) {
    AndroidFacet facet = property.getModel().getFacet();
    AttributeDefinition definition = property.getDefinition();
    String libraryName = definition != null ? definition.getLibraryName() : null;
    AndroidAttribute.AttributeNamespace namespace = convertNamespace(property.getNamespace());

    return AndroidAttribute.newBuilder()
      .setAttributeName(convertAttributeName(property.getName(), namespace, libraryName, facet))
      .setAttributeNamespace(namespace)
      .build();
  }

  @NotNull
  static AndroidAttribute convertAttribute(@NotNull String attributeName, @NotNull AndroidFacet facet) {
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
  @VisibleForTesting
  static AndroidAttribute.AttributeNamespace convertNamespace(@Nullable String namespace) {
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
  static ViewGroup convertGroupName(@NotNull String groupName) {
    switch (groupName) {
      case "All":
        return ViewGroup.ALL_GROUPS;
      case "Widgets":
        return ViewGroup.WIDGETS;
      case "Text":
        return ViewGroup.TEXT;
      case "Layouts":
        return ViewGroup.LAYOUTS;
      case "Containers":
        return ViewGroup.CONTAINERS;
      case "Images":
        return ViewGroup.IMAGES;
      case "Date":
        return ViewGroup.DATES;
      case "Transitions":
        return ViewGroup.TRANSITIONS;
      case "Advanced":
        return ViewGroup.ADVANCED;
      case "Google":
        return ViewGroup.GOOGLE;
      case "Design":
        return ViewGroup.DESIGN;
      case "AppCompat":
        return ViewGroup.APP_COMPAT;
      default:
        return ViewGroup.CUSTOM;
    }
  }

  @NotNull
  static LayoutPaletteEvent.ViewOption convertViewOption(@NotNull String tagName, @NotNull String representation) {
    switch (tagName) {
      case PROGRESS_BAR:
        return convertProgressBarViewOption(representation);

      case SEEK_BAR:
        return convertSeekBarViewOption(representation);

      case EDIT_TEXT:
        return convertEditTextViewOption(representation);

      case LINEAR_LAYOUT:
        return convertLinearLayoutViewOption(representation);

      default:
        return LayoutPaletteEvent.ViewOption.NORMAL;
    }
  }

  @NotNull
  static LayoutPaletteEvent.ViewType convertPaletteMode(@NotNull PaletteMode paletteMode) {
    switch (paletteMode) {
      case LARGE_ICONS:
        return LayoutPaletteEvent.ViewType.LARGE_IONS;
      case SMALL_ICONS:
        return LayoutPaletteEvent.ViewType.SMALL_ICONS;
      case ICON_AND_NAME:
      default:
        return LayoutPaletteEvent.ViewType.ICON_AND_NAME;
    }
  }

  @NotNull
  static LayoutAttributeChangeEvent.ViewType convertPropertiesMode(@NotNull PropertiesViewMode propertiesMode) {
    switch (propertiesMode) {
      case TABLE:
        return LayoutAttributeChangeEvent.ViewType.PROPERTY_TABLE;
      case INSPECTOR:
      default:
        return LayoutAttributeChangeEvent.ViewType.INSPECTOR;
    }
  }

  @NotNull
  static SearchOption convertFilterMatches(int matches) {
    if (matches < 1) {
      return SearchOption.NONE;
    }
    if (matches > 1) {
      return SearchOption.MULTIPLE_MATCHES;
    }
    return SearchOption.SINGLE_MATCH;
  }

  @NotNull
  @VisibleForTesting
  static LayoutPaletteEvent.ViewOption convertProgressBarViewOption(@NotNull String representation) {
    String styleValue = getStyleValue(representation);
    if (styleValue == null || styleValue.equals("?android:attr/progressBarStyle")) {
      return LayoutPaletteEvent.ViewOption.NORMAL;
    }
    if (styleValue.equals("?android:attr/progressBarStyleHorizontal")) {
      return LayoutPaletteEvent.ViewOption.HORIZONTAL_PROGRESS_BAR;
    }
    return LayoutPaletteEvent.ViewOption.CUSTOM_OPTION;
  }

  @NotNull
  @VisibleForTesting
  static LayoutPaletteEvent.ViewOption convertSeekBarViewOption(@NotNull String representation) {
    String styleValue = getStyleValue(representation);
    if (styleValue == null) {
      return LayoutPaletteEvent.ViewOption.NORMAL;
    }
    if (styleValue.equals("@style/Widget.AppCompat.SeekBar.Discrete")) {
      return LayoutPaletteEvent.ViewOption.DISCRETE_SEEK_BAR;
    }
    return LayoutPaletteEvent.ViewOption.CUSTOM_OPTION;
  }

  @NotNull
  @VisibleForTesting
  static LayoutPaletteEvent.ViewOption convertEditTextViewOption(@NotNull String representation) {
    Matcher matcher = INPUT_STYLE_PATTERN.matcher(representation);
    if (!matcher.find()) {
      return LayoutPaletteEvent.ViewOption.NORMAL;
    }
    LayoutPaletteEvent.ViewOption viewOption = PALETTE_VIEW_OPTION_MAP.get(matcher.group(1));
    return viewOption != null ? viewOption : LayoutPaletteEvent.ViewOption.CUSTOM_OPTION;
  }

  @NotNull
  @VisibleForTesting
  static LayoutPaletteEvent.ViewOption convertLinearLayoutViewOption(@NotNull String representation) {
    Matcher matcher = ORIENTATION_PATTERN.matcher(representation);
    if (!matcher.find()) {
      return LayoutPaletteEvent.ViewOption.HORIZONTAL_LINEAR_LAYOUT;
    }
    String orientation = matcher.group(1);
    if (orientation.equals("horizontal")) {
      return LayoutPaletteEvent.ViewOption.HORIZONTAL_LINEAR_LAYOUT;
    }
    if (orientation.equals("vertical")) {
      return LayoutPaletteEvent.ViewOption.VERTICAL_LINEAR_LAYOUT;
    }
    return LayoutPaletteEvent.ViewOption.CUSTOM_OPTION;
  }

  @NotNull
  @VisibleForTesting
  static String convertAttributeName(@NotNull String attributeName,
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
  @VisibleForTesting
  static AndroidView convertTagName(@NotNull String tagName) {
    tagName = acceptedGoogleTagNamespace(tagName) ? StringUtil.getShortName(tagName, '.') : CUSTOM_NAME;
    return AndroidView.newBuilder()
      .setTagName(tagName)
      .build();
  }

  @Nullable
  @VisibleForTesting
  static String getStyleValue(@NotNull String representation) {
    Matcher matcher = STYLE_PATTERN.matcher(representation);
    return matcher.find() ? matcher.group(1) : null;
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
    ResourceManager systemResourceManager = resourceManagers.getSystemResourceManager();
    if (systemResourceManager == null) {
      return new NamespaceAndLibraryNamePair(AndroidAttribute.AttributeNamespace.APPLICATION);
    }

    ResourceManager localResourceManager = resourceManagers.getLocalResourceManager();
    AttributeDefinitions localAttributeDefinitions = localResourceManager.getAttributeDefinitions();
    AttributeDefinitions systemAttributeDefinitions = systemResourceManager.getAttributeDefinitions();

    if (systemAttributeDefinitions != null && systemAttributeDefinitions.getAttributeNames().contains(attributeName)) {
      return new NamespaceAndLibraryNamePair(AndroidAttribute.AttributeNamespace.ANDROID);
    }
    if (localAttributeDefinitions == null) {
      return new NamespaceAndLibraryNamePair(AndroidAttribute.AttributeNamespace.APPLICATION);
    }
    AttributeDefinition definition = localAttributeDefinitions.getAttrDefByName(attributeName);
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
