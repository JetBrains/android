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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerFonts;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.LineBorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class handles rendering the box, labels timing, and stop/abort button. This is used when parsing and recording.
 */
public class StatusPanel extends JComponent {
  /**
   * How much time has elapsed since the profiler is in this state.
   */
  @NotNull
  private JLabel myDurationLabel = new JLabel();
  /**
   * Status itself (e.g. recording, parsing).
   */
  @NotNull
  private final String myStatus;

  @NotNull
  private final StatusPanelModel myModel;

  private final AspectObserver myObserver = new AspectObserver();

  private final JButton myAbortButton;

  /**
   * @param abortText The text shown on the button. If null, no button is added.
   */
  public StatusPanel(@NotNull StatusPanelModel captureHandler, @NotNull String status, @Nullable String abortText) {
    myModel = captureHandler;
    myStatus = status;
    myModel.getRange().addDependency(myObserver).onChange(Range.Aspect.RANGE, this::updateDuration);
    myAbortButton = abortText != null ? createAbortButton(abortText) : null;
    populateContent();
  }

  public void setAbortButtonEnabled(boolean enabled) {
    if (myAbortButton == null) return;
    myAbortButton.setEnabled(enabled);
  }

  private void populateContent() {
    // We need an outer panel, as the given panel uses BorderLayout and trying to add a fixed size box to it would make it to occupy the
    // entire panel area.
    JPanel mainPanel = new JPanel(new TabularLayout("*,300px,*", "*,150px,*"));
    // TODO(b/109661512): Move vgap scale into TabularLayout
    JPanel statusPanel = new JPanel(new TabularLayout("*,Fit,20px,Fit,*", "28px,Fit,Fit,Fit,Fit,*").setVGap(JBUI.scale(5)));
    statusPanel.setBorder(new LineBorder(ProfilerColors.CPU_CAPTURE_STATUS, 1));

    JLabel status = createLabel("Status", true);
    JLabel actualStatus = createLabel(myStatus, false);
    JLabel duration = createLabel("Duration", true);
    JLabel technology = createLabel("Type", true);
    JLabel actualTechnology = createLabel(myModel.getConfigurationText(), false);

    statusPanel.add(status, new TabularLayout.Constraint(1, 1));
    statusPanel.add(actualStatus, new TabularLayout.Constraint(1, 3));
    statusPanel.add(duration, new TabularLayout.Constraint(2, 1));
    statusPanel.add(myDurationLabel, new TabularLayout.Constraint(2, 3));
    statusPanel.add(technology, new TabularLayout.Constraint(3, 1));
    statusPanel.add(actualTechnology, new TabularLayout.Constraint(3, 3));

    // Adds the button centralized in the 3 middle columns (2nd to 4th).
    statusPanel.add(createButtonPanel(), new TabularLayout.Constraint(4, 3, 1));

    mainPanel.add(statusPanel, new TabularLayout.Constraint(1, 1));
    setLayout(new BorderLayout());
    add(mainPanel, BorderLayout.CENTER);
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
    if (myAbortButton != null) {
      panel.add(myAbortButton, new TabularLayout.Constraint(1, 0));
    }
    return panel;
  }

  @VisibleForTesting
  JLabel getDurationLabel() {
    return myDurationLabel;
  }

  @NotNull
  private String getDurationText() {
    return TimeFormatter
      .getMultiUnitDurationString(TimeUnit.NANOSECONDS.toMicros((long)myModel.getRange().getLength()));
  }

  private JButton createAbortButton(@NotNull String abortText) {
    JButton abortButton = new JButton(abortText);
    abortButton.addActionListener((event) -> {
      myModel.abort();
      abortButton.setEnabled(false);
    });
    return abortButton;
  }
}