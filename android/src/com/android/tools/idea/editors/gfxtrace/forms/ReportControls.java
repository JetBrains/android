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
package com.android.tools.idea.editors.gfxtrace.forms;

import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ReportControls {
  private JPanel myPanel;
  private JComboBox mySeverityCombo;
  private JButton myClearTagsButton;
  private JBList myTagList;

  public void setVisible(boolean v) {
    myPanel.setVisible(v);
  }

  public void addTag(@NotNull final String tag) {
    if (!((DefaultListModel)myTagList.getModel()).contains(tag)) {
      ((DefaultListModel)myTagList.getModel()).addElement(tag);
      myClearTagsButton.setEnabled(true);
    }
  }

  public void clearTags() {
    ((DefaultListModel)myTagList.getModel()).clear();
    myClearTagsButton.setEnabled(false);
  }

  public void clearFilter() {
    mySeverityCombo.setSelectedIndex(mySeverityCombo.getItemCount() - 1);
    clearTags();
  }

  public JPanel getPanel() {
    return myPanel;
  }

  public JComboBox getSeverityCombo() {
    return mySeverityCombo;
  }

  public JButton getClearTagsButton() {
    return myClearTagsButton;
  }

  public JBList getTagList() {
    return myTagList;
  }
}
