/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.common;

import com.android.tools.profilers.IdeProfilerServices;
import com.android.tools.profilers.ProfilerColors;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.labels.SwingActionLink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.android.tools.profilers.common.CodeLocation.INVALID_LINE_NUMBER;

public class StackView {
  @NotNull
  private final IdeProfilerServices myIdeProfilerServices;

  @NotNull
  private final JBScrollPane myScrollPane;

  @NotNull
  private final JPanel myPanel;

  @Nullable
  private final Runnable myPreNavigate;

  public StackView(@NotNull IdeProfilerServices ideProfilerServices, @Nullable Runnable preNavigate) {
    myIdeProfilerServices = ideProfilerServices;
    myPanel = new JPanel(new VerticalFlowLayout());
    myPanel.setBackground(ProfilerColors.MONITOR_BACKGROUND);
    myScrollPane = new JBScrollPane(myPanel);
    myPreNavigate = preNavigate;
  }

  public void clearStackFrames() {
    myPanel.removeAll();
    // HACK: TabbedPanes like to wrap the target component with more wrapping when the component is added as a tab (it modifies the padding
    // on the target component, not a proxy panel, ugh).
    myScrollPane.setBorder(BorderFactory.createEmptyBorder());
  }

  public void setStackFrames(@NotNull String stackFrames) {
    setStackFrames(Arrays.stream(stackFrames.split("\\n")).map(StackLine::new).collect(Collectors.toList()));
  }

  public void setStackFrames(@Nullable List<StackLine> stackFrames) {
    clearStackFrames();

    if (stackFrames == null || stackFrames.isEmpty()) {
      return;
    }

    for (StackLine stackFrame : stackFrames) {
      if (stackFrame.getCodeLocation().getClassName() == null) {
        myPanel.add(new JLabel(stackFrame.getDisplayLine()));
      }
      else {
        myPanel.add(new SwingActionLink(new AbstractAction(stackFrame.getDisplayLine()) {
          @Override
          public void actionPerformed(ActionEvent e) {
            if (myPreNavigate != null) {
              myPreNavigate.run();
            }
            myIdeProfilerServices.navigateToStackFrame(stackFrame.getCodeLocation());
          }
        }));
      }
    }
  }

  @NotNull
  public JComponent getComponent() {
    return myScrollPane;
  }

  @VisibleForTesting
  @NotNull
  public JPanel getPanel() {
    return myPanel;
  }
}
