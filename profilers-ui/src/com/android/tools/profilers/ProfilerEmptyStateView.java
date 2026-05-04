/*
 * Copyright (C) 2026 The Android Open Source Project
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

import static com.android.tools.profilers.ProfilerFonts.H1_FONT;
import static com.android.tools.profilers.ProfilerLayout.PROFILING_INSTRUCTIONS_ICON_PADDING;

import com.android.tools.adtui.instructions.HyperlinkInstruction;
import com.android.tools.adtui.instructions.IconInstruction;
import com.android.tools.adtui.instructions.InstructionsPanel;
import com.android.tools.adtui.instructions.NewRowInstruction;
import com.android.tools.adtui.instructions.RenderInstruction;
import com.android.tools.adtui.instructions.TextInstruction;
import com.intellij.icons.AllIcons;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtilities;
import icons.StudioIllustrations;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A shared view component containing an illustration, a title, and a list of instructions.
 * Used for displaying error states or placeholder messages (e.g., NullMonitorStageView, OfflineProfilerContext).
 */
public class ProfilerEmptyStateView extends JPanel {

  private JPanel myInstructionsWrappingPanel;

  /**
   * Constructs the view. If message is provided, it displays that text.
   * If null, it falls back to the default "Click + to attach a process" list.
   */
  public ProfilerEmptyStateView(@NotNull String title, @Nullable String message) {
    initLayout(title);

    Font font = H1_FONT.deriveFont(12.0f);
    FontMetrics metrics = UIUtilities.getFontMetrics(this, font);
    List<RenderInstruction> instructions = new ArrayList<>();

    if (message != null) {
      instructions.add(new TextInstruction(metrics, message));
    }
    else {
      instructions.add(new TextInstruction(metrics, "Click "));
      instructions.add(new IconInstruction(AllIcons.General.Add, PROFILING_INSTRUCTIONS_ICON_PADDING, null));
      instructions.add(new TextInstruction(metrics, " to attach a process or load a capture."));
    }

    instructions.add(new NewRowInstruction(NewRowInstruction.DEFAULT_ROW_MARGIN));
    instructions.add(new HyperlinkInstruction(font, "Learn More", "https://developer.android.com/r/studio-ui/about-profilers.html"));

    RenderInstruction[] instructionsArray = new RenderInstruction[instructions.size()];
    instructions.toArray(instructionsArray);
    initializeInstructions(instructionsArray);
  }

  private void initLayout(@NotNull String title) {
    setLayout(new BorderLayout());

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

    JLabel titleLabel = new JLabel(title);
    titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
    titleLabel.setVerticalAlignment(SwingConstants.TOP);
    titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
    titleLabel.setFont(H1_FONT);
    titleLabel.setForeground(new JBColor(0x000000, 0xFFFFFF));
    topPanel.add(titleLabel);
    topPanel.add(Box.createRigidArea(new Dimension(1, 15)));

    myInstructionsWrappingPanel = new JPanel();
    myInstructionsWrappingPanel.setOpaque(false);
    topPanel.add(myInstructionsWrappingPanel);
    topPanel.add(Box.createVerticalGlue());

    add(topPanel, BorderLayout.CENTER);
  }

  private void initializeInstructions(@NotNull RenderInstruction[] instructions) {
    myInstructionsWrappingPanel.removeAll();
    myInstructionsWrappingPanel.add(new InstructionsPanel.Builder(instructions)
                                      .setPaddings(0, 0)
                                      .setColors(ProfilerColors.MESSAGE_COLOR, ProfilerColors.DEFAULT_BACKGROUND).build());
    myInstructionsWrappingPanel.revalidate();
    myInstructionsWrappingPanel.repaint();
  }
}
