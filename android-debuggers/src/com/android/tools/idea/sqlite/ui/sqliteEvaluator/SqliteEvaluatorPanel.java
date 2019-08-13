/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.sqlite.ui.sqliteEvaluator;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;

public class SqliteEvaluatorPanel {
  private JPanel myRoot;
  private JButton myEvaluateButton;
  private JPanel myControlsContainer;
  private JComboBox<SqliteEvaluatorViewImpl.ComboBoxItem> mySchemaComboBox;

  public JPanel getRoot() {
    return myRoot;
  }

  public JButton getEvaluateButton() {
    return myEvaluateButton;
  }

  public JPanel getControlsContainer() {
    return myControlsContainer;
  }

  public JComboBox<SqliteEvaluatorViewImpl.ComboBoxItem> getSchemaComboBox() {
    return mySchemaComboBox;
  }
}
