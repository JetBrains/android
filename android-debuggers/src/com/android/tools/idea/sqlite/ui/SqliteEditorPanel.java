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
package com.android.tools.idea.sqlite.ui;

import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class SqliteEditorPanel {
  public JPanel mainPanel;
  public JTextField deviceIdText;
  public JTextField devicePathText;
  public JPanel resultSetTitlePanel;
  public JBLabel resultSetTitleLabel;
  public JBScrollPane resultSetPane;
  public JBTable resultSetTable;
  private JPanel headerPanel;
  private JPanel filePropertiesPanel;
}
