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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies;

import com.android.tools.idea.gradle.structure.configurables.android.dependencies.details.DependencyDetails;
import com.intellij.ui.border.IdeaTitledBorder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

class DependencyInfoPanel {
  private JPanel myMainPanel;

  private JPanel myDetailsHostPanel;
  private JPanel myDetailsPanel;

  private JPanel myIssuesHostPanel;
  private JComponent myIssuesViewer;

  DependencyInfoPanel() {
    myIssuesHostPanel.setBorder(new IdeaTitledBorder("Issues", 0, new Insets(0, 0, 0, 0)));
  }

  void setDetailsViewer(@NotNull DependencyDetails details) {
    if (myDetailsPanel != null) {
      myDetailsHostPanel.remove(myDetailsPanel);
    }
    myDetailsPanel = details.getPanel();
    myDetailsHostPanel.add(myDetailsPanel, BorderLayout.CENTER);
  }

  @NotNull
  JPanel getPanel() {
    return myMainPanel;
  }

  public void setIssuesViewer(@NotNull JComponent issuesViewer) {
    if (myIssuesViewer != null) {
      myIssuesHostPanel.remove(myIssuesViewer);
    }
    myIssuesViewer = issuesViewer;
    myIssuesHostPanel.add(myIssuesViewer, BorderLayout.CENTER);
  }
}
