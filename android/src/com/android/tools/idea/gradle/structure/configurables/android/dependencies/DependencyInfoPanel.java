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
import com.android.tools.idea.gradle.structure.configurables.issues.IssuesViewer;
import com.android.tools.idea.gradle.structure.configurables.ui.CollapsiblePanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

class DependencyInfoPanel {
  private JPanel myMainPanel;
  private JPanel myDependencyDetailsPanel;
  private JPanel myIssuesPanel;

  DependencyInfoPanel() {
    myMainPanel.setPreferredSize(new Dimension(100, 50));
  }

  void setDependencyDetails(@NotNull DependencyDetails details) {
    ((CollapsiblePanel)myDependencyDetailsPanel).setContents(details.getPanel());
    revalidateAndRepaint(myMainPanel);
  }

  @NotNull
  JPanel getPanel() {
    return myMainPanel;
  }

  void setIssuesViewer(@NotNull IssuesViewer viewer) {
    ((CollapsiblePanel)myIssuesPanel).setContents(viewer.getPanel());
    revalidateAndRepaint(myMainPanel);
  }

  private static void revalidateAndRepaint(@NotNull JComponent c) {
    c.revalidate();
    c.repaint();
  }

  private void createUIComponents() {
    myDependencyDetailsPanel = new CollapsiblePanel("Details");
    myIssuesPanel = new CollapsiblePanel("Messages");
  }
}
