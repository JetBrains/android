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
package com.android.tools.idea.editors.strings.table;

import com.android.ide.common.resources.escape.xml.CharacterDataEscaper;
import com.android.tools.idea.editors.strings.StringResourceEditor;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.JBColor;
import java.awt.Component;
import javax.swing.DefaultCellEditor;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.border.LineBorder;
import javax.swing.text.JTextComponent;
import org.jetbrains.annotations.NotNull;

public final class StringTableCellEditor extends DefaultCellEditor {
  StringTableCellEditor() {
    this(new JTextField());
  }

  @VisibleForTesting
  StringTableCellEditor(@NotNull JTextField component) {
    super(component);
  }

  @VisibleForTesting
  public void setCellEditorValue(@NotNull Object value) {
    delegate.setValue(value);
  }

  @Override
  public JTextComponent getComponent() {
    return (JTextComponent)editorComponent;
  }

  @NotNull
  @Override
  public Component getTableCellEditorComponent(@NotNull JTable table, @NotNull Object value, boolean selected, int row, int column) {
    JComponent component = (JComponent)super.getTableCellEditorComponent(table, value, selected, row, column);

    component.setBorder(new LineBorder(JBColor.BLACK));
    component.setFont(StringResourceEditor.getFont(component.getFont()));

    return component;
  }

  @Override
  public boolean stopCellEditing() {
    try {
      CharacterDataEscaper.escape((String)getCellEditorValue());
      return super.stopCellEditing();
    }
    catch (IllegalArgumentException exception) {
      getComponent().setBorder(new LineBorder(JBColor.RED));
      return false;
    }
  }
}
