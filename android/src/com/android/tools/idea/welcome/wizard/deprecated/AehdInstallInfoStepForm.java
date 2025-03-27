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
package com.android.tools.idea.welcome.wizard.deprecated;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/**
 * UI for AehdInstallInfoStep
 */
public class AehdInstallInfoStepForm {
  private JPanel myRoot;

  public AehdInstallInfoStepForm() {
    setupUI();
  }

  @NotNull
  public JComponent getRoot() {
    return myRoot;
  }

  private void setupUI() {
    myRoot = new JPanel();
    myRoot.setLayout(new GridLayoutManager(4, 2, new Insets(0, 0, 0, 0), -1, -1));
    final JLabel label1 = new JLabel();
    label1.setText(
      "<html>This wizard will execute Android Emulator hypervisor driver stand-alone installer. This is an additional step required to install this package. </html>");
    myRoot.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, 1,
                                           GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(10, -1), null, 0, false));
    final JLabel label2 = new JLabel();
    label2.setText("Click 'Next' to proceed");
    myRoot.add(label2,
               new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    myRoot.add(spacer1, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                            GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(-1, 150), null, 0, false));
    final Spacer spacer2 = new Spacer();
    myRoot.add(spacer2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                            GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 16), null, 0, false));
  }
}
