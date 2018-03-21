/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.ui.properties;

import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;

public class ConfigPanelUi {
  public static final int V_GAP = 4;
  public static final int SEPARATOR_HEIGHT = 8;
  private JPanel myPanel;
  private JPanel myComponentPanel;

  public final JComponent getComponent() {
    return myComponentPanel;
  }

  protected final void setNumberOfProperties(int numberOfProperties) {
    GridLayoutManager layoutManager = new GridLayoutManager(numberOfProperties * 3 + 1, 1, JBUI.insets(8), 0, V_GAP, false, false);
    myPanel.setLayout(layoutManager);
    myPanel.add(new JPanel(), new GridConstraints(
      numberOfProperties * 3,
      0,
      1,
      1,
      GridConstraints.ANCHOR_CENTER,
      GridConstraints.FILL_BOTH,
      GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_WANT_GROW,
      GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_WANT_GROW,
      null,
      null,
      null,
      0,
      false));
  }

  protected final void addPropertyComponents(String label, JComponent editor) {
    int index = myPanel.getComponentCount() / 3;
    JBLabel labelComponent = new JBLabel(label);
    labelComponent.setLabelFor(editor);
    myPanel.add(labelComponent, new GridConstraints(
      index * 3,
      0,
      1,
      1,
      GridConstraints.ANCHOR_WEST,
      GridConstraints.ALIGN_LEFT,
      GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_WANT_GROW,
      GridConstraints.SIZEPOLICY_CAN_GROW,
      null,
      null,
      null,
      0,
      false));
    myPanel.add(editor, new GridConstraints(
      index * 3 + 1,
      0,
      1,
      1,
      GridConstraints.ANCHOR_WEST,
      GridConstraints.ALIGN_LEFT,
      GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_WANT_GROW,
      GridConstraints.SIZEPOLICY_CAN_GROW,
      null,
      null,
      null,
      0,
      false));
    Spacer panel = new Spacer() {
      @Override
      public Dimension getMinimumSize() {
        return new Dimension(1, SEPARATOR_HEIGHT);
      }
    };
    myPanel.add(panel, new GridConstraints(
      index * 3 + 2,
      0,
      1,
      1,
      GridConstraints.ANCHOR_WEST,
      GridConstraints.ALIGN_LEFT,
      GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_WANT_GROW,
      GridConstraints.SIZEPOLICY_CAN_GROW,
      null,
      null,
      null,
      0,
      false));
  }
}
