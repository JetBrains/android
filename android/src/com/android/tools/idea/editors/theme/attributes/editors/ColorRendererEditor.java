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

import com.android.ide.common.resources.ResourceResolver;
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
import javax.swing.border.Border;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ColorRendererEditor extends AbstractTableCellEditor implements TableCellRenderer {
  private static final Logger LOG = Logger.getInstance(ColorRendererEditor.class);

  private static final int PADDING = 2;

  private final Module myModule;
  private final Configuration myConfiguration;
  private final ResourceResolver myResourceResolver;

  private final Border mySelectedBorder;
  private final Border myUnselectedBorder;

  private final ColorComponent myComponent;
  private Object myEditorValue = null;

  public ColorComponent getComponent() {
    return myComponent;
  }

  public ColorRendererEditor(@NotNull Module module, @NotNull Configuration configuration, @NotNull JTable table) {
    myModule = module;
    myConfiguration = configuration;
    myResourceResolver = configuration.getResourceResolver();

    mySelectedBorder = BorderFactory.createMatteBorder(PADDING, PADDING, PADDING, PADDING, table.getSelectionBackground());
    myUnselectedBorder = BorderFactory.createMatteBorder(PADDING, PADDING, PADDING, PADDING, table.getBackground());

    myComponent = new ColorComponent(table.getBackground(), table.getFont().deriveFont(Font.BOLD));
    myComponent.addActionListener(new ColorEditorActionListener());
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object obj, boolean isSelected, boolean hasFocus, int row, int column) {
    if (obj instanceof EditedStyleItem) {
      configureComponent((EditedStyleItem) obj, table, isSelected);
    } else {
      LOG.error(String.format("Object passed to ColorRendererEditor has class %1$s instead of ItemResourceValueWrapper", obj.getClass().getName()));
    }

    return myComponent;
  }

  private void configureComponent(final EditedStyleItem resValue, final JTable table, final boolean isSelected) {
    myComponent.name = resValue.getName();
    myComponent.value = resValue.getValue();
    myComponent.setToolTipText(ThemeEditorUtils.generateToolTipText(resValue.getItemResourceValue(), myModule, myConfiguration));
    myComponent.setColor(ResourceHelper.resolveColor(myResourceResolver, resValue.getItemResourceValue()));

    myComponent.setBorder(isSelected ? mySelectedBorder : myUnselectedBorder);
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    if (value instanceof EditedStyleItem) {
      configureComponent((EditedStyleItem) value, table, true);
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
        new ChooseResourceDialog(myModule, ChooseResourceDialog.COLOR_TYPES, myComponent.value, null);

      dialog.show();

      if (dialog.isOK()) {
        myEditorValue = dialog.getResourceName();
        ColorRendererEditor.this.stopCellEditing();
      } else {
        myEditorValue = null;
        ColorRendererEditor.this.cancelCellEditing();
      }
    }
  }
}
