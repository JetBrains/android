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


import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.ThemeEditorContext;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.ui.resourcechooser.ChooseResourceDialog;
import com.intellij.openapi.ui.ComboBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.EnumSet;

/**
 * Custom Renderer and Editor for values of boolean attributes
 * Uses a dropdown to offer the choice between true or false or having a reference
 * Deals with references through a separate dialog window
 */
public class BooleanRendererEditor extends TypedCellRendererEditor<EditedStyleItem, String> {
  private static final String USE_REFERENCE = "Use reference ...";
  private static final String[] COMBOBOX_OPTIONS = {"true", "false", USE_REFERENCE};

  private final ComboBox myComboBox;
  private final @NotNull ThemeEditorContext myContext;
  private @Nullable String myResultValue;
  private String myEditedItemValue;

  public BooleanRendererEditor(@NotNull ThemeEditorContext context) {
    myContext = context;
    // Override isShowing because of the use of a {@link CellRendererPane}
    myComboBox = new ComboBox() {
      @Override
      public boolean isShowing() {
        return true;
      }
    };
    myComboBox.addActionListener(new BooleanChoiceListener());
  }

  @Override
  public Component getRendererComponent(JTable table, EditedStyleItem value, boolean isSelected, boolean hasFocus, int row, int column) {
    final Component component;
    if (column == 0) {
      component = table.getDefaultRenderer(String.class).getTableCellRendererComponent(table, ThemeEditorUtils.getDisplayHtml(value), isSelected, hasFocus, row, column);
    } else {
      myComboBox.removeAllItems();
      myComboBox.addItem(value.getValue());
      component = myComboBox;
    }
    return component;
  }

  @Override
  public Component getEditorComponent(JTable table, EditedStyleItem value, boolean isSelected, int row, int column) {
    myEditedItemValue = value.getValue();
    DefaultComboBoxModel comboBoxModel = new DefaultComboBoxModel(COMBOBOX_OPTIONS);
    if (!(myEditedItemValue.equals("true") || myEditedItemValue.equals("false"))) {
      comboBoxModel.insertElementAt(myEditedItemValue, 0);
    }
    myComboBox.setModel(comboBoxModel);
    myComboBox.setSelectedItem(myEditedItemValue);
    return myComboBox;
  }

  @Override
  public String getEditorValue() {
    return myResultValue;
  }

  private class BooleanChoiceListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
      String selectedValue = (String) myComboBox.getSelectedItem();
      if (USE_REFERENCE.equals(selectedValue)) {
        myComboBox.hidePopup();
        Configuration configuration = myContext.getConfiguration();
        ChooseResourceDialog dialog = ChooseResourceDialog.builder()
          .setModule(myContext.getModuleForResources())
          .setTypes(EnumSet.of(ResourceType.BOOL))
          .setCurrentValue(myEditedItemValue)
          .setConfiguration(configuration)
          .build();
        dialog.show();

        if (dialog.isOK()) {
          myResultValue = dialog.getResourceName();
          stopCellEditing();
        }
        else {
          myResultValue = null;
          cancelCellEditing();
        }
      } else {
        myResultValue = selectedValue;
        stopCellEditing();
      }
    }
  }
}
