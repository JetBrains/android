/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.strings;

import com.android.tools.idea.rendering.StringResourceData;
import com.android.tools.idea.editors.strings.table.StringResourceTableUtil;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class StringResourceViewPanel {
  private JPanel myContainer;
  private JBTable myTable;

  public StringResourceViewPanel() {
    StringResourceTableUtil.initTableView(myTable);
  }

  public void setStringResourceData(@NotNull StringResourceData data) {
    StringResourceTableUtil.initTableData(myTable, data);
  }

  @NotNull
  public JPanel getComponent() {
    return myContainer;
  }

  @NotNull
  public JBTable getPreferredFocusedComponent() {
    return myTable;
  }
}
