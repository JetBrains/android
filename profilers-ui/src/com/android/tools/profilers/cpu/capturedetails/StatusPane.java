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
package com.android.tools.profilers.cpu.capturedetails;

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerFonts;
import com.android.tools.profilers.cpu.CpuProfilerAspect;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.cpu.CpuProfilerStageView;
import com.android.tools.profilers.cpu.ProfilingTechnology;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;

/**
 * A {@link CapturePane} used to display the current state of the profiler (e.g. parsing, recording), its elapsed time, and a button to
 * interrupt it.
 */
abstract class StatusPane extends CapturePane {

  /**
   * Status itself (e.g. recording, parsing).
   */
  @NotNull
  private String myStatus;

  /**
   * How much time has elapsed since the profiler is in this state.
   */
  @NotNull
  private JLabel myDurationLabel;

  @NotNull
  private final String myTechnology;

  @NotNull
  protected final CpuProfilerStage myStage;

  @NotNull
  protected final AspectObserver myObserver;

  public StatusPane(@NotNull CpuProfilerStageView stageView, @NotNull String statusLabel) {
    super(stageView);
    myStage = stageView.getStage();
    myStatus = statusLabel;
    myDurationLabel = createLabel("", false);
    myTechnology = ProfilingTechnology.fromConfig(myStage.getProfilerConfigModel().getProfilingConfiguration()).getName();
    myObserver = new AspectObserver();

    myStage.getAspect().addDependency(myObserver)
           .onChange(CpuProfilerAspect.CAPTURE_ELAPSED_TIME, this::updateDuration);
    disableInteraction();
    updateView();
  }

  @Override
  void populateContent(@NotNull JPanel panel) {
    // We need an outer panel, as the given panel uses BorderLayout and trying to add a fixed size box to it would make it to occupy the
    // entire panel area.
    JPanel mainPanel = new JPanel(new TabularLayout("*,300px,*", "*,150px,*"));
    // TODO(b/109661512): Move vgap scale into TabularLayout
    JPanel statusPanel = new JPanel(new TabularLayout("*,Fit,20px,Fit,*","28px,Fit,Fit,Fit,Fit,*").setVGap(JBUIScale.scale(5)));
    statusPanel.setBorder(new LineBorder(ProfilerColors.CPU_CAPTURE_STATUS, 1));

    JLabel status = createLabel("Status", true);
    JLabel actualStatus = createLabel(myStatus, false);
    JLabel duration = createLabel("Duration", true);
    JLabel technology = createLabel("Type", true);
    JLabel actualTechnology = createLabel(myTechnology, false);

    statusPanel.add(status, new TabularLayout.Constraint(1, 1));
    statusPanel.add(actualStatus, new TabularLayout.Constraint(1, 3));
    statusPanel.add(duration, new TabularLayout.Constraint(2, 1));
    statusPanel.add(myDurationLabel, new TabularLayout.Constraint(2, 3));
    statusPanel.add(technology, new TabularLayout.Constraint(3, 1));
    statusPanel.add(actualTechnology, new TabularLayout.Constraint(3, 3));

    // Adds the button centralized in the 3 middle columns (2nd to 4th).
    statusPanel.add(createButtonPanel(), new TabularLayout.Constraint(4, 3, 1));

    mainPanel.add(statusPanel, new TabularLayout.Constraint(1, 1));
    panel.add(mainPanel, BorderLayout.CENTER);
  }

  private void updateDuration() {
    myDurationLabel.setText(getDurationText());
  }

  private static JLabel createLabel(String text, boolean isRightAligned) {
    JLabel label = new JLabel(text, isRightAligned ? SwingConstants.RIGHT : SwingConstants.LEFT);
    label.setFont(ProfilerFonts.STANDARD_FONT);
    label.setForeground(ProfilerColors.CPU_CAPTURE_STATUS);
    label.setBorder(JBUI.Borders.empty());
    return label;
  }

  private JPanel createButtonPanel() {
    JPanel panel = new JPanel(new TabularLayout("Fit", "4px,*"));
    panel.add(createAbortButton(), new TabularLayout.Constraint(1, 0));
    return panel;
  }

  /**
   * Creates a {@link JButton} used to interrupt the current status and close the pane.
   */
  protected abstract JButton createAbortButton();

  @NotNull
  protected abstract String getDurationText();
}
