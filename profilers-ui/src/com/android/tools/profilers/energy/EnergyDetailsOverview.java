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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.JBEmptyBorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class EnergyDetailsOverview extends JPanel {

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
    StyleSheet styleSheet = ((HTMLDocument) myTextPane.getDocument()).getStyleSheet();
    styleSheet.addRule("body { font-family: " + labelFont.getFamily() + "; font-size: 11pt; }");
    styleSheet.addRule("p { margin: 4 0 4 0; }");
    add(myTextPane);
  }

  /**
   * Set the details overview for a specific duration, if given {@code duration} is {@code null}, this make overview empty.
   */
  public void setDuration(@Nullable EventDuration duration) {
    myTextPane.setText("");
    if (duration == null) {
      return;
    }

    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append("<html>");
    for (EnergyProfiler.EnergyEvent event : duration.getEventList()) {
      appendMetadataTitle(stringBuilder, event);
      switch (event.getMetadataCase()) {
        case WAKE_LOCK_ACQUIRED:
          appendWakeLockAcquired(stringBuilder, event.getWakeLockAcquired());
          break;
        case ALARM_SET:
          appendAlarmSet(stringBuilder, event.getAlarmSet());
          break;
        case ALARM_CANCELLED:
          appendAlarmCancelled(stringBuilder, event.getAlarmCancelled());
          break;
        default:
          break;
      }
      stringBuilder.append("<br>");
    }
    stringBuilder.append("</html>");
    myTextPane.setText(stringBuilder.toString());
  }

  private long getSessionStartTimeNs() {
    return myStageView.getStage().getStudioProfilers().getSession().getStartTimestamp();
  }

  private void appendMetadataTitle(@NotNull StringBuilder stringBuilder, @NotNull EnergyProfiler.EnergyEvent event) {
    long timeMs = TimeUnit.NANOSECONDS.toMillis(event.getTimestamp() - getSessionStartTimeNs());
    stringBuilder.append("<p><b>").append(event.getMetadataCase().name()).append("</b>&nbsp;");
    stringBuilder.append(StringUtil.formatDuration(timeMs)).append("</p>");
  }

  private static void appendWakeLockAcquired(@NotNull StringBuilder stringBuilder,
                                             @NotNull EnergyProfiler.WakeLockAcquired wakeLockAcquired) {
    appendTitleAndValue(stringBuilder,"Name", wakeLockAcquired.getTag());
    appendTitleAndValue(stringBuilder,"Level", wakeLockAcquired.getLevel().name());
    if (!wakeLockAcquired.getFlagsList().isEmpty()) {
      String creationFlags = wakeLockAcquired.getFlagsList().stream()
        .map(EnergyProfiler.WakeLockAcquired.CreationFlag::name)
        .collect(Collectors.joining(", "));
      appendTitleAndValue(stringBuilder,"Flags", creationFlags);
    }
  }

  private static void appendAlarmSet(@NotNull StringBuilder stringBuilder, @NotNull EnergyProfiler.AlarmSet alarmSet) {
    appendTitleAndValue(stringBuilder,"Type", alarmSet.getType().name());
    appendTitleAndValue(stringBuilder,"TriggerTime", StringUtil.formatDuration(alarmSet.getTriggerMs()));
    appendTitleAndValue(stringBuilder,"IntervalTime", StringUtil.formatDuration(alarmSet.getIntervalMs()));
    appendTitleAndValue(stringBuilder,"WindowTime", StringUtil.formatDuration(alarmSet.getWindowMs()));
    switch(alarmSet.getSetActionCase()) {
      case OPERATION:
        appendAlarmOperation(stringBuilder, alarmSet.getOperation());
        break;
      case LISTENER:
        appendTitleAndValue(stringBuilder, "ListenerTag", alarmSet.getListener().getTag());
        break;
      default:
        break;
    }
  }

  private static void appendAlarmCancelled(@NotNull StringBuilder stringBuilder, @NotNull EnergyProfiler.AlarmCancelled alarmCancelled) {
    switch (alarmCancelled.getCancelActionCase()) {
      case OPERATION:
        appendAlarmOperation(stringBuilder, alarmCancelled.getOperation());
        break;
      case LISTENER:
        appendTitleAndValue(stringBuilder, "ListenerTag", alarmCancelled.getListener().getTag());
        break;
      default:
        break;
    }
  }

  private static void appendAlarmOperation(@NotNull StringBuilder stringBuilder, @NotNull EnergyProfiler.PendingIntent operation) {
    if (operation.getCreatorPackage().isEmpty()) {
      return;
    }
    String value = String.format("%s&nbsp;(UID:&nbsp;%d)", operation.getCreatorPackage(), operation.getCreatorUid());
    appendTitleAndValue(stringBuilder, "Creator", value);
  }

  private static void appendTitleAndValue(@NotNull StringBuilder stringBuilder, @NotNull String title, @NotNull String value) {
    stringBuilder.append("<p><b>").append(title).append("</b>:&nbsp<span>");
    stringBuilder.append(value).append("</span></p>");
  }
}
