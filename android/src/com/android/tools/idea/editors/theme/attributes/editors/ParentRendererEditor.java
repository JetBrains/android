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
package com.android.tools.idea.editors.theme.attributes.editors;


import com.android.tools.idea.configurations.ThemeSelectionDialog;
import com.android.tools.idea.editors.theme.ParentThemesListModel;
import com.android.tools.idea.editors.theme.ThemeEditorContext;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Custom Renderer and Editor for the theme parent attribute.
 * Uses a dropdown to offer the choice between Material Dark, Material Light or Other.
 * Deals with Other through a separate dialog window.
 */
public class ParentRendererEditor extends TypedCellEditor<ThemeEditorStyle, String> implements TableCellRenderer {
  private final ComboBox myComboBox;
  private @Nullable String myResultValue;
  private final ThemeEditorContext myContext;
  private final JBLabel myReadOnlyLabel;

  public ParentRendererEditor(@NotNull ThemeEditorContext context) {
    myContext = context;
    myComboBox = new ComboBox();
    //noinspection GtkPreferredJComboBoxRenderer
    myComboBox.setRenderer(new StyleListCellRenderer(context));
    myComboBox.addActionListener(new ParentChoiceListener());
    myReadOnlyLabel = new JBLabel();
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    final TableModel model = table.getModel();
    if (!model.isCellEditable(row, column)) {
      final ThemeEditorStyle style = (ThemeEditorStyle)value;
      myReadOnlyLabel.setText(style.getName());
      return myReadOnlyLabel;
    }

    myComboBox.removeAllItems();
    myComboBox.addItem(value);

    return myComboBox;
  }

  @Override
  public Component getEditorComponent(JTable table, ThemeEditorStyle value, boolean isSelected, int row, int column) {
    ImmutableList<ThemeEditorStyle> defaultThemes = ThemeEditorUtils.getDefaultThemes(myContext.getThemeResolver());
    myComboBox.setModel(new ParentThemesListModel(defaultThemes, value));
    myResultValue = value.getQualifiedName();
    return myComboBox;
  }

  @Override
  public String getEditorValue() {
    return myResultValue;
  }

  private class ParentChoiceListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
      Object selectedValue = myComboBox.getSelectedItem();
      if (ParentThemesListModel.SHOW_ALL_THEMES.equals(selectedValue)) {
        myComboBox.hidePopup();
        final ThemeSelectionDialog dialog = new ThemeSelectionDialog(myContext.getConfiguration());

        dialog.show();

        if (dialog.isOK()) {
          String theme = dialog.getTheme();
          myResultValue = theme == null ? null : theme;
          stopCellEditing();
        }
        else {
          myResultValue = null;
          cancelCellEditing();
        }
      }
      else {
        if (selectedValue instanceof ThemeEditorStyle){
          myResultValue = ((ThemeEditorStyle)selectedValue).getQualifiedName();
        }
        stopCellEditing();
      }
    }
  }
}
