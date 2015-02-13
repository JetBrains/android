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
package com.android.tools.idea.editors.theme.attributes.editors;

import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.EditedStyleItem;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.rendering.ResourceHelper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.util.ui.AbstractTableCellEditor;
import org.jetbrains.android.uipreview.ChooseResourceDialog;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ColorEditor extends AbstractTableCellEditor {
  private static final Logger LOG = Logger.getInstance(ColorEditor.class);

  private final Module myModule;
  private final Configuration myConfiguration;

  private final ColorComponent myComponent;
  private Object myEditorValue = null;

  public ColorEditor(@NotNull Module module, @NotNull Configuration configuration, @NotNull JTable table) {
    myModule = module;
    myConfiguration = configuration;

    myComponent = new ColorComponent(table.getBackground(), table.getFont().deriveFont(Font.BOLD));
    myComponent.addActionListener(new ColorEditorActionListener());
    myComponent.setBorder(ColorComponent.getBorder(table.getSelectionBackground()));
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    if (value instanceof EditedStyleItem) {
      final EditedStyleItem item = (EditedStyleItem) value;
      final Color color = ResourceHelper.resolveColor(myConfiguration.getResourceResolver(), item.getItemResourceValue());
      myComponent.configure(item, color);
    } else {
      LOG.error(String.format("Object passed to ColorRendererEditor has class %1$s instead of ItemResourceValueWrapper", value.getClass().getName()));
    }
    myEditorValue = null; // invalidate stored editor value

    return myComponent;
  }

  @Override
  public Object getCellEditorValue() {
    return myEditorValue;
  }

  private class ColorEditorActionListener implements ActionListener {
    @Override
    public void actionPerformed(final ActionEvent e) {
      final ChooseResourceDialog dialog =
        new ChooseResourceDialog(myModule, ChooseResourceDialog.COLOR_TYPES, myComponent.getValue(), null);

      dialog.show();

      if (dialog.isOK()) {
        myEditorValue = dialog.getResourceName();
        ColorEditor.this.stopCellEditing();
      } else {
        myEditorValue = null;
        ColorEditor.this.cancelCellEditing();
      }
    }
  }
}
