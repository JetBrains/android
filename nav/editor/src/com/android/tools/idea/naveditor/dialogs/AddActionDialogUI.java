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
import com.android.tools.idea.common.model.NlComponent;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.*;

/**
 * This is just a container for the fields in the add action dialog form. The logic is all in {@link AddActionDialog}
 */
@VisibleForTesting
public class AddActionDialogUI {
  JComboBox<NlComponent> myFromComboBox;
  JComboBox<AddActionDialog.DestinationListEntry> myDestinationComboBox;
  JComboBox<ValueWithDisplayString> myEnterComboBox;
  JComboBox<ValueWithDisplayString> myExitComboBox;
  JComboBox<AddActionDialog.DestinationListEntry> myPopToComboBox;
  JCheckBox myInclusiveCheckBox;
  JComboBox<ValueWithDisplayString> myPopEnterComboBox;
  JComboBox<ValueWithDisplayString> myPopExitComboBox;
  JCheckBox mySingleTopCheckBox;
  JPanel myContentPanel;
  JBTextField myIdTextField;

  public AddActionDialogUI() {
    setupUI();
  }

  private void setupUI() {
    myContentPanel = new JPanel();
    myContentPanel.setLayout(new GridLayoutManager(17, 6, new Insets(16, 4, 16, 4), -1, -1));
    myContentPanel.setOpaque(true);
    myContentPanel.setPreferredSize(new Dimension(400, 525));
    final JBLabel jBLabel1 = new JBLabel();
    jBLabel1.setText("From");
    myContentPanel.add(jBLabel1, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                     new Dimension(94, 16), null, 0, false));
    final JBLabel jBLabel2 = new JBLabel();
    jBLabel2.setText("Destination");
    myContentPanel.add(jBLabel2, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                     new Dimension(94, 16), null, 0, false));
    final Spacer spacer1 = new Spacer();
    myContentPanel.add(spacer1, new GridConstraints(3, 1, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                    GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    final TitledSeparator titledSeparator1 = new TitledSeparator();
    titledSeparator1.setText("Transition");
    myContentPanel.add(titledSeparator1, new GridConstraints(4, 1, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JBLabel jBLabel3 = new JBLabel();
    jBLabel3.setText("Enter");
    myContentPanel.add(jBLabel3, new GridConstraints(5, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                     new Dimension(94, 16), null, 0, false));
    myFromComboBox = new JComboBox();
    myContentPanel.add(myFromComboBox, new GridConstraints(1, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                           GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                           null, null, 0, false));
    myDestinationComboBox = new JComboBox();
    myContentPanel.add(myDestinationComboBox, new GridConstraints(2, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                                  GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                  null, null, null, 0, false));
    myEnterComboBox = new JComboBox();
    myContentPanel.add(myEnterComboBox, new GridConstraints(5, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                            GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                            null, null, 0, false));
    final JBLabel jBLabel4 = new JBLabel();
    jBLabel4.setText("Exit");
    myContentPanel.add(jBLabel4, new GridConstraints(6, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                     new Dimension(94, 16), null, 0, false));
    myExitComboBox = new JComboBox();
    myContentPanel.add(myExitComboBox, new GridConstraints(6, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                           GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                           null, null, 0, false));
    final Spacer spacer2 = new Spacer();
    myContentPanel.add(spacer2, new GridConstraints(9, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                    GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(94, 14), null, 0, false));
    final TitledSeparator titledSeparator2 = new TitledSeparator();
    titledSeparator2.setText("Pop Behavior");
    myContentPanel.add(titledSeparator2, new GridConstraints(10, 1, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JBLabel jBLabel5 = new JBLabel();
    jBLabel5.setText("Pop To");
    myContentPanel.add(jBLabel5, new GridConstraints(11, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                     new Dimension(94, 16), null, 0, false));
    myPopToComboBox = new JComboBox();
    myContentPanel.add(myPopToComboBox, new GridConstraints(11, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                            GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                            null, null, 0, false));
    final JBLabel jBLabel6 = new JBLabel();
    jBLabel6.setText("Inclusive");
    myContentPanel.add(jBLabel6, new GridConstraints(12, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                     new Dimension(94, 16), null, 0, false));
    myInclusiveCheckBox = new JCheckBox();
    myInclusiveCheckBox.setSelected(false);
    myInclusiveCheckBox.setText("");
    myContentPanel.add(myInclusiveCheckBox, new GridConstraints(12, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer3 = new Spacer();
    myContentPanel.add(spacer3, new GridConstraints(13, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                    GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(94, 14), null, 0, false));
    final TitledSeparator titledSeparator3 = new TitledSeparator();
    titledSeparator3.setText("Launch Options");
    myContentPanel.add(titledSeparator3, new GridConstraints(14, 1, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JBLabel jBLabel7 = new JBLabel();
    jBLabel7.setText("Single Top");
    myContentPanel.add(jBLabel7, new GridConstraints(15, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                     new Dimension(94, 16), null, 0, false));
    mySingleTopCheckBox = new JCheckBox();
    mySingleTopCheckBox.setText("");
    myContentPanel.add(mySingleTopCheckBox, new GridConstraints(15, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer4 = new Spacer();
    myContentPanel.add(spacer4, new GridConstraints(3, 4, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                    GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, new Dimension(150, -1), null, 0, false));
    myIdTextField = new JBTextField();
    myContentPanel.add(myIdTextField, new GridConstraints(0, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                          GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                          new Dimension(150, -1), null, 0, false));
    final JLabel label1 = new JLabel();
    label1.setText("ID");
    myContentPanel.add(label1, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                   GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                   new Dimension(94, 16), null, 0, false));
    myPopEnterComboBox = new JComboBox();
    myPopEnterComboBox.setEnabled(true);
    myContentPanel.add(myPopEnterComboBox, new GridConstraints(7, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                               GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                               null, null, 0, false));
    myPopExitComboBox = new JComboBox();
    myPopExitComboBox.setEnabled(true);
    myContentPanel.add(myPopExitComboBox, new GridConstraints(8, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                              GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                              null, null, 0, false));
    final JBLabel jBLabel8 = new JBLabel();
    jBLabel8.setText("Pop Enter");
    myContentPanel.add(jBLabel8, new GridConstraints(7, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                     new Dimension(94, 16), null, 0, false));
    final JBLabel jBLabel9 = new JBLabel();
    jBLabel9.setText("Pop Exit");
    myContentPanel.add(jBLabel9, new GridConstraints(8, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                     new Dimension(94, 16), null, 0, false));
    final Spacer spacer5 = new Spacer();
    myContentPanel.add(spacer5, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                    GridConstraints.SIZEPOLICY_FIXED, 1, new Dimension(8, -1), new Dimension(8, -1),
                                                    new Dimension(8, -1), 0, false));
  }
}
