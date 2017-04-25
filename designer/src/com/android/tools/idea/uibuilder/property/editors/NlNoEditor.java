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
package com.android.tools.idea.uibuilder.property.editors;

import com.android.tools.adtui.ptable.PTableCellEditor;

import javax.swing.*;
import java.awt.*;
import java.util.EventObject;

// Editor that always declines to edit (used non implemented conditions in editor providers).
public class NlNoEditor extends PTableCellEditor {
  private static NlNoEditor ourInstance;

  public static NlNoEditor getInstance() {
    if (ourInstance == null) {
      ourInstance = new NlNoEditor();
    }
    return ourInstance;
  }

  @Override
  public boolean isCellEditable(EventObject anEvent) {
    return false;
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    return null;
  }

  @Override
  public Object getCellEditorValue() {
    return null;
  }
}
