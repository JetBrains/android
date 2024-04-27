/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.configurations.Configuration;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.rendering.RenderErrorModelFactory;
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel;
import com.android.tools.rendering.RenderContext;
import com.android.tools.rendering.RenderResult;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.LayoutEditorEvent;
import com.google.wireless.android.sdk.stats.LayoutEditorRenderResult;
import com.google.wireless.android.sdk.stats.LayoutEditorState;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.UIUtil;
import java.lang.ref.WeakReference;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages anonymous stats logging for design tools. No stats will be logged if global stats logging is disabled (see {@link UsageTracker}).
 */
public class CommonUsageTrackerImpl implements CommonUsageTracker {
  private static final Random sRandom = new Random();

  /**
   * We don't use the auto-generated id to log user's custom avd. All custom AVDs are tracked as a device which has "_custom_avd" id.
   */
  private static final String CUSTOM_AVD_ID = "_custom_avd";

  private final Executor myExecutor;
  private final WeakReference<DesignSurface<?>> myDesignSurfaceRef;
  private final Consumer<AndroidStudioEvent.Builder> myEventLogger;

  public CommonUsageTrackerImpl(@NotNull Executor executor,
                                @Nullable DesignSurface<?> surface,
                                @NotNull Consumer<AndroidStudioEvent.Builder> eventLogger) {
    myExecutor = executor;
    myDesignSurfaceRef = new WeakReference<>(surface);
    myEventLogger = eventLogger;
  }

  /**
   * Generates a {@link LayoutEditorState} containing all the state of the design editor from the given {@link DesignSurface}.
   */
  @NotNull
  static LayoutEditorState getState(@Nullable DesignSurface<?> surface) {
    LayoutEditorState.Builder builder = LayoutEditorState.newBuilder();
    if (surface == null) {
      return builder.build();
    }

    double scale = surface.getScale();
    if (SystemInfo.isMac && UIUtil.isRetina()) {
      scale *= 2;
    }
    // TODO(b/136258816): Update metrics to log multiple configurations
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

    return builder.setType(surface.getAnalyticsManager().getLayoutType())
      .setMode(surface.getAnalyticsManager().getEditorMode())
      .setSurfaces(surface.getAnalyticsManager().getSurfaceType())
      .build();
  }

  /**
   * Returns whether an event should be logged given a percentage of times we want to log it.
   */
  @VisibleForTesting
  boolean shouldLog(int percentage) {
    return sRandom.nextInt(100) >= 100 - percentage - 1;
  }

  @Override
  public void logStudioEvent(@NotNull LayoutEditorEvent.LayoutEditorEventType eventType,
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

        // Add application id if available
        CommonUsageTrackerKt.setApplicationId(studioEvent, myDesignSurfaceRef.get());

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
    // TODO: move the assertions below to somewhere in uibuilder.analytics module.
    assert !LayoutEditorEvent.LayoutEditorEventType.DROP_VIEW_FROM_PALETTE.equals(eventType)
      : "DROP_VIEW_FROM_PALETTE actions should be logged through logDropFromPalette";
    assert !LayoutEditorEvent.LayoutEditorEventType.ATTRIBUTE_CHANGE.equals(eventType)
      : "DROP_VIEW_FROM_PALETTE actions should be logged through logPropertyChange";
    assert !LayoutEditorEvent.LayoutEditorEventType.FAVORITE_CHANGE.equals(eventType)
      : "FAVORITE_CHANGE actions should be logged through logFavoritesChange";

    logStudioEvent(eventType, null);
  }

  @Override
  public void logRenderResult(@Nullable LayoutEditorRenderResult.Trigger trigger,
                              @NotNull RenderResult result,
                              @NotNull RenderResultType type) {
    // Renders are a quite common event so we sample them
    if (!shouldLog(type.getLogPercent())) {
      return;
    }

    logStudioEvent(type.getLoggingType(), (event) -> {
      LayoutEditorRenderResult.Builder builder = LayoutEditorRenderResult.newBuilder()
        .setResultCode(result.getRenderResult().getStatus().ordinal())
        .setTotalRenderTimeMs(type.getDurationProvider().apply(result));

      if (trigger != null) {
        builder.setTrigger(trigger);
      }

      builder.setComponentCount((int)result.getRootViews().stream()
        .flatMap(s -> Stream.concat(s.getChildren().stream(), Stream.of(s)))
        .count());

      RenderErrorModel errorModel = RenderErrorModelFactory.createErrorModel(myDesignSurfaceRef.get(), result);
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
      RenderContext context = result.getRenderContext();
      if (context != null) {
        Device device = context.getConfiguration().getDevice();
        if (device != null) {
          if (ConfigurationManager.isAvdDevice(device)) {
            builder.setDeviceId(CUSTOM_AVD_ID);
          }
          else {
            builder.setDeviceId(device.getId());
          }
        }
      }
      event.setRenderResult(builder.build());
    });
  }
}
