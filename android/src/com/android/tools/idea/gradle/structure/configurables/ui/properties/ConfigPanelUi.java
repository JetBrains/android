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
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class ConfigPanelUi {
  public static final int V_GAP = 4;
  public static final int H_GAP = 4;
  private int myLastRow = -1;
  private JPanel myPanel;
  private JPanel myComponentPanel;

  public final JComponent getComponent() {
    return myComponentPanel;
  }

  protected final void setNumberOfProperties(int numberOfProperties) {
    myPanel.add(new JPanel(), new GridBagConstraints(
      0,
      numberOfProperties * 3,
      1,
      1,
      1,
      1,
      GridBagConstraints.CENTER,
      GridBagConstraints.VERTICAL,
      JBUI.emptyInsets(),
      0, 0));
  }

  protected final void addPropertyComponents(@NotNull String label, @NotNull JComponent editor, @Nullable JComponent statusComponent) {
    myLastRow++;
    JBLabel labelComponent = new JBLabel(label);
    labelComponent.setLabelFor(editor);
    myPanel.add(labelComponent, new GridBagConstraints(
      0,
      myLastRow * 3,
      2,
      1,
      1,
      0,

      GridBagConstraints.WEST,
      GridBagConstraints.HORIZONTAL,
      JBUI.insets(2 * V_GAP, H_GAP, 0, H_GAP),
      0,
      0));
    myPanel.add(editor, new GridBagConstraints(
      0,
      myLastRow * 3 + 1,
      statusComponent == null ? 2 : 1,
      1,
      1,
      0,
      GridBagConstraints.WEST,
      GridBagConstraints.HORIZONTAL,
      JBUI.insets(0, H_GAP, 0, H_GAP),
      0,
      0));
    if (statusComponent != null) {
      myPanel.add(statusComponent, new GridBagConstraints(
        1,
        myLastRow * 3 + 1,
        1,
        1,
        2,
        0,
        GridBagConstraints.WEST,
        GridBagConstraints.HORIZONTAL,
        JBUI.insets(0, H_GAP, 0, H_GAP),
        0,
        0));
    }
  }
}
