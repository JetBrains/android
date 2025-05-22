/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.naveditor.dialogs;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

@VisibleForTesting
public class AddArgumentDialogUI {
  JPanel myContentPanel;
  JCheckBox myNullableCheckBox;
  JTextField myNameTextField;
  JPanel myDefaultValuePanel;
  JComboBox<String> myDefaultValueComboBox;
  JTextField myDefaultValueTextField;
  JBLabel myNullableLabel;
  JComboBox<AddArgumentDialog.Type> myTypeComboBox;
  JLabel myArrayLabel;
  JCheckBox myArrayCheckBox;

  public AddArgumentDialogUI() {
    setupUI();
  }

  private void setupUI() {
    myContentPanel = new JPanel();
    myContentPanel.setLayout(new GridLayoutManager(10, 2, new Insets(0, 0, 0, 0), -1, -1));
    final JBLabel jBLabel1 = new JBLabel();
    jBLabel1.setText("Name");
    myContentPanel.add(jBLabel1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                     0, false));
    final JBLabel jBLabel2 = new JBLabel();
    jBLabel2.setText("Default Value");
    myContentPanel.add(jBLabel2, new GridConstraints(8, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                     0, false));
    myNullableCheckBox = new JCheckBox();
    myNullableCheckBox.setText("");
    myContentPanel.add(myNullableCheckBox, new GridConstraints(6, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                               GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myNameTextField = new JTextField();
    myContentPanel.add(myNameTextField, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                            GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                            new Dimension(150, -1), null, 0, false));
    myTypeComboBox = new JComboBox();
    myTypeComboBox.setEditable(true);
    myContentPanel.add(myTypeComboBox, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                           null, null, null, 0, false));
    myDefaultValuePanel = new JPanel();
    myDefaultValuePanel.setLayout(new CardLayout(0, 0));
    myContentPanel.add(myDefaultValuePanel, new GridConstraints(8, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                null, null, null, 0, false));
    myDefaultValueComboBox = new JComboBox();
    myDefaultValuePanel.add(myDefaultValueComboBox, "comboDefaultValue");
    myDefaultValueTextField = new JTextField();
    myDefaultValuePanel.add(myDefaultValueTextField, "textDefaultValue");
    myArrayCheckBox = new JCheckBox();
    myArrayCheckBox.setText("");
    myContentPanel.add(myArrayCheckBox, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myNullableLabel = new JBLabel();
    myNullableLabel.setText("Nullable");
    myContentPanel.add(myNullableLabel, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                            null, 0, false));
    myArrayLabel = new JLabel();
    myArrayLabel.setText("Array");
    myContentPanel.add(myArrayLabel, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                         GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                         null, 0, false));
    final JBLabel jBLabel3 = new JBLabel();
    jBLabel3.setText("Type");
    myContentPanel.add(jBLabel3, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                     0, false));
    final Spacer spacer1 = new Spacer();
    myContentPanel.add(spacer1, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                    new Dimension(-1, 1), new Dimension(-1, 5), null, 0, false));
    final Spacer spacer2 = new Spacer();
    myContentPanel.add(spacer2, new GridConstraints(7, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                    new Dimension(-1, 1), new Dimension(-1, 5), null, 0, false));
    final Spacer spacer3 = new Spacer();
    myContentPanel.add(spacer3, new GridConstraints(9, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                    new Dimension(-1, 1), new Dimension(-1, 5), null, 0, false));
    final Spacer spacer4 = new Spacer();
    myContentPanel.add(spacer4, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                    new Dimension(-1, 1), new Dimension(-1, 5), null, 0, false));
    final Spacer spacer5 = new Spacer();
    myContentPanel.add(spacer5, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                    new Dimension(-1, 1), new Dimension(-1, 5), null, 0, false));
  }
}
