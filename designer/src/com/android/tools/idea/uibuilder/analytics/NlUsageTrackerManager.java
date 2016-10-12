/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.annotations.VisibleForTesting;
import com.android.sdklib.devices.State;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.RenderErrorModelFactory;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.wireless.android.sdk.stats.*;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Random;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Class to manage anonymous stats logging for the layout editor. If global stats logging is disabled, no stats will be logged
 * (see {@link UsageTracker}).
 */
public class NlUsageTrackerManager implements NlUsageTracker {
  @VisibleForTesting
  static final NlUsageTracker NOP_TRACKER = new NopTracker();

  // Sampling percentage for render events
  private static final int LOG_RENDER_PERCENT = 10;

  private static final Cache<DesignSurface, NlUsageTracker> sTrackersCache = CacheBuilder.newBuilder()
    .weakKeys()
    .expireAfterAccess(5, TimeUnit.MINUTES)
    .build();
  private static ExecutorService ourExecutorService = new ThreadPoolExecutor(0, 1,
                                                                             1L, TimeUnit.MINUTES,
                                                                             new LinkedBlockingQueue<>(10));

  private static final Random sRandom = new Random();

  private final Executor myExecutor;
  private final WeakReference<DesignSurface> myDesignSurfaceRef;
  private final UsageTracker myUsageTracker;


  @VisibleForTesting
  NlUsageTrackerManager(@NotNull Executor executor, @Nullable DesignSurface surface, @NotNull UsageTracker usageTracker) {
    myExecutor = executor;
    myDesignSurfaceRef = new WeakReference<>(surface);
    myUsageTracker = usageTracker;
  }

  /**
   * Returns an NlUsageTracker for the given surface or a no-op tracker if the surface is null
   */
  @VisibleForTesting
  @NotNull
  static NlUsageTracker getInstanceInner(@Nullable DesignSurface surface) {
    if (surface == null) {
      return NOP_TRACKER;
    }

    try {
      return sTrackersCache.get(surface, () -> new NlUsageTrackerManager(ourExecutorService, surface, UsageTracker.getInstance()));
    }
    catch (ExecutionException e) {
      assert false;
    }
    return NOP_TRACKER;
  }

  /**
   * Returns an NlUsageTracker for the given surface or a no-op tracker if the surface is null or stats tracking is disabled
   */
  @NotNull
  public static NlUsageTracker getInstance(@Nullable DesignSurface surface) {
    return UsageTracker.getInstance().getAnalyticsSettings().hasOptedIn() ? getInstanceInner(surface) : NOP_TRACKER;
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

    builder.setMode(surface.isPreviewSurface() ? LayoutEditorState.Mode.PREVIEW_MODE : LayoutEditorState.Mode.DESIGN_MODE);

    switch (surface.getLayoutType()) {
      case DRAWABLE:
        builder.setType(LayoutEditorState.Type.DRAWABLE);
        break;
      case LAYOUT:
        builder.setType(LayoutEditorState.Type.LAYOUT);
        break;
      case MENU:
        builder.setType(LayoutEditorState.Type.MENU);
        break;
      case PREFERENCE_SCREEN:
        builder.setType(LayoutEditorState.Type.PREFERENCE_SCREEN);
        break;
      case UNKNOWN:
    }

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

    switch (surface.getScreenMode()) {
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

        myUsageTracker.log(studioEvent);
      });
    }
    catch (RejectedExecutionException e) {
      // We are hitting the throttling limit
    }
  }

  @Override
  public void logAction(@NotNull LayoutEditorEvent.LayoutEditorEventType eventType) {
    assert !LayoutEditorEvent.LayoutEditorEventType.RENDER.equals(eventType) : "RENDER actions should be logged through logRenderResult";

    logStudioEvent(eventType, null);
  }


  @Override
  public void logRenderResult(@Nullable NlModel.ChangeType trigger, @NotNull RenderResult result, long totalRenderTimeMs) {
    // Renders are a quite common event so we sample them
    if (!shouldLog(LOG_RENDER_PERCENT)) {
      return;
    }

    logStudioEvent(LayoutEditorEvent.LayoutEditorEventType.RENDER, (event) -> {
      LayoutEditorRenderResult.Builder builder = LayoutEditorRenderResult.newBuilder()
        .setResultCode(result.getRenderResult().getStatus().ordinal())
        .setTotalRenderTimeMs(totalRenderTimeMs);

      if (trigger != null) {
        switch (trigger) {
          case RESOURCE_EDIT:
          case RESOURCE_CHANGED:
            builder.setTrigger(LayoutEditorRenderResult.Trigger.RESOURCE_CHANGE);
            break;
          case EDIT:
          case ADD_COMPONENTS:
          case DELETE:
          case DND_COMMIT:
          case DND_END:
          case DROP:
          case RESIZE_END:
          case RESIZE_COMMIT:
            builder.setTrigger(LayoutEditorRenderResult.Trigger.EDIT);
            break;
          case REQUEST_RENDER:
            builder.setTrigger(LayoutEditorRenderResult.Trigger.USER);
            break;
          case BUILD:
            builder.setTrigger(LayoutEditorRenderResult.Trigger.BUILD);
            break;
          case CONFIGURATION_CHANGE:
          case UPDATE_HIERARCHY:
            break;
        }
      }

      builder.setComponentCount((int)result.getRootViews().stream()
        .flatMap(s -> Stream.concat(s.getChildren().stream(), Stream.of(s)))
        .count());

      RenderErrorModel errorModel = RenderErrorModelFactory.createErrorModel(result, null);
      builder.setTotalIssueCount(errorModel.getSize());
      if (errorModel.getSize() > 0) {
        int errorCount = 0;
        int fidelityWarningCount = 0;
        for (RenderErrorModel.Issue issue : errorModel.getIssues()) {
          if (HighlightSeverity.ERROR.equals(issue.getSeverity())) {
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
}
