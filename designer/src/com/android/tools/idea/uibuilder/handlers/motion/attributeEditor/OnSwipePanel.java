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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;
import java.util.Vector;

import static javax.swing.ListSelectionModel.SINGLE_SELECTION;

/**
 * Used for OnSwipeControls
 */
public class OnSwipePanel extends TagPanel {
  private Vector<String> colNames = new Vector<>(Arrays.asList("Name", "Value"));
  private Vector<Vector<Object>> data = new Vector<>();
  private DefaultTableModel myTableModel = new OnSwipeTableModel(data, colNames);
  MotionSceneModel.OnSwipeTag myOnSwipeTag;
  private JBPopupMenu myPopupMenu = new JBPopupMenu("Add Attribute");

  public OnSwipePanel(MotionLayoutAttributePanel panel) {
    super(panel);
    myTitle.setText("Touch Handling (onSwipe)");
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
    myAddRemovePanel.myAddButton.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        myPopupMenu.show(e.getComponent(), e.getX(), e.getY());
      }
    });

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
  protected void deleteAttr(int selection) {
    String attributeName = (String)myTable.getValueAt(selection, 0);
    if (myOnSwipeTag != null && myOnSwipeTag.deleteAttribute(attributeName)) {
      myTableModel.removeRow(selection);
    }
  }

  @Override
  protected void deleteTag() {
    if (myOnSwipeTag != null && myOnSwipeTag.deleteTag()) {
      setVisible(false);
    }
  }

  public ActionListener myAddItemAction = new ActionListener() {
    @Override
    public void actionPerformed(ActionEvent e) {
      String attributeName = ((JMenuItem)e.getSource()).getText();
      String value = "";
      if (myOnSwipeTag != null && myOnSwipeTag.setValue(attributeName, value)) {
        myTableModel.addRow(new Object[]{attributeName, value});
      }
    }
  };

  private void setupPopup(MotionSceneModel.OnSwipeTag tag, Set<String> strings) {
    myOnSwipeTag = tag;
    myPopupMenu.removeAll();
    String[] names = tag.getPossibleAttr();
    for (int i = 0; i < names.length; i++) {
      if (strings.contains(names[i])) {
        continue;
      }
      JMenuItem menuItem = new JMenuItem(names[i]);
      menuItem.addActionListener(myAddItemAction);
      myPopupMenu.add(menuItem);
    }
  }

  public void setOnSwipeTag(MotionSceneModel.OnSwipeTag tag) {
    setVisible( (tag != null));
    if (tag == null) {
      setVisible(false);
      return;
    }
    HashMap<String, Object> attr = tag.getAttributes();
    data.clear();
    for (String s : attr.keySet()) {
      Vector<Object> v = new Vector<>(Arrays.asList(s, attr.get(s)));
      data.add(v);
    }
    myTableModel.fireTableDataChanged();
    setupPopup(tag, attr.keySet());
    setVisible(true);
  }

  private class OnSwipeTableModel extends DefaultTableModel {

    private OnSwipeTableModel(@NotNull Vector data, @NotNull Vector columnNames) {
      super(data, columnNames);
    }

    @Override
    public void setValueAt(@NotNull Object value, int rowIndex, int columnIndex) {
      super.setValueAt(value, rowIndex, columnIndex);
      String key = getValueAt(rowIndex, 0).toString();
      myOnSwipeTag.setValue(key, (String)value);
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex == 1;
    }
  }
}
