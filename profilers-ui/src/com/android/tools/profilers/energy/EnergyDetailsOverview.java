/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.energy;

import com.android.tools.adtui.ui.BreakWordWrapHtmlTextPane;
import com.android.tools.profiler.proto.Common;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.JBEmptyBorder;
import java.awt.BorderLayout;
import java.util.concurrent.TimeUnit;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class EnergyDetailsOverview extends JPanel {

  @NotNull private final JTextPane myTextPane;

  public EnergyDetailsOverview() {
    super(new BorderLayout());
    setBorder(new JBEmptyBorder(0, 0, 5, 0));
    myTextPane = new BreakWordWrapHtmlTextPane();
    add(myTextPane);
  }

  /**
   * Set the details overview for a specific duration, if given {@code duration} is {@code null}, the overview is empty.
   * <li>
   *   <ul>Wake lock acquire data is shown, but release data is redundant and is not shown.</ul>
   *   <ul>Alarm set data is shown, alarm cancel data is redundant and is not shown.</ul>
   *   <ul>Job scheduled data and finished data is shown, the start/stop data is not user code and is not shown.</ul>
   *   <ul>Location update requested data is shown, other data is redundant and is not shown.</ul>
   * </li>
   */
  public void setDuration(@Nullable EnergyDuration duration) {
    myTextPane.setText("");
    if (duration == null || duration.getKind() == EnergyDuration.Kind.UNKNOWN) {
      return;
    }
    UiHtmlText html = new UiHtmlText();

    // TODO(b/74204071): Clean up these streams once we know the first item in the duration will always be the initiating event
    switch (duration.getKind()) {
      case WAKE_LOCK:
        duration.getEventList().stream().filter(e -> e.getEnergyEvent().hasWakeLockAcquired()).findFirst()
          .ifPresent(event -> html.renderWakeLockAcquired(event.getEnergyEvent().getWakeLockAcquired()));
        break;
      case ALARM:
        duration.getEventList().stream().filter(e -> e.getEnergyEvent().hasAlarmSet()).findFirst()
          .ifPresent(event -> html.renderAlarmSet(event.getEnergyEvent().getAlarmSet()));
        break;
      case JOB:
        duration.getEventList().stream().filter(e -> e.getEnergyEvent().hasJobScheduled()).findFirst()
          .ifPresent(event -> html.renderJobScheduled(event.getEnergyEvent().getJobScheduled()));
        duration.getEventList().stream().filter(e -> e.getEnergyEvent().hasJobFinished()).findFirst()
          .ifPresent(event -> html.renderJobFinished(event.getEnergyEvent().getJobFinished()));
        break;
      case LOCATION:
        duration.getEventList().stream().filter(e -> e.getEnergyEvent().hasLocationUpdateRequested()).findFirst()
          .ifPresent(event -> html.renderLocationUpdateRequested(event.getEnergyEvent().getLocationUpdateRequested()));
        break;
      default:
        getLogger().warn("Unsupported overview " + duration.getKind().name());
        break;
    }

    renderDuration(html, duration);
    myTextPane.setText(html.toString());
  }

  /**
   * Renders the duration amount if the last event in duration is terminal. For example, the duration is 10s from the wake lock acquire
   * to the last wake lock release. If the last event is not terminal, does not show the duration amount.
   */
  private static void renderDuration(@NotNull UiHtmlText html, @NotNull EnergyDuration duration) {
    Common.Event lastEvent = duration.getEventList().get(duration.getEventList().size() - 1);
    if (lastEvent.getIsEnded()) {
      long durationNs = lastEvent.getTimestamp() - duration.getEventList().get(0).getTimestamp();
      html.appendTitleAndValue("Duration", StringUtil.formatDuration(TimeUnit.NANOSECONDS.toMillis(durationNs)));
    }
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(EnergyDetailsOverview.class);
  }
}
