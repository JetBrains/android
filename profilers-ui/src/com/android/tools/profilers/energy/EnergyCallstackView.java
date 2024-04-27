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

import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.android.tools.adtui.ui.HideablePanel;
import com.android.tools.inspectors.common.api.stacktrace.StackTraceModel;
import com.android.tools.inspectors.common.ui.stacktrace.StackTraceGroup;
import com.android.tools.inspectors.common.ui.stacktrace.StackTraceView;
import com.android.tools.profiler.proto.Common;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class EnergyCallstackView extends JPanel {

  @NotNull private final EnergyProfilerStageView myStageView;

  public EnergyCallstackView(@NotNull EnergyProfilerStageView stageView) {
    super(new VerticalFlowLayout(0, JBUI.scale(5)));
    myStageView = stageView;
  }

  /**
   * Set the details view for all callstacks of a duration, if given {@code duration} is {@code null}, this clears the view.
   */
  public void setDuration(@Nullable EnergyDuration duration) {
    removeAll();
    if (duration == null) {
      return;
    }

    List<HideablePanel> callstackList = new ArrayList<>();
    StackTraceGroup stackTraceGroup = myStageView.getIdeComponents().createStackGroup();
    long startTimeNs = myStageView.getStage().getStudioProfilers().getSession().getStartTimestamp();
    for (Common.Event event : duration.getEventList()) {
      if (event.getEnergyEvent().getCallstack().isEmpty() ||
          EnergyDuration.getMetadataName(event.getEnergyEvent().getMetadataCase()).isEmpty()) {
        continue;
      }

      String callstackString = event.getEnergyEvent().getCallstack();
      StackTraceModel model = new StackTraceModel(myStageView.getStage().getStudioProfilers().getIdeServices().getCodeNavigator());
      StackTraceView stackTraceView = stackTraceGroup.createStackView(model);
      stackTraceView.getModel().setStackFrames(callstackString);
      JComponent traceComponent = stackTraceView.getComponent();
      // Sets a border on the ListView so the horizontal scroll bar doesn't hide the bottom of the content. Also the ListView cannot resize
      // properly when the scroll pane resize, wrap it in a JPanel. So move the list view out of the original scroll pane.
      if (traceComponent instanceof JScrollPane) {
        traceComponent = (JComponent)((JScrollPane)traceComponent).getViewport().getComponent(0);
        traceComponent.setBorder(new JBEmptyBorder(0, 0, 12, 0));
        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.add(traceComponent, BorderLayout.CENTER);
        wrapperPanel.setBackground(traceComponent.getBackground());
        traceComponent = AdtUiUtils.createNestedVScrollPane(wrapperPanel);
      }

      String time = TimeFormatter.getFullClockString(TimeUnit.NANOSECONDS.toMicros(event.getTimestamp() - startTimeNs));
      String description = time + "&nbsp;&nbsp;" + EnergyDuration.getMetadataName(event.getEnergyEvent().getMetadataCase());
      HideablePanel hideablePanel = new HideablePanel.Builder(description, traceComponent)
        .setContentBorder(new JBEmptyBorder(5, 0, 0, 0))
        .setPanelBorder(new JBEmptyBorder(0, 0, 0, 0))
        .setTitleRightPadding(0)
        .build();
      // Make the call stack hideable panel use the parent component's background.
      hideablePanel.setBackground(null);
      callstackList.add(hideablePanel);
    }
    if (callstackList.size() > 2) {
      callstackList.forEach(c -> c.setExpanded(false));
    }

    JLabel label = new JLabel("<html><b>Callstacks</b>: " + callstackList.size() + "</html>");
    label.setBorder(new JBEmptyBorder(0, 0, 4, 0));
    add(label);
    callstackList.forEach(c -> add(c));
  }
}
