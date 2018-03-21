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
import com.intellij.ui.JBColor;
import icons.StudioIcons;
import icons.StudioIllustrations;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

import static com.android.tools.profilers.ProfilerLayout.PROFILING_INSTRUCTIONS_ICON_PADDING;

/**
 * View shown if no processes are selected
 */
public class NullMonitorStageView extends StageView<NullMonitorStage> {
  private static final String ANDROID_PROFILER_TITLE = "Android Profiler";
  private static final String DEVICE_NOT_SUPPORTED_TITLE = "Device not supported";
  private static final String NO_DEVICE_MESSAGE = "No device detected. Please plug in a device, or launch the emulator.";
  private static final String NO_DEBUGGABLE_PROCESS_MESSAGE = "No debuggable processes detected for the selected device.";
  private static final String DEVICE_NOT_SUPPORTED_MESSAGE = "Android Profiler requires a device with API 21 (Lollipop) or higher.";

  @NotNull
  private NullMonitorStage myStage;

  private JLabel myTitle;
  @NotNull private final JPanel myInstructionsWrappingPanel;

  public NullMonitorStageView(@NotNull StudioProfilersView profilersView, @NotNull NullMonitorStage stage) {
    super(profilersView, stage);
    myStage = stage;

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
    myTitle.setFont(myTitle.getFont().deriveFont(21.0f));
    myTitle.setForeground(new JBColor(0x000000, 0xFFFFFF));
    topPanel.add(myTitle);
    topPanel.add(Box.createRigidArea(new Dimension(1, 15)));

    myInstructionsWrappingPanel = new JPanel();
    myInstructionsWrappingPanel.setOpaque(false);
    topPanel.add(myInstructionsWrappingPanel);
    topPanel.add(Box.createVerticalGlue());

    getComponent().add(topPanel, BorderLayout.CENTER);

    stage.getAspect().addDependency(this).onChange(NullMonitorStage.Aspect.NULL_MONITOR_TYPE, this::updateTitleAndMessage);
    updateTitleAndMessage();
  }

  private void updateTitleAndMessage() {
    myTitle.setText(getTitle());
    myInstructionsWrappingPanel.removeAll();
    myInstructionsWrappingPanel.add(new InstructionsPanel.Builder(getMessageInstructions())
                                      .setPaddings(0, 0)
                                      .setColors(ProfilerColors.MESSAGE_COLOR, ProfilerColors.DEFAULT_BACKGROUND).build());
    myInstructionsWrappingPanel.revalidate();
    myInstructionsWrappingPanel.repaint();
  }

  @Override
  public JComponent getToolbar() {
    return new JPanel();
  }

  @Override
  public boolean needsProcessSelection() {
    return true;
  }

  public String getTitle() {
    if (myStage.getStudioProfilers().getIdeServices().getFeatureConfig().isSessionsEnabled()) {
      return ANDROID_PROFILER_TITLE;
    }

    switch (myStage.getType()) {
      case UNSUPPORTED_DEVICE:
        return DEVICE_NOT_SUPPORTED_TITLE;
      case NO_DEVICE:
      case NO_DEBUGGABLE_PROCESS:
      default:
        return ANDROID_PROFILER_TITLE;
    }
  }

  public RenderInstruction[] getMessageInstructions() {
    Font font = myTitle.getFont().deriveFont(12.0f);
    java.util.List<RenderInstruction> instructions = new ArrayList<>();
    if (myStage.getStudioProfilers().getIdeServices().getFeatureConfig().isSessionsEnabled()) {
      instructions.add(new TextInstruction(font, "Click "));
      instructions.add(new IconInstruction(StudioIcons.Common.ADD, PROFILING_INSTRUCTIONS_ICON_PADDING, null));
      instructions.add(new TextInstruction(font, " to attach a process or load a capture."));
    }
    else {
      switch (myStage.getType()) {
        case NO_DEVICE:
          instructions.add(new TextInstruction(font, NO_DEVICE_MESSAGE));
          break;
        case UNSUPPORTED_DEVICE:
          instructions.add(new TextInstruction(font, DEVICE_NOT_SUPPORTED_MESSAGE));
          break;
        case NO_DEBUGGABLE_PROCESS:
        default:
          instructions.add(new TextInstruction(font, NO_DEBUGGABLE_PROCESS_MESSAGE));
          break;
      }
    }

    instructions.add(new NewRowInstruction(NewRowInstruction.DEFAULT_ROW_MARGIN));
    instructions.add(new UrlInstruction(font, "Learn More", "https://developer.android.com/r/studio-ui/about-profilers.html"));

    RenderInstruction[] instructionsArray = new RenderInstruction[instructions.size()];
    instructions.toArray(instructionsArray);
    return instructionsArray;
  }
}
