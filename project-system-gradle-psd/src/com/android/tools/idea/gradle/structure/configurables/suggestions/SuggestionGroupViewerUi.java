// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.gradle.structure.configurables.suggestions;

import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import java.awt.BorderLayout;
import java.awt.Insets;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class SuggestionGroupViewerUi {
  protected JPanel myView;
  protected JPanel myPanel;

  public SuggestionGroupViewerUi(String borderTitle) {
    setupUI();
    myView.setLayout(new VerticalLayout(0));
    myView.setBorder(IdeBorderFactory.createBorder());
    myView.setLayout(new BoxLayout(myView, BoxLayout.Y_AXIS));
    myPanel.setBorder(IdeBorderFactory.createTitledBorder(borderTitle, false));
  }

  @NotNull
  public JPanel getView() {
    return myView;
  }

  @NotNull
  public JPanel getPanel() {
    return myPanel;
  }

  private void setupUI() {
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    myView = new JPanel();
    myView.setLayout(new BorderLayout(0, 0));
    myPanel.add(myView, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null,
                                            0, false));
  }
}
