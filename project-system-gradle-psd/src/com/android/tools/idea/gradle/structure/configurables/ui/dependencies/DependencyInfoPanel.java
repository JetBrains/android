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
package com.android.tools.idea.gradle.structure.configurables.ui.dependencies;

import com.android.tools.idea.gradle.structure.configurables.dependencies.details.DependencyDetails;
import com.android.tools.idea.gradle.structure.configurables.issues.IssuesViewer;
import com.android.tools.idea.gradle.structure.configurables.ui.CollapsiblePanel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import javax.swing.border.TitledBorder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static com.android.tools.idea.gradle.structure.configurables.ui.UiUtil.revalidateAndRepaint;

class DependencyInfoPanel {
  private JPanel myMainPanel;
  private JPanel myDependencyDetailsPanel;
  private JPanel myIssuesPanel;

  public DependencyInfoPanel() {
    setupUI();
  }
  void setDependencyDetails(@NotNull DependencyDetails details) {
    ((CollapsiblePanel)myDependencyDetailsPanel).setContents(details.getPanel());
    revalidateAndRepaintPanel();
  }

  @NotNull
  JPanel getPanel() {
    return myMainPanel;
  }

  void setIssuesViewer(@NotNull IssuesViewer viewer) {
    myIssuesPanel.add(viewer.getPanel(), BorderLayout.CENTER);
    revalidateAndRepaintPanel();
  }

  private void createUIComponents() {
    myDependencyDetailsPanel = new CollapsiblePanel("Details");
    myIssuesPanel = new JPanel(new BorderLayout());
  }

  void revalidateAndRepaintPanel() {
    revalidateAndRepaint(myMainPanel);
  }

  private void setupUI() {
    createUIComponents();
    myMainPanel = new JPanel();
    myMainPanel.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
    myMainPanel.setBorder(
      BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5), null, TitledBorder.DEFAULT_JUSTIFICATION,
                                       TitledBorder.DEFAULT_POSITION, null, null));
    myMainPanel.add(myDependencyDetailsPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL,
                                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                  GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                  null, null, null, 0, false));
    myMainPanel.add(myIssuesPanel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null,
                                                       null, null, 0, false));
  }
}
