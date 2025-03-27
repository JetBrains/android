/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome.wizard.deprecated;

import com.intellij.ui.components.JBScrollPane;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.border.TitledBorder;

/**
 * Wizard step UI for selecting installation types
 */
public class InstallationTypeWizardStepForm {
  private JBScrollPane myContents;
  private JRadioButton myStandardRadioButton;
  private JRadioButton myCustomRadioButton;

  public InstallationTypeWizardStepForm() {
    setupUI();
  }

  public JBScrollPane getContents() {
    return myContents;
  }

  public JRadioButton getStandardRadioButton() {
    return myStandardRadioButton;
  }

  public JRadioButton getCustomRadioButton() {
    return myCustomRadioButton;
  }

  private void setupUI() {
    myContents = new JBScrollPane();
    myContents.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(), null, TitledBorder.DEFAULT_JUSTIFICATION,
                                                          TitledBorder.DEFAULT_POSITION, null, null));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(8, 1, new Insets(0, 0, 0, 0), -1, -1));
    myContents.setViewportView(panel1);
    final JLabel label1 = new JLabel();
    label1.setText("Choose the type of setup you want for Android Studio:");
    panel1.add(label1,
               new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    panel1.add(spacer1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                            GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 16), null, 0, false));
    myStandardRadioButton = new JRadioButton();
    myStandardRadioButton.setText("Standard");
    panel1.add(myStandardRadioButton, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                          GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JLabel label2 = new JLabel();
    label2.setText("Android Studio will be installed with the most common settings and options.");
    panel1.add(label2,
               new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 4, false));
    final JLabel label3 = new JLabel();
    label3.setText("Recommended for most users.");
    panel1.add(label3,
               new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 4, false));
    myCustomRadioButton = new JRadioButton();
    myCustomRadioButton.setText("Custom");
    panel1.add(myCustomRadioButton, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JLabel label4 = new JLabel();
    label4.setText("You can customize installation settings and components installed.");
    panel1.add(label4,
               new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 4, false));
    final Spacer spacer2 = new Spacer();
    panel1.add(spacer2, new GridConstraints(7, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                            GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    ButtonGroup buttonGroup;
    buttonGroup = new ButtonGroup();
    buttonGroup.add(myStandardRadioButton);
    buttonGroup.add(myCustomRadioButton);
  }
}
