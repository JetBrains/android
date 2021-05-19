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
import com.android.tools.profilers.cpu.CpuProfilerAspect;
import com.android.tools.profilers.cpu.CpuProfilerStageView;
import com.android.tools.profilers.cpu.CpuProfilerToolbar;
import com.android.tools.profilers.cpu.config.CpuProfilingConfigurationView;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration;
import com.android.tools.profilers.cpu.ProfilingTechnology;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.UIUtilities;
import java.awt.BorderLayout;
import java.awt.FontMetrics;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link CapturePane} that contains necessary components to start recording a method trace (e.g "Record" button
 * or profiling configuration combobox).
 */
class RecordingInitiatorPane extends CapturePane {
  @VisibleForTesting
  static final String HELP_TIP_TITLE = "Thread details unavailable";

  @VisibleForTesting
  static final String LEARN_MORE_MESSAGE = "Learn more";

  private static final String CONFIGURATIONS_URL = "https://d.android.com/r/studio-ui/profiler/cpu-recording-mode";

  @NotNull private final CpuProfilingConfigurationView myConfigsView;
  @NotNull private final JButton myRecordButton;

  // Intentionally local field, to prevent GC from cleaning it and removing weak listeners
  @SuppressWarnings("FieldCanBeLocal")
  @NotNull private final AspectObserver myObserver;

  RecordingInitiatorPane(@NotNull CpuProfilerStageView stageView) {
    super(stageView);
    myConfigsView = new CpuProfilingConfigurationView(stageView.getStage(), stageView.getIdeComponents());

    myRecordButton = new JButton(CpuProfilerToolbar.RECORD_TEXT);
    myRecordButton.setEnabled(stageView.getStage().getStudioProfilers().getSessionsManager().isSessionAlive());
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
    JPanel content = new JPanel(new TabularLayout("*,Fit,Fit,*", "*,Fit,Fit,Fit,Fit,*").setVGap(JBUIScale.scale(10)));

    JLabel label = new JLabel("Select CPU Profiling mode");
    label.setFont(ProfilerFonts.H2_FONT);
    label.setForeground(StandardColors.TEXT_COLOR);

    // We're using |HyperlinkLabel| instead of |JLabel|, otherwise it will not be aligned with "learn more"
    // and will have a different style, unfortunately |HyperlinkLabel| doesn't allow a custom font size.
    HyperlinkLabel technologyDescription = new HyperlinkLabel();
    ProfilingConfiguration config = myStageView.getStage().getProfilerConfigModel().getProfilingConfiguration();
    technologyDescription.setHyperlinkText(ProfilingTechnology.fromConfig(config).getDescription(), "", "");
    technologyDescription.setForeground(StandardColors.TEXT_COLOR);

    HyperlinkLabel learnMore = new HyperlinkLabel();
    learnMore.setHyperlinkText(LEARN_MORE_MESSAGE);
    learnMore.setHyperlinkTarget(CONFIGURATIONS_URL);

    // IntelliJ will put an arrow icon after the link, and in TabularLayout it's at the right edge of the
    // column. Due to other rows, column 1 is visibly wider than "Learn more". As a result, there would
    // be an undesired space between learnMore and the arrow.
    // The trick here is to make the cell of "Learn more" a single-row-two-column nested table, so the
    // "Learn more" is aligned with |technologyDescription| and the arrow icon is properly placed.
    JPanel learnMoreCell = new JPanel(new TabularLayout("Fit,*", "Fit").setVGap(JBUIScale.scale(10)));
    learnMoreCell.add(learnMore, new TabularLayout.Constraint(0, 0));

    content.add(label, new TabularLayout.Constraint(1, 1));
    content.add(myConfigsView.getComponent(), new TabularLayout.Constraint(2, 1));
    content.add(myRecordButton, new TabularLayout.Constraint(2, 2));
    content.add(technologyDescription, new TabularLayout.Constraint(3, 1, 3));
    content.add(learnMoreCell, new TabularLayout.Constraint(4, 1));

    panel.add(content, BorderLayout.CENTER);
  }

  @NotNull
  private JComponent createHelpTipInstructions() {
    FontMetrics headerMetrics = UIUtilities.getFontMetrics(this, ProfilerFonts.H3_FONT);
    FontMetrics bodyMetrics = UIUtilities.getFontMetrics(this, ProfilerFonts.STANDARD_FONT);
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
