/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.theme;

import com.intellij.icons.AllIcons;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import spantable.CellSpanTable;

import javax.swing.*;
import javax.swing.border.Border;

public class ThemeEditorPanel {
  public static final Border BORDER = BorderFactory.createEmptyBorder(10, 10, 10, 10);

  private JPanel myRightPanel;
  private JComboBox myThemeCombo;
  private JCheckBox myAdvancedFilterCheckBox;
  private JButton myParentThemeButton;
  private JButton myNewThemeButton;
  private JButton myBackButton;
  private JBLabel mySubStyleLabel;
  private CellSpanTable myPropertiesTable;
  private JBScrollPane myScrollPane;

  public ThemeEditorPanel() {
    myParentThemeButton.setIcon(AllIcons.Actions.MoveUp);
    myNewThemeButton.setIcon(AllIcons.General.Add);
    myBackButton.setIcon(AllIcons.Actions.Back);

    myParentThemeButton.setBorder(BORDER);
    myNewThemeButton.setBorder(BORDER);
    myBackButton.setBorder(BORDER);
  }

  public CellSpanTable getPropertiesTable() {
    return myPropertiesTable;
  }

  public JBLabel getSubStyleLabel() {
    return mySubStyleLabel;
  }

  public JButton getBackButton() {
    return myBackButton;
  }

  public JButton getNewThemeButton() {
    return myNewThemeButton;
  }

  public JButton getParentThemeButton() {
    return myParentThemeButton;
  }

  public JCheckBox getAdvancedFilterCheckBox() {
    return myAdvancedFilterCheckBox;
  }

  public JComboBox getThemeCombo() {
    return myThemeCombo;
  }

  public JBScrollPane getScrollPane() {
    return myScrollPane;
  }

  public JPanel getComponent() {
    return myRightPanel;
  }
}
