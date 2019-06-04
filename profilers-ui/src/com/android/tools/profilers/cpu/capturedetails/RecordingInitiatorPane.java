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
import com.android.tools.adtui.instructions.InstructionsPanel;
import com.android.tools.adtui.instructions.NewRowInstruction;
import com.android.tools.adtui.instructions.TextInstruction;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.stdui.StandardColors;
import com.android.tools.profilers.ProfilerFonts;
import com.android.tools.profilers.cpu.*;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.NotNull;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import java.awt.*;

/**
 * A {@link CapturePane} that contains necessary components to start recording a method trace (e.g "Record" button
 * or profiling configuration combobox).
 */
class RecordingInitiatorPane extends CapturePane {
  @VisibleForTesting
  static final String HELP_TIP_TITLE = "Thread details unavailable";

  @NotNull private final CpuProfilingConfigurationView myConfigsView;
  @NotNull private final JButton myRecordButton;

  // Intentionally local field, to prevent GC from cleaning it and removing weak listeners
  @SuppressWarnings("FieldCanBeLocal")
  @NotNull private final AspectObserver myObserver;

  RecordingInitiatorPane(@NotNull CpuProfilerStageView stageView) {
    super(stageView);
    myConfigsView = new CpuProfilingConfigurationView(stageView.getStage(), stageView.getIdeComponents());

    myRecordButton = new JButton(CpuProfilerToolbar.RECORD_TEXT);
    myRecordButton.addActionListener(event -> stageView.getStage().toggleCapturing());

    disableInteraction();
    updateView();

    myObserver = new AspectObserver();
    stageView.getStage().getAspect().addDependency(myObserver)
             .onChange(CpuProfilerAspect.PROFILING_CONFIGURATION, this::updateView);
  }

  @Override
  void populateContent(@NotNull JPanel panel) {
    if (!myStageView.getStage().getStudioProfilers().getIdeServices().getFeatureConfig().isCpuNewRecordingWorkflowEnabled()) {
      panel.add(createHelpTipInstructions(), BorderLayout.CENTER);
      return;
    }

    // TODO(b/109661512): Remove |JBUI.scale(10)| once the issue is fixed.
    JPanel content = new JPanel(new TabularLayout("*,Fit,Fit,*", "*,Fit,Fit,Fit,*").setVGap(JBUIScale.scale(10)));

    JLabel label = new JLabel("Select CPU Profiling mode");
    label.setFont(ProfilerFonts.H2_FONT);
    label.setForeground(StandardColors.TEXT_COLOR);

    ProfilingConfiguration config = myStageView.getStage().getProfilerConfigModel().getProfilingConfiguration();
    JLabel technologyDescription = new JLabel(ProfilingTechnology.fromConfig(config).getDescription());
    technologyDescription.setFont(ProfilerFonts.STANDARD_FONT);
    technologyDescription.setForeground(StandardColors.TEXT_COLOR);

    content.add(label, new TabularLayout.Constraint(1, 1));
    content.add(myConfigsView.getComponent(), new TabularLayout.Constraint(2, 1));
    content.add(myRecordButton, new TabularLayout.Constraint(2, 2));
    content.add(technologyDescription, new TabularLayout.Constraint(3, 1, 3));

    panel.add(content, BorderLayout.CENTER);
  }

  @NotNull
  private JComponent createHelpTipInstructions() {
    FontMetrics headerMetrics = SwingUtilities2.getFontMetrics(this, ProfilerFonts.H3_FONT);
    FontMetrics bodyMetrics = SwingUtilities2.getFontMetrics(this, ProfilerFonts.STANDARD_FONT);
    return new InstructionsPanel.Builder(
      new TextInstruction(headerMetrics, HELP_TIP_TITLE),
      new NewRowInstruction(NewRowInstruction.DEFAULT_ROW_MARGIN),
      new TextInstruction(bodyMetrics, "Click Record to start capturing CPU activity"),
      new NewRowInstruction(NewRowInstruction.DEFAULT_ROW_MARGIN),
      new TextInstruction(bodyMetrics, "or select a capture in the timeline.")
    ).setColors(JBColor.foreground(), null)
     .build();
  }
}
