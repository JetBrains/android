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
package com.android.tools.idea.uibuilder.analytics;

import static com.android.SdkConstants.EDIT_TEXT;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.PROGRESS_BAR;
import static com.android.SdkConstants.SEEK_BAR;
import static com.android.tools.idea.common.analytics.UsageTrackerUtil.convertTagName;

import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.common.analytics.CommonUsageTracker;
import com.android.tools.idea.common.analytics.CommonUsageTrackerImpl;
import com.android.tools.idea.common.analytics.UsageTrackerUtil;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.wireless.android.sdk.stats.AndroidAttribute;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.LayoutAttributeChangeEvent;
import com.google.wireless.android.sdk.stats.LayoutEditorEvent;
import com.google.wireless.android.sdk.stats.LayoutFavoriteAttributeChangeEvent;
import com.google.wireless.android.sdk.stats.LayoutPaletteEvent;
import com.google.wireless.android.sdk.stats.SearchOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Class to manage anonymous stats logging for the layout editor. If global stats logging is disabled, no stats will be logged
 * (see {@link UsageTracker}).
 */
public class NlUsageTrackerImpl implements NlUsageTracker {
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

  /**
   * {@link CommonUsageTracker} that shares the same {@link Executor}, {@link DesignSurface} and event logger of the current tracker.
   * Logging of studio events are effectively made by the common tracker.
   */
  @NotNull private final CommonUsageTracker myCommonTracker;

  NlUsageTrackerImpl(@NotNull Executor executor,
                     @Nullable DesignSurface surface,
                     @NotNull Consumer<AndroidStudioEvent.Builder> eventLogger) {
    myCommonTracker = new CommonUsageTrackerImpl(executor, surface, eventLogger);
  }

  @Override
  public void logDropFromPalette(@NotNull String viewTagName,
                                 @NotNull String representation,
                                 @NotNull String selectedGroup,
                                 int filterMatches) {
    LayoutPaletteEvent.Builder builder = LayoutPaletteEvent.newBuilder()
      .setView(convertTagName(viewTagName))
      .setViewOption(convertViewOption(viewTagName, representation))
      .setSelectedGroup(convertGroupName(selectedGroup))
      .setSearchOption(convertFilterMatches(filterMatches));
    myCommonTracker.logStudioEvent(LayoutEditorEvent.LayoutEditorEventType.DROP_VIEW_FROM_PALETTE,
                                   (event) -> event.setPaletteEvent(builder));
  }

  @Override
  public void logPropertyChange(@NotNull NlPropertyItem property,
                                int filterMatches) {
    LayoutAttributeChangeEvent.Builder builder = LayoutAttributeChangeEvent.newBuilder()
      .setAttribute(convertAttribute(property))
      .setSearchOption(convertFilterMatches(filterMatches));
    for (NlComponent component : property.getComponents()) {
      builder.addView(convertTagName(component.getTagName()));
    }
    myCommonTracker.logStudioEvent(LayoutEditorEvent.LayoutEditorEventType.ATTRIBUTE_CHANGE,
                                   (event) -> event.setAttributeChangeEvent(builder));
  }

  @NotNull
  private static AndroidAttribute convertAttribute(@NotNull NlPropertyItem property) {
    AndroidFacet facet = property.getModel().getFacet();
    AndroidAttribute.AttributeNamespace namespace = UsageTrackerUtil.convertNamespace(property.getNamespace());

    return AndroidAttribute.newBuilder()
      .setAttributeName(UsageTrackerUtil.convertAttributeName(property.getName(), namespace, property.getLibraryName(), facet))
      .setAttributeNamespace(namespace)
      .build();
  }


  @Override
  public void logFavoritesChange(@NotNull String addedPropertyName,
                                 @NotNull String removedPropertyName,
                                 @NotNull List<String> currentFavorites,
                                 @NotNull AndroidFacet facet) {
    LayoutFavoriteAttributeChangeEvent.Builder builder = LayoutFavoriteAttributeChangeEvent.newBuilder();
    if (!addedPropertyName.isEmpty()) {
      builder.setAdded(UsageTrackerUtil.convertAttribute(addedPropertyName, facet));
    }
    if (!removedPropertyName.isEmpty()) {
      builder.setRemoved(UsageTrackerUtil.convertAttribute(removedPropertyName, facet));
    }
    for (String propertyName : currentFavorites) {
      builder.addActive(UsageTrackerUtil.convertAttribute(propertyName, facet));
    }
    myCommonTracker.logStudioEvent(LayoutEditorEvent.LayoutEditorEventType.FAVORITE_CHANGE,
                                   (event) -> event.setFavoriteChangeEvent(builder));
  }

  @NotNull
  static LayoutPaletteEvent.ViewGroup convertGroupName(@NotNull String groupName) {
    switch (groupName) {
      case "All":
        return LayoutPaletteEvent.ViewGroup.ALL_GROUPS;
      case "All Results":
        return LayoutPaletteEvent.ViewGroup.ALL_RESULTS;
      case "Common":
        return LayoutPaletteEvent.ViewGroup.COMMON;
      case "Buttons":
        return LayoutPaletteEvent.ViewGroup.BUTTONS;
      case "Widgets":
        return LayoutPaletteEvent.ViewGroup.WIDGETS;
      case "Text":
        return LayoutPaletteEvent.ViewGroup.TEXT;
      case "Layouts":
        return LayoutPaletteEvent.ViewGroup.LAYOUTS;
      case "Containers":
        return LayoutPaletteEvent.ViewGroup.CONTAINERS;
      case "Images":
        return LayoutPaletteEvent.ViewGroup.IMAGES;
      case "Date":
        return LayoutPaletteEvent.ViewGroup.DATES;
      case "Transitions":
        return LayoutPaletteEvent.ViewGroup.TRANSITIONS;
      case "Advanced":
        return LayoutPaletteEvent.ViewGroup.ADVANCED;
      case "Google":
        return LayoutPaletteEvent.ViewGroup.GOOGLE;
      case "Design":
        return LayoutPaletteEvent.ViewGroup.DESIGN;
      case "AppCompat":
        return LayoutPaletteEvent.ViewGroup.APP_COMPAT;
      case "Legacy":
        return LayoutPaletteEvent.ViewGroup.LEGACY;
      case "Helpers":
        return LayoutPaletteEvent.ViewGroup.HELPERS;
      default:
        return LayoutPaletteEvent.ViewGroup.CUSTOM;
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
  static SearchOption convertFilterMatches(int matches) {
    if (matches < 1) {
      return SearchOption.NONE;
    }
    if (matches > 1) {
      return SearchOption.MULTIPLE_MATCHES;
    }
    return SearchOption.SINGLE_MATCH;
  }

  @Nullable
  @VisibleForTesting
  static String getStyleValue(@NotNull String representation) {
    Matcher matcher = STYLE_PATTERN.matcher(representation);
    return matcher.find() ? matcher.group(1) : null;
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
}
