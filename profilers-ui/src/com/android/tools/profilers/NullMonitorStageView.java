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

import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.TooltipComponent;
import com.android.tools.adtui.model.Range;
import com.android.tools.profilers.cpu.CpuMonitor;
import com.android.tools.profilers.cpu.CpuMonitorTooltipView;
import com.android.tools.profilers.cpu.CpuMonitorView;
import com.android.tools.profilers.event.EventMonitor;
import com.android.tools.profilers.event.EventMonitorTooltipView;
import com.android.tools.profilers.event.EventMonitorView;
import com.android.tools.profilers.memory.MemoryMonitor;
import com.android.tools.profilers.memory.MemoryMonitorTooltipView;
import com.android.tools.profilers.memory.MemoryMonitorView;
import com.android.tools.profilers.network.NetworkMonitor;
import com.android.tools.profilers.network.NetworkMonitorTooltipView;
import com.android.tools.profilers.network.NetworkMonitorView;
import com.intellij.util.ui.JBImageIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * View shown if advanced profiling is not enabled
 */
public class NullMonitorStageView extends StageView<NullMonitorStage> {

  public NullMonitorStageView(@NotNull StudioProfilersView profilersView, @NotNull NullMonitorStage stage) {
    super(profilersView, stage);

    JPanel topPanel = new JPanel(new TabularLayout("*", "*,*"));
    topPanel.setBackground(ProfilerColors.MONITOR_BACKGROUND);

    JLabel picLabel = new JLabel(ProfilerIcons.ANDROID_PROFILERS);
    picLabel.setHorizontalAlignment(SwingConstants.CENTER);
    picLabel.setVerticalAlignment(SwingConstants.BOTTOM);
    topPanel.add(picLabel, new TabularLayout.Constraint(0, 0, 1));

    JLabel disabledMessage = new JLabel("No device detected, please plug in a device, or launch the emulator.");
    disabledMessage.setHorizontalAlignment(SwingConstants.CENTER);
    disabledMessage.setVerticalAlignment(SwingConstants.TOP);
    topPanel.add(disabledMessage, new TabularLayout.Constraint(1, 0, 1));

    //TODO: Add button to launch AVD Manager, requires adding a dependency on android project.
    getComponent().add(topPanel, BorderLayout.CENTER);
  }

  @Override
  public JComponent getToolbar() {
    return new JPanel();
  }

  @Override
  public boolean needsProcessSelection() {
    return true;
  }
}
