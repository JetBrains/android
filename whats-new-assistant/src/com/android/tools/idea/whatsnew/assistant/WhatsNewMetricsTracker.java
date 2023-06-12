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
package com.android.tools.idea.whatsnew.assistant;

import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.assistant.PanelFactory;
import com.google.common.base.Stopwatch;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.WhatsNewAssistantUpdateEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WhatsNewMetricsTracker {
  private static final Key<MetricsEventBuilder> METRICS_BUILDER_KEY = Key.create("WhatsNewMetricsTracker");

  @NotNull
  public static WhatsNewMetricsTracker getInstance() {
    return Objects.requireNonNull(PanelFactory.EP_NAME.findExtension(WhatsNewUpdateStatusPanelFactory.class)).getMetricsTracker();
  }

  void open(@NotNull Project project, boolean isAutoOpened) {
    // An extra "open" can fire when the window is already open and the user manually uses the WhatsNewSidePanelAction
    // again, so in this case just ignore the call and treat the original open as the actual beginning.
    MetricsEventBuilder metrics = project.getUserData(METRICS_BUILDER_KEY);
    if (metrics == null) {
      metrics = new MetricsEventBuilder();
      project.putUserData(METRICS_BUILDER_KEY, metrics);
      metrics.myBuilder.setAutoOpened(isAutoOpened);
      metrics.myActionButtonMetricsBuilder.generateEventsForAllCreatedBeforeActions(project).forEach(metrics::addActionButtonEvent);
    }
  }

  private @Nullable MetricsEventBuilder getMetricsBuilder(@NotNull Project project) {
    return project.getUserData(METRICS_BUILDER_KEY);
  }

  void clearDataFor(@NotNull Project project) {
    project.putUserData(METRICS_BUILDER_KEY, null);
  }

  void updateFlow(@NotNull Project project) {
    MetricsEventBuilder metrics = getMetricsBuilder(project);
    if (metrics != null) {
      metrics.myBuilder.setUpdateFlow(true);
    }
  }

  void scrolledToBottom(@NotNull Project project) {
    MetricsEventBuilder metrics = getMetricsBuilder(project);
    if (metrics != null) {
      metrics.scrolledToBottom();
    }
  }

  public void actionButtonCreated(@NotNull Project project, @NotNull String actionKey) {
    MetricsEventBuilder metrics = getMetricsBuilder(project);
    if (metrics != null) {
      metrics.addActionButtonEvent(metrics.myActionButtonMetricsBuilder.actionCreated(project, actionKey));
    }
  }

  public void clickActionButton(@NotNull Project project, @NotNull String actionKey) {
    MetricsEventBuilder metrics = getMetricsBuilder(project);
    if (metrics != null) {
      metrics.addActionButtonEvent(metrics.myActionButtonMetricsBuilder.clickAction(project, actionKey));
    }
  }

  public void stateUpdateActionButton(@NotNull Project project, @NotNull String actionKey) {
    MetricsEventBuilder metrics = getMetricsBuilder(project);
    if (metrics != null) {
      metrics.addActionButtonEvent(metrics.myActionButtonMetricsBuilder.stateUpdateAction(project, actionKey));
    }
  }

  public void dismissed(@NotNull Project project) {
    MetricsEventBuilder metrics = getMetricsBuilder(project);
    if (metrics != null) {
      metrics.myBuilder.setDismissed(true);
    }
  }

  public void setUpdateTime(@NotNull Project project) {
    MetricsEventBuilder metrics = getMetricsBuilder(project);
    if (metrics != null) {
      metrics.setUpdateTime();
    }
  }

  void close(@NotNull Project project) {
    MetricsEventBuilder metrics = getMetricsBuilder(project);
    if (metrics != null) {
      metrics.buildAndLog();
      project.putUserData(METRICS_BUILDER_KEY, null);
    }
  }

  /**
   * Wrapper for WhatsNewAssistantUpdateEvent because we need to keep track of the time difference.
   */
  private static class MetricsEventBuilder {
    final @NotNull WhatsNewAssistantUpdateEvent.Builder myBuilder = WhatsNewAssistantUpdateEvent.newBuilder();
    final @NotNull Stopwatch myStopwatch = Stopwatch.createStarted();
    final @NotNull ActionButtonMetricsEventBuilder myActionButtonMetricsBuilder = new ActionButtonMetricsEventBuilder();

    private void setUpdateTime() {
      myBuilder.setTimeToUpdateMs(myStopwatch.elapsed().toMillis());
    }

    private void scrolledToBottom() {
      myBuilder.setScrolledToBottom(true);
      myBuilder.setTimeToScrolledToBottom(myStopwatch.elapsed().toMillis());
    }

    private void buildAndLog() {
      myBuilder.setTimeToCloseMs(myStopwatch.elapsed().toMillis());

      UsageTracker.log(
        AndroidStudioEvent.newBuilder()
          .setKind(AndroidStudioEvent.EventKind.WHATS_NEW_ASSISTANT_UPDATE_EVENT)
          .setWhatsNewAssistantUpdateEvent(myBuilder)
      );
    }

    public void addActionButtonEvent(WhatsNewAssistantUpdateEvent.ActionButtonEvent.Builder actionButtonEvent) {
      myBuilder.addActionButtonEvents(actionButtonEvent.setTimeFromWnaOpen(myStopwatch.elapsed().toMillis()));
    }
  }
}
