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
package com.android.tools.idea.gradle.structure.dependencies;

import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.*;
import javax.swing.border.TitledBorder;

class LibraryDependenciesFormUi {

  protected JPanel myMainPanel;
  protected SimpleColoredComponent myLibraryLabel;
  protected JPanel mySearchPanelHost;

  LibraryDependenciesFormUi() {
    setupUI();
    myLibraryLabel.setBorder(UIUtil.getTextFieldBorder());
    myLibraryLabel.setIpad(JBInsets.emptyInsets());
    myLibraryLabel.clear();
  }

  private void setupUI() {
    myMainPanel = new JPanel();
    myMainPanel.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
    myMainPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(), null, TitledBorder.DEFAULT_JUSTIFICATION,
                                                           TitledBorder.DEFAULT_POSITION, null, null));
    final JBLabel jBLabel1 = new JBLabel();
    jBLabel1.setComponentStyle(UIUtil.ComponentStyle.SMALL);
    jBLabel1.setText("Library:");
    jBLabel1.setDisplayedMnemonic('L');
    jBLabel1.setDisplayedMnemonicIndex(0);
    myMainPanel.add(jBLabel1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                  GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                  false));
    myLibraryLabel = new SimpleColoredComponent();
    myMainPanel.add(myLibraryLabel, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                        GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                        new Dimension(150, -1), null, 0, false));
    mySearchPanelHost = new JPanel();
    mySearchPanelHost.setLayout(new BorderLayout(0, 0));
    myMainPanel.add(mySearchPanelHost, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                           null, null, null, 0, false));
    jBLabel1.setLabelFor(myLibraryLabel);
  }
}
