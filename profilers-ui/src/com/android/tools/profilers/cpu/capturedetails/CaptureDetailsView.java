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


import com.android.tools.adtui.instructions.InstructionsPanel;
import com.android.tools.adtui.instructions.TextInstruction;
import com.android.tools.profilers.ProfilerFonts;
import com.android.tools.profilers.StudioProfilersView;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtilities;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * A Base class for TopDown, BottomUp, CallChart and FlameChart details view.
 */
public abstract class CaptureDetailsView {
  protected static final String CARD_EMPTY_INFO = "Empty content";
  protected static final String CARD_CONTENT = "Content";

  @VisibleForTesting
  static final String NO_DATA_FOR_THREAD_MESSAGE = "No data available for the selected thread.";
  @VisibleForTesting
  static final String NO_DATA_FOR_RANGE_MESSAGE = "No data available for the selected time frame.";

  @NotNull protected final StudioProfilersView myProfilersView;

  public CaptureDetailsView(@NotNull StudioProfilersView profilersView) {
    myProfilersView = profilersView;
  }

  @NotNull
  abstract JComponent getComponent();

  protected static void switchCardLayout(@NotNull JPanel panel, boolean isEmpty) {
    CardLayout cardLayout = (CardLayout)panel.getLayout();
    cardLayout.show(panel, isEmpty ? CARD_EMPTY_INFO : CARD_CONTENT);
  }

  @NotNull
  protected static JPanel getNoDataForThread() {
    JPanel panel = new JPanel(new BorderLayout());
    InstructionsPanel info =
      new InstructionsPanel.Builder(
        new TextInstruction(UIUtilities.getFontMetrics(panel, ProfilerFonts.H3_FONT), NO_DATA_FOR_THREAD_MESSAGE))
        .setColors(JBColor.foreground(), null)
        .build();
    panel.add(info, BorderLayout.CENTER);
    return panel;
  }

  @NotNull
  protected static JComponent getNoDataForRange() {
    JPanel panel = new JPanel(new BorderLayout());
    InstructionsPanel info =
      new InstructionsPanel.Builder(
        new TextInstruction(UIUtilities.getFontMetrics(panel, ProfilerFonts.H3_FONT), NO_DATA_FOR_RANGE_MESSAGE))
        .setColors(JBColor.foreground(), null)
        .build();
    panel.add(info, BorderLayout.CENTER);
    return panel;
  }
}
