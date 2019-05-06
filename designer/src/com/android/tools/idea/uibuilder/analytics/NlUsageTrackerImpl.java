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

import com.google.common.annotations.VisibleForTesting;
import com.android.sdklib.devices.State;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.common.analytics.UsageTrackerUtil;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.RenderErrorModelFactory;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel;
import com.android.tools.idea.uibuilder.property.NlPropertiesPanel.PropertiesViewMode;
import com.android.tools.idea.uibuilder.property2.NelePropertyItem;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.type.LayoutEditorFileType;
import com.google.common.collect.ImmutableMap;
import com.google.wireless.android.sdk.stats.AndroidAttribute;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.LayoutAttributeChangeEvent;
import com.google.wireless.android.sdk.stats.LayoutEditorEvent;
import com.google.wireless.android.sdk.stats.LayoutEditorRenderResult;
import com.google.wireless.android.sdk.stats.LayoutEditorState;
import com.google.wireless.android.sdk.stats.LayoutEditorState.Mode;
import com.google.wireless.android.sdk.stats.LayoutFavoriteAttributeChangeEvent;
import com.google.wireless.android.sdk.stats.LayoutPaletteEvent;
import com.google.wireless.android.sdk.stats.SearchOption;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.UIUtil;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
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

  // Sampling percentage for render events
  private static final int LOG_RENDER_PERCENT = 10;

  private static final Random sRandom = new Random();

  private final Executor myExecutor;
  private final WeakReference<DesignSurface> myDesignSurfaceRef;
  private final Consumer<AndroidStudioEvent.Builder> myEventLogger;

  @VisibleForTesting
  NlUsageTrackerImpl(@NotNull Executor executor,
                     @Nullable DesignSurface surface,
                     @NotNull Consumer<AndroidStudioEvent.Builder> eventLogger) {
    myExecutor = executor;
    myDesignSurfaceRef = new WeakReference<>(surface);
    myEventLogger = eventLogger;
  }

  /**
   * Generates a {@link LayoutEditorState} containing all the state of the layout editor from the given surface.
   */
  @NotNull
  static LayoutEditorState getState(@Nullable DesignSurface surface) {
    LayoutEditorState.Builder builder = LayoutEditorState.newBuilder();
    if (surface == null) {
      return builder.build();
    }

    if (surface.getLayoutType() instanceof LayoutEditorFileType) {
      builder.setType(((LayoutEditorFileType)surface.getLayoutType()).getLayoutEditorStateType());
    }
    // TODO(b/120469076): track VECTOR type as well

    double scale = surface.getScale();
    if (SystemInfo.isMac && UIUtil.isRetina()) {
      scale *= 2;
    }
    Configuration configuration = surface.getConfiguration();
    if (configuration != null) {
      State deviceState = configuration.getDeviceState();

      if (deviceState != null) {
        switch (deviceState.getOrientation()) {
          case PORTRAIT:
            builder.setConfigOrientation(LayoutEditorState.Orientation.PORTRAIT);
            break;
          case LANDSCAPE:
            builder.setConfigOrientation(LayoutEditorState.Orientation.LANDSCAPE);
            break;
          case SQUARE:
            // SQUARE is not supported
        }
      }

      if (configuration.getTarget() != null) {
        builder.setConfigApiLevel(configuration.getTarget().getVersion().getApiString());
      }
    }

    if (scale >= 0) {
      builder.setConfigZoomLevel((int)(scale * 100));
    }

    // TODO: better handling of layout vs. nav editor?
    if (surface instanceof NlDesignSurface) {
      builder.setMode(((NlDesignSurface)surface).isPreviewSurface() ? Mode.PREVIEW_MODE : Mode.DESIGN_MODE);

      switch (((NlDesignSurface)surface).getSceneMode()) {
        case SCREEN_ONLY:
          builder.setSurfaces(LayoutEditorState.Surfaces.SCREEN_SURFACE);
          break;
        case BLUEPRINT_ONLY:
          builder.setSurfaces(LayoutEditorState.Surfaces.BLUEPRINT_SURFACE);
          break;
        case BOTH:
          builder.setSurfaces(LayoutEditorState.Surfaces.BOTH);
          break;
      }
    }

    return builder.build();
  }

  /**
   * Returns whether an event should be logged given a percentage of times we want to log it.
   */
  @VisibleForTesting
  boolean shouldLog(int percent) {
    return sRandom.nextInt(100) >= 100 - percent - 1;
  }

  /**
   * Logs given layout editor event. This method will return immediately.
   *
   * @param eventType The event type to log
   * @param consumer  An optional {@link Consumer} used to add additional information to a {@link LayoutEditorEvent.Builder}
   *                  about the given event
   */
  private void logStudioEvent(@NotNull LayoutEditorEvent.LayoutEditorEventType eventType,
                              @Nullable Consumer<LayoutEditorEvent.Builder> consumer) {
    try {
      myExecutor.execute(() -> {
        LayoutEditorEvent.Builder builder = LayoutEditorEvent.newBuilder()
          .setType(eventType)
          .setState(getState(myDesignSurfaceRef.get()));
        if (consumer != null) {
          consumer.accept(builder);
        }

        AndroidStudioEvent.Builder studioEvent = AndroidStudioEvent.newBuilder()
          .setCategory(AndroidStudioEvent.EventCategory.LAYOUT_EDITOR)
          .setKind(AndroidStudioEvent.EventKind.LAYOUT_EDITOR_EVENT)
          .setLayoutEditorEvent(builder.build());

        myEventLogger.accept(studioEvent);
      });
    }
    catch (RejectedExecutionException e) {
      // We are hitting the throttling limit
    }
  }

  @Override
  public void logAction(@NotNull LayoutEditorEvent.LayoutEditorEventType eventType) {
    assert !LayoutEditorEvent.LayoutEditorEventType.RENDER.equals(eventType) : "RENDER actions should be logged through logRenderResult";
    assert !LayoutEditorEvent.LayoutEditorEventType.DROP_VIEW_FROM_PALETTE.equals(eventType)
      : "DROP_VIEW_FROM_PALETTE actions should be logged through logDropFromPalette";
    assert !LayoutEditorEvent.LayoutEditorEventType.ATTRIBUTE_CHANGE.equals(eventType)
      : "DROP_VIEW_FROM_PALETTE actions should be logged through logPropertyChange";
    assert !LayoutEditorEvent.LayoutEditorEventType.FAVORITE_CHANGE.equals(eventType)
      : "FAVORITE_CHANGE actions should be logged through logFavoritesChange";

    logStudioEvent(eventType, null);
  }

  @Override
  public void logRenderResult(@Nullable LayoutEditorRenderResult.Trigger trigger, @NotNull RenderResult result, long totalRenderTimeMs) {
    // Renders are a quite common event so we sample them
    if (!shouldLog(LOG_RENDER_PERCENT)) {
      return;
    }

    logStudioEvent(LayoutEditorEvent.LayoutEditorEventType.RENDER, (event) -> {
      LayoutEditorRenderResult.Builder builder = LayoutEditorRenderResult.newBuilder()
        .setResultCode(result.getRenderResult().getStatus().ordinal())
        .setTotalRenderTimeMs(totalRenderTimeMs);

      if (trigger != null) {
          builder.setTrigger(trigger);
      }

      builder.setComponentCount((int)result.getRootViews().stream()
        .flatMap(s -> Stream.concat(s.getChildren().stream(), Stream.of(s)))
        .count());

      RenderErrorModel errorModel = RenderErrorModelFactory.createErrorModel(myDesignSurfaceRef.get(), result, null);
      builder.setTotalIssueCount(errorModel.getIssues().size());
      if (!errorModel.getIssues().isEmpty()) {
        int errorCount = 0;
        int fidelityWarningCount = 0;
        for (RenderErrorModel.Issue issue : errorModel.getIssues()) {
          if (HighlightSeverity.ERROR.getName().equals(issue.getSeverity().getName())) {
            errorCount++;
          }
          else if (issue.getSummary().startsWith("Layout fid")) {
            fidelityWarningCount++;
          }
        }

        builder
          .setErrorCount(errorCount)
          .setFidelityWarningCount(fidelityWarningCount);
      }

      event.setRenderResult(builder.build());
    });
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
    logStudioEvent(LayoutEditorEvent.LayoutEditorEventType.DROP_VIEW_FROM_PALETTE, (event) -> event.setPaletteEvent(builder));
  }

  @Override
  public void logPropertyChange(@NotNull NlProperty property,
                                @NotNull PropertiesViewMode propertiesMode,
                                int filterMatches) {
    LayoutAttributeChangeEvent.Builder builder = LayoutAttributeChangeEvent.newBuilder()
      .setAttribute(UsageTrackerUtil.convertAttribute(property))
      .setSearchOption(convertFilterMatches(filterMatches))
      .setViewType(convertPropertiesMode(propertiesMode));
    for (NlComponent component : property.getComponents()) {
      builder.addView(convertTagName(component.getTagName()));
    }
    logStudioEvent(LayoutEditorEvent.LayoutEditorEventType.ATTRIBUTE_CHANGE, (event) -> event.setAttributeChangeEvent(builder));
  }

  @Override
  public void logPropertyChange(@NotNull NelePropertyItem property,
                                int filterMatches) {
    LayoutAttributeChangeEvent.Builder builder = LayoutAttributeChangeEvent.newBuilder()
      .setAttribute(convertAttribute(property))
      .setSearchOption(convertFilterMatches(filterMatches));
    for (NlComponent component : property.getComponents()) {
      builder.addView(convertTagName(component.getTagName()));
    }
    logStudioEvent(LayoutEditorEvent.LayoutEditorEventType.ATTRIBUTE_CHANGE, (event) -> event.setAttributeChangeEvent(builder));
  }

  @NotNull
  private static AndroidAttribute convertAttribute(@NotNull NelePropertyItem property) {
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
    logStudioEvent(LayoutEditorEvent.LayoutEditorEventType.FAVORITE_CHANGE, (event) -> event.setFavoriteChangeEvent(builder));
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

  @Nullable
  @com.google.common.annotations.VisibleForTesting
  static String getStyleValue(@NotNull String representation) {
    Matcher matcher = STYLE_PATTERN.matcher(representation);
    return matcher.find() ? matcher.group(1) : null;
  }

  @NotNull
  @com.google.common.annotations.VisibleForTesting
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
  @com.google.common.annotations.VisibleForTesting
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
  @com.google.common.annotations.VisibleForTesting
  static LayoutPaletteEvent.ViewOption convertEditTextViewOption(@NotNull String representation) {
    Matcher matcher = INPUT_STYLE_PATTERN.matcher(representation);
    if (!matcher.find()) {
      return LayoutPaletteEvent.ViewOption.NORMAL;
    }
    LayoutPaletteEvent.ViewOption viewOption = PALETTE_VIEW_OPTION_MAP.get(matcher.group(1));
    return viewOption != null ? viewOption : LayoutPaletteEvent.ViewOption.CUSTOM_OPTION;
  }

  @NotNull
  @com.google.common.annotations.VisibleForTesting
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
