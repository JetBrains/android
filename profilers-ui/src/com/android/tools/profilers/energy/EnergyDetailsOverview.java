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

import com.android.tools.profiler.proto.EnergyProfiler;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.JBEmptyBorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.util.concurrent.TimeUnit;

final class EnergyDetailsOverview extends JPanel {

  @NotNull private final JTextPane myTextPane = new JTextPane();

  public EnergyDetailsOverview() {
    super(new BorderLayout());
    setBorder(new JBEmptyBorder(5, 5, 5, 0));

    myTextPane.setContentType("text/html");
    myTextPane.setBackground(null);
    myTextPane.setBorder(null);
    myTextPane.setEditable(false);
    Font labelFont = UIManager.getFont("Label.font");
    StyleSheet styleSheet = ((HTMLDocument)myTextPane.getDocument()).getStyleSheet();
    styleSheet.addRule("body { font-family: " + labelFont.getFamily() + "; font-size: 13pt; }");
    styleSheet.addRule("p { margin: 2 0 2 0; }");
    add(myTextPane);
  }

  /**
   * Set the details overview for a specific duration, if given {@code duration} is {@code null}, the overview is empty.
   * <li>
   *   <ul>Wake lock acquire data is shown, but release data is redundant and is not shown.</ul>
   *   <ul>Alarm set data is shown, alarm cancel data is redundant and is not shown.</ul>
   *   <ul>Job scheduled data and finished data is shown, the start/stop data is not user code and is not shown.</ul>
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
        EnergyEvent firstAcquire = duration.getEventList().stream().filter(e -> e.hasWakeLockAcquired()).findFirst().orElse(null);
        if (firstAcquire != null) {
          html.renderWakeLockAcquired(firstAcquire.getWakeLockAcquired());
        }
        break;
      case ALARM:
        EnergyEvent firstAlarmSet = duration.getEventList().stream().filter(e -> e.hasAlarmSet()).findFirst().orElse(null);
        if (firstAlarmSet != null) {
          html.renderAlarmSet(firstAlarmSet.getAlarmSet());
        }
        break;
      case JOB:
        EnergyEvent firstScheduled = duration.getEventList().stream().filter(e -> e.hasJobScheduled()).findFirst().orElse(null);
        if (firstScheduled != null) {
          html.renderJobScheduled(firstScheduled.getJobScheduled());
        }
        EnergyEvent firstFinished = duration.getEventList().stream().filter(e -> e.hasJobFinished()).findFirst().orElse(null);
        if (firstFinished != null) {
          html.renderJobFinished(firstFinished.getJobFinished());
        }
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
    EnergyProfiler.EnergyEvent lastEvent = duration.getEventList().get(duration.getEventList().size() - 1);
    if (lastEvent.getIsTerminal()) {
      long durationNs = lastEvent.getTimestamp() - duration.getEventList().get(0).getTimestamp();
      html.appendTitleAndValue("Duration", StringUtil.formatDuration(TimeUnit.NANOSECONDS.toMillis(durationNs)));
    }
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(EnergyDetailsOverview.class);
  }
}
