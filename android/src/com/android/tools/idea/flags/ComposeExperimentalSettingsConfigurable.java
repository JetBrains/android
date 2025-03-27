/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.flags;

import com.android.tools.idea.compose.ComposeExperimentalConfiguration;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import java.awt.Insets;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class ComposeExperimentalSettingsConfigurable implements ExperimentalConfigurable {
  private JPanel myPanel;
  private JCheckBox myPreviewPickerCheckBox;
  private final ComposeExperimentalConfiguration mySettings;

  public ComposeExperimentalSettingsConfigurable() {
    setupUI();
    mySettings = ComposeExperimentalConfiguration.getInstance();
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    return mySettings.isPreviewPickerEnabled() != myPreviewPickerCheckBox.isSelected();
  }

  public void apply() {
    mySettings.setPreviewPickerEnabled(myPreviewPickerCheckBox.isSelected());
  }

  public void reset() {
    myPreviewPickerCheckBox.setSelected(mySettings.isPreviewPickerEnabled());
  }

  private void setupUI() {
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
    myPreviewPickerCheckBox = new JCheckBox();
    myPreviewPickerCheckBox.setText("Enable @Preview picker");
    myPanel.add(myPreviewPickerCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    myPanel.add(spacer1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                             GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    final JLabel label1 = new JLabel();
    label1.setEnabled(false);
    label1.setText("Popup with editing tools for @Preview annotation from the Editor gutter");
    myPanel.add(label1,
                new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 2, false));
  }

  public JComponent getRootComponent() { return myPanel; }
}
