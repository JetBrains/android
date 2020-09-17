/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.profilers;

import com.android.tools.adtui.instructions.*;
import com.intellij.icons.AllIcons;
import com.intellij.ui.JBColor;
import icons.StudioIllustrations;
import com.intellij.util.ui.UIUtilities;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static com.android.tools.profilers.ProfilerFonts.H1_FONT;
import static com.android.tools.profilers.ProfilerLayout.PROFILING_INSTRUCTIONS_ICON_PADDING;

/**
 * View shown if no processes are selected
 */
public class NullMonitorStageView extends StageView<NullMonitorStage> {
  private static final String ANDROID_PROFILER_TITLE = "Android Profiler";

  private JLabel myTitle;
  @NotNull private final JPanel myInstructionsWrappingPanel;

  public NullMonitorStageView(@NotNull StudioProfilersView profilersView, @NotNull NullMonitorStage stage) {
    super(profilersView, stage);

    JPanel topPanel = new JPanel();
    BoxLayout layout = new BoxLayout(topPanel, BoxLayout.Y_AXIS);
    topPanel.setLayout(layout);

    topPanel.add(Box.createVerticalGlue());
    topPanel.setBackground(ProfilerColors.DEFAULT_BACKGROUND);

    JLabel picLabel = new JLabel(StudioIllustrations.Common.DISCONNECT_PROFILER);
    picLabel.setHorizontalAlignment(SwingConstants.CENTER);
    picLabel.setVerticalAlignment(SwingConstants.CENTER);
    picLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
    topPanel.add(picLabel);

    myTitle = new JLabel();
    myTitle.setHorizontalAlignment(SwingConstants.CENTER);
    myTitle.setVerticalAlignment(SwingConstants.TOP);
    myTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
    myTitle.setFont(H1_FONT);
    myTitle.setForeground(new JBColor(0x000000, 0xFFFFFF));
    topPanel.add(myTitle);
    topPanel.add(Box.createRigidArea(new Dimension(1, 15)));

    myInstructionsWrappingPanel = new JPanel();
    myInstructionsWrappingPanel.setOpaque(false);
    topPanel.add(myInstructionsWrappingPanel);
    topPanel.add(Box.createVerticalGlue());

    getComponent().add(topPanel, BorderLayout.CENTER);
    initializeStageView();
  }

  private void initializeStageView() {
    myTitle.setText(ANDROID_PROFILER_TITLE);
    myInstructionsWrappingPanel.add(new InstructionsPanel.Builder(getMessageInstructions())
                                      .setPaddings(0, 0)
                                      .setColors(ProfilerColors.MESSAGE_COLOR, ProfilerColors.DEFAULT_BACKGROUND).build());
  }

  @Override
  public JComponent getToolbar() {
    return new JPanel();
  }

  @Override
  public boolean needsProcessSelection() {
    return true;
  }

  private RenderInstruction[] getMessageInstructions() {
    Font font = myTitle.getFont().deriveFont(12.0f);
    List<RenderInstruction> instructions = new ArrayList<>();
    FontMetrics metrics = UIUtilities.getFontMetrics(myInstructionsWrappingPanel, font);
    // Display device unsupported reason if available instead of the generic message.
    if (getStage().getUnsupportedReason() != null) {
      instructions.add(new TextInstruction(metrics, getStage().getUnsupportedReason()));
    } else {
      instructions.add(new TextInstruction(metrics, "Click "));
      instructions.add(new IconInstruction(AllIcons.General.Add, PROFILING_INSTRUCTIONS_ICON_PADDING, null));
      instructions.add(new TextInstruction(metrics, " to attach a process or load a capture."));
    }
    instructions.add(new NewRowInstruction(NewRowInstruction.DEFAULT_ROW_MARGIN));
    instructions.add(new HyperlinkInstruction(font, "Learn More", "https://developer.android.com/r/studio-ui/about-profilers.html"));

    RenderInstruction[] instructionsArray = new RenderInstruction[instructions.size()];
    instructions.toArray(instructionsArray);
    return instructionsArray;
  }
}
