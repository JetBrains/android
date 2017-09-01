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
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.support.TextEditorWithAutoCompletion;
import com.android.tools.idea.uibuilder.property.renderer.NlXmlValueRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class NlSlicePropertyEditor extends NlReferenceEditor {

  public static NlTableCellEditor create(@NotNull Project project) {
    NlAddPropertyValueTableCellEditor cellEditor = new NlAddPropertyValueTableCellEditor();
    cellEditor.init(new NlSlicePropertyEditor(project, cellEditor), null);
    return cellEditor;
  }

  protected NlSlicePropertyEditor(@NotNull Project project, @NotNull NlEditingListener listener) {
    super(project, listener, null, false, false, false, VERTICAL_PADDING_FOR_SMALL_FONT);
  }

  @Override
  public void setProperty(@NotNull NlProperty property) {
    super.setProperty(property);
    TextEditorWithAutoCompletion textEditor = getTextEditor();
    textEditor.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
    textEditor.setFontStyle(Font.BOLD);
    textEditor.setTextColor(NlXmlValueRenderer.VALUE_COLOR);
  }

  @Override
  public void refresh() {
    // No need this editor is not used in the inspector
  }

  private static class NlAddPropertyValueTableCellEditor extends NlTableCellEditor {
    @Override
    @Nullable
    public Component getTableCellEditorComponent(@NotNull JTable table, @NotNull Object value, boolean isSelected, int row, int column) {
      if (value instanceof AddPropertyItem) {
        AddPropertyItem item = (AddPropertyItem)value;
        value = item.getProperty();
      }
      if (value instanceof NlProperty) {
        return super.getTableCellEditorComponent(table, value, isSelected, row, column);
      }
      return null;
    }

    @Override
    public Object getCellEditorValue() {
      return getEditor().getValue();
    }
  }
}
