/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.attributeEditor;

import com.android.tools.idea.uibuilder.handlers.motion.MotionLayoutAttributePanel;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.MotionSceneModel;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.TimeLineIcons;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Vector;

import static javax.swing.ListSelectionModel.SINGLE_SELECTION;

/**
 * Used to show custom attributes
 */
public class CustomAttributePanel extends TagPanel {
  private final Vector<String> colNames = new Vector<>(Arrays.asList("Name", "Value"));
  private final Vector<Vector<Object>> data = new Vector<>();
  private final DefaultTableModel myTableModel = new CustomAttrTableModel(data, colNames);
  private final JBPopupMenu myPopupMenu = new JBPopupMenu("Add Attribute");
  private MotionSceneModel.CustomAttributes myCustomAttributes;

  public CustomAttributePanel(MotionLayoutAttributePanel panel) {
    super(panel);
    myTitle.setText("Custom");
    myTable = new JBTable(myTableModel);
    myRemoveTagButton = EditorUtils.makeButton(TimeLineIcons.REMOVE_TAG);
    setup();

    myTable.setSelectionMode(SINGLE_SELECTION);
    myTable.setDefaultRenderer(EditorUtils.AttributesNamesHolder.class, new EditorUtils.AttributesNamesCellRenderer());
    myTable.setDefaultRenderer(String.class, new EditorUtils.AttributesValueCellRenderer());

    myTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     boolean hasFocus,
                                                     int row,
                                                     int column) {
        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        if (!isSelected) {
          c.setForeground(column > 0 ? EditorUtils.ourValueColor : EditorUtils.ourNameColor);
        }
        return c;
      }
    });

    myPopupMenu.add(new JMenuItem("test1"));
    myAddRemovePanel.myAddButton.setVisible(false);
    myAddRemovePanel.myRemoveButton.setVisible(false);

    GridBagConstraints gbc = new GridBagConstraints();

    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 1;
    gbc.fill = GridBagConstraints.BOTH;
    add(myTitle, gbc);

    gbc.gridx = 1;
    gbc.weightx = 0;
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.EAST;
    add(myRemoveTagButton, gbc);

    gbc.gridwidth = 2;
    gbc.gridx = 0;
    gbc.weightx = 1;
    gbc.gridy++;
    gbc.fill = GridBagConstraints.BOTH;
    add(myTable, gbc);

    gbc.gridy++;
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.WEST;
    add(myAddRemovePanel, gbc);
  }

  @Override
  protected void deleteTag() {
    if (myCustomAttributes != null && myCustomAttributes.deleteTag()) {
      setVisible(false);
    }
  }

  @Override
  protected void deleteAttr(int selection) {
    throw new UnsupportedOperationException();
  }

  public void setTag(MotionSceneModel.CustomAttributes tag) {
    myCustomAttributes = tag;
    HashMap<String, Object> attr = tag.getAttributes();
    data.clear();
    for (String s : attr.keySet()) {
      Vector<Object> v = new Vector<>(Arrays.asList(s, attr.get(s)));
      data.add(v);
    }
    data.sort(Comparator.comparing(row -> ((String)row.get(0))));
    myTableModel.fireTableDataChanged();
  }

  private class CustomAttrTableModel extends DefaultTableModel {

    private CustomAttrTableModel(@NotNull Vector data, @NotNull Vector columnNames) {
      super(data, columnNames);
    }

    @Override
    public void setValueAt(@NotNull Object value, int rowIndex, int columnIndex) {
      super.setValueAt(value, rowIndex, columnIndex);
      String key = getValueAt(rowIndex, 0).toString();
      myCustomAttributes.setValue(key, (String)value);
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex == 1;
    }
  }
}
