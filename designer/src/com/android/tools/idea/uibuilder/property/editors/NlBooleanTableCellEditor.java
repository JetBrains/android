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
package com.android.tools.idea.uibuilder.property.editors;

import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.ptable.PTableCellEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class NlBooleanTableCellEditor extends PTableCellEditor implements NlEditingListener {
  private NlBooleanEditor myEditor;

  public NlBooleanTableCellEditor() {
    myEditor = NlBooleanEditor.createForTable(this);
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    assert value instanceof NlProperty;
    myEditor.setProperty((NlProperty)value);
    return myEditor.getComponent();
  }

  @Override
  public Object getCellEditorValue() {
    return myEditor.getValue();
  }

  @Override
  public void activate() {
    myEditor.setNextState();
  }

  @Override
  public boolean isBooleanEditor() {
    return true;
  }

  @Override
  public void stopEditing(@NotNull NlComponentEditor editor, @Nullable Object value) {
    stopCellEditing();
  }

  @Override
  public void cancelEditing(@NotNull NlComponentEditor editor) {
    cancelCellEditing();
  }
}
