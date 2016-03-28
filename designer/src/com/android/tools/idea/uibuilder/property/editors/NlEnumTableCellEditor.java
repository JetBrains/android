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
package com.android.tools.idea.uibuilder.property.editors;

import com.android.annotations.NonNull;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.ptable.PTableCellEditor;

import javax.swing.*;
import java.awt.*;

public class NlEnumTableCellEditor extends PTableCellEditor implements NlEnumEditor.Listener {
  private final NlEnumEditor myEnumEditor;

  private Object myValue;

  public NlEnumTableCellEditor() {
    myEnumEditor = new NlEnumEditor(this);
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    assert value instanceof NlProperty;
    myEnumEditor.setProperty((NlProperty)value);
    myValue = myEnumEditor.getValue();
    return myEnumEditor.getComponent();
  }

  @Override
  public Object getCellEditorValue() {
    return NlEnumEditor.UNSET.equals(myValue) ? null : myValue;
  }

  @Override
  public void activate() {
    myEnumEditor.showPopup();
  }

  @Override
  public void itemPicked(@NonNull NlEnumEditor source, @NonNull String value) {
    myValue = value;
    stopCellEditing();
  }

  @Override
  public void resourcePicked(@NonNull NlEnumEditor source, @NonNull String value) {
    myValue = value;
    stopCellEditing();
  }

  @Override
  public void resourcePickerCancelled(@NonNull NlEnumEditor source) {
    cancelCellEditing();
  }
}
