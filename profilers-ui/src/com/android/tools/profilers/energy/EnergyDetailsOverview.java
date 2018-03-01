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

  @NotNull private final EnergyProfilerStageView myStageView;
  @NotNull private final JTextPane myTextPane;

  public EnergyDetailsOverview(@NotNull EnergyProfilerStageView stageView) {
    super(new BorderLayout());
    setBorder(new JBEmptyBorder(10, 10, 5, 5));
    myStageView = stageView;

    myTextPane = new JTextPane();
    myTextPane.setContentType("text/html");
    myTextPane.setBackground(null);
    myTextPane.setBorder(null);
    myTextPane.setEditable(false);
    Font labelFont = UIManager.getFont("Label.font");
    StyleSheet styleSheet = ((HTMLDocument)myTextPane.getDocument()).getStyleSheet();
    styleSheet.addRule("body { font-family: " + labelFont.getFamily() + "; font-size: 11pt; }");
    styleSheet.addRule("p { margin: 4 0 4 0; }");
    add(myTextPane);
  }

  /**
   * Set the details overview for a specific duration, if given {@code duration} is {@code null}, this make overview empty.
   */
  public void setDuration(@Nullable EnergyDuration duration) {
    myTextPane.setText("");
    if (duration == null) {
      return;
    }

    UiHtmlText html = new UiHtmlText();
    for (EnergyProfiler.EnergyEvent event : duration.getEventList()) {
      renderMetadataTitle(html, event);

      switch (event.getMetadataCase()) {
        case WAKE_LOCK_ACQUIRED:
          html.renderWakeLockAcquired(event.getWakeLockAcquired());
          break;
        case ALARM_SET:
          html.renderAlarmSet(event.getAlarmSet());
          break;
        case ALARM_CANCELLED:
          html.renderAlarmCancelled(event.getAlarmCancelled());
          break;
        case JOB_SCHEDULED:
          html.renderJobScheduled(event.getJobScheduled());
          break;
        case JOB_STARTED:
          html.renderJobStarted(event.getJobStarted());
          break;
        case JOB_STOPPED:
          html.renderJobStopped(event.getJobStopped());
          break;
        case JOB_FINISHED:
          html.renderJobFinished(event.getJobFinished());
          break;
        default:
          getLogger().warn("Unsupported overview " + event.getMetadataCase());
          break;
      }
      html.appendNewLine();
    }
    myTextPane.setText(html.toString());
  }

  private void renderMetadataTitle(@NotNull UiHtmlText html, @NotNull EnergyProfiler.EnergyEvent event) {
    long timeNs = event.getTimestamp() - myStageView.getStage().getStudioProfilers().getSession().getStartTimestamp();
    String time = StringUtil.formatDuration(TimeUnit.NANOSECONDS.toMillis(timeNs));
    String title = String.format("<b>%s</b>&nbsp;%s", event.getMetadataCase().name(), time);
    html.appendTitle(title);
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(EnergyDetailsOverview.class);
  }
}
