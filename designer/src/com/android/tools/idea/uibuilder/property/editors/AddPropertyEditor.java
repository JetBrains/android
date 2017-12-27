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

import com.android.tools.idea.uibuilder.property.AddPropertyItem;
import com.android.tools.idea.uibuilder.property.editors.support.TextEditorWithAutoCompletion;
import com.android.tools.idea.uibuilder.property.renderer.NlXmlNameRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;

/**
 * Controller of {@link javax.swing.table.TableCellEditor} for entering the name
 * of a new property in the slice property editor.
 */
public class AddPropertyEditor extends NlReferenceEditor {
  private AddPropertyItem myItem;

  public static NlTableCellEditor create(@NotNull Project project) {
    NlAddPropertyTableCellEditor cellEditor = new NlAddPropertyTableCellEditor();
    cellEditor.init(new AddPropertyEditor(project, cellEditor), null);
    return cellEditor;
  }

  protected AddPropertyEditor(@NotNull Project project, @NotNull NlEditingListener listener) {
    super(project, listener, null, false, false, false, VERTICAL_PADDING_FOR_SMALL_FONT);
  }

  private JComponent startEditing(@NotNull AddPropertyItem item) {
    myItem = item;
    TextEditorWithAutoCompletion textEditor = getTextEditor();
    textEditor.updateCompletions(item.getUnspecifiedProperties());
    textEditor.setText(item.getName());
    textEditor.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
    textEditor.setFontStyle(Font.BOLD);
    textEditor.setTextColor(NlXmlNameRenderer.ATTRIBUTE_COLOR);
    return getComponent();
  }

  @Override
  protected void editorFocusGain(@NotNull FocusEvent event) {
  }

  @Override
  protected void editorFocusLost(@NotNull FocusEvent event) {
    myItem.updateProperty();
  }

  @Override
  public void stopEditing(@Nullable Object newValue) {
    myItem.setValue(newValue);
    cancelEditing();
  }

  @Override
  protected void cancel() {
    myItem.setValue("");
    cancelEditing();
  }

  private static class NlAddPropertyTableCellEditor extends NlTableCellEditor {
    @Override
    @Nullable
    public Component getTableCellEditorComponent(@NotNull JTable table, @NotNull Object value, boolean isSelected, int row, int column) {
      AddPropertyItem item = (AddPropertyItem)value;
      AddPropertyEditor editor = (AddPropertyEditor)getEditor();
      startCellEditing(table, row);
      return editor.startEditing(item);
    }

    @Override
    public Object getCellEditorValue() {
      return null;
    }
  }
}
