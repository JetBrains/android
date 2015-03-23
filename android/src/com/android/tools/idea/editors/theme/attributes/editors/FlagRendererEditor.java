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

import com.android.tools.idea.editors.theme.EditedStyleItem;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.util.ui.AbstractTableCellEditor;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

/**
 * Renderer and Editor for attributes that take flags as values.
 * When editing, opens a dialog with checkboxes for all the possible flags to choose from.
 */
public class FlagRendererEditor extends AbstractTableCellEditor implements TableCellRenderer {
  private final AttributeDefinitions myAttributeDefinitions;
  private final Box myBox = new Box(BoxLayout.LINE_AXIS);
  private final JLabel myLabel = new JLabel();
  private final EditorTextField myTextField = new EditorTextField();
  private EditedStyleItem myItem = null;

  public FlagRendererEditor(@Nullable AttributeDefinitions attributeDefinitions) {
    myAttributeDefinitions = attributeDefinitions;
    myBox.add(myTextField);
    myBox.add(Box.createHorizontalGlue());
    JButton editButton = new JButton();
    myBox.add(editButton);
    myTextField.setAlignmentX(Component.LEFT_ALIGNMENT);
    myTextField.setOneLineMode(true);
    editButton.setAlignmentX(Component.RIGHT_ALIGNMENT);
    editButton.setText("...");
    int buttonWidth = editButton.getFontMetrics(editButton.getFont()).stringWidth("...") + 10;
    editButton.setPreferredSize(new Dimension(buttonWidth, editButton.getHeight()));
    myLabel.setOpaque(true); // Allows for colored background

    editButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        final FlagDialog dialog = new FlagDialog();

        dialog.show();

        if (dialog.isOK()) {
          myTextField.setText(dialog.getValue());
          stopCellEditing();
        }
        else {
          cancelCellEditing();
        }
      }
    });
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    if (!(value instanceof EditedStyleItem)) {
      return null;
    }

    myItem = (EditedStyleItem)value;
    myLabel.setText(myItem.getValue());
    return myLabel;
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    if (!(value instanceof EditedStyleItem)) {
      return null;
    }

    myItem = (EditedStyleItem)value;
    myTextField.setText(myItem.getValue());
    return myBox;
  }

  @Override
  public Object getCellEditorValue() {
    return myTextField.getText();
  }

  private class FlagDialog extends DialogWrapper {
    private final HashSet<String> mySelectedFlags = new HashSet<String>();

    public FlagDialog() {
      super(false);
      String value = myItem.getValue();
      if (!StringUtil.isEmpty(value)) {
       for (String flag : Splitter.on("|").split(value)) {
         mySelectedFlags.add(flag);
       }
      }
      setTitle("Flag Options");
      init();
    }

    private class CheckBoxListener implements ActionListener {

      @Override
      public void actionPerformed(ActionEvent e) {
        JCheckBox checkbox = (JCheckBox)e.getSource();
        String name = checkbox.getText();
        if (mySelectedFlags.contains(name)) {
          mySelectedFlags.remove(name);
        }
        else {
          mySelectedFlags.add(name);
        }
      }
    }

    @Override
    protected JComponent createCenterPanel() {
      Box box = new Box(BoxLayout.PAGE_AXIS);
      if (myAttributeDefinitions != null) {
        AttributeDefinition attrDefinition = myAttributeDefinitions.getAttrDefByName(myItem.getName());
        if (attrDefinition != null) {
          String[] flagNames = attrDefinition.getValues();
          for (String flagName : flagNames) {
            JCheckBox flag = new JCheckBox(flagName);
            if (mySelectedFlags.contains(flagName)) {
              flag.setSelected(true);
            }
            flag.addActionListener(new CheckBoxListener());
            box.add(flag);
          }
        }
      }
      return box;
    }

    public String getValue() {
      return Joiner.on("|").join(mySelectedFlags);
    }
  }
}
