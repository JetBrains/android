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

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import static com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString.CustomLabel;

/**
 * Panel used to show KeyFrames (Pos, Cycle and Attributes)
 */
public class AttributeTagPanel extends TagPanel {
  private MotionLayoutAttributePanel myPanel;
  public KeyAttrTableModel myKeyAttrTableModel = new KeyAttrTableModel();
  JTable myTable = new JBTable(myKeyAttrTableModel);
  JBPopupMenu myPopupMenu = new JBPopupMenu("Add Attribute");
  EditorUtils.AddRemovePanel addRemovePanel = new EditorUtils.AddRemovePanel();
  ArrayList<CustomAttributePanel> myCustomAttributePanels = new ArrayList<>();
  GridBagConstraints gbc = new GridBagConstraints();
  public HashMap<EditorUtils.AttributesNamesHolder, Object> myAttributes;
  public ArrayList<EditorUtils.AttributesNamesHolder> myAttributesNames;

  public AttributeTagPanel(MotionLayoutAttributePanel panel) {
    myPanel = panel;
    myTable.setDefaultRenderer(EditorUtils.AttributesNamesHolder.class, new EditorUtils.AttributesNamesCellRenderer());

    myTable.setDefaultRenderer(String.class, new EditorUtils.AttributesValueCellRenderer());

    myPopupMenu.add(new JMenuItem("test1"));

    addRemovePanel.myAddButton.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        myPopupMenu.show(e.getComponent(), e.getX(), e.getY());
      }
    });

    addRemovePanel.myRemoveButton.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        myPopupMenu.show(e.getComponent(), e.getX(), e.getY());
      }
    });

    gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 1;
    gbc.gridwidth = 2;
    gbc.fill = GridBagConstraints.BOTH;
    add(myTitle, gbc);
    gbc.gridx = 1;
    gbc.weightx = 0;

    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.EAST;
    add(EditorUtils.makeButton(TimeLineIcons.REMOVE_TAG), gbc);
    gbc.gridwidth = 2;
    gbc.gridx = 0;
    gbc.gridy++;
    gbc.weightx = 1;

    gbc.fill = GridBagConstraints.BOTH;

    add(myTable, gbc);
    gbc.gridy++;
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.WEST;
    add(addRemovePanel, gbc);
    gbc.gridwidth = 2;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridy++;
  }

  public ActionListener myAddItemAction = new ActionListener() {
    @Override
    public void actionPerformed(ActionEvent e) {
      String s = ((JMenuItem)e.getSource()).getText();
      if (CustomLabel.equals(s)) { // add custom attribute
        return;
      }
      EditorUtils.AttributesNamesHolder holder = new EditorUtils.AttributesNamesHolder(s);
      myAttributes.put(holder, "");
      myAttributesNames.add(holder);
      myKeyAttrTableModel.fireTableRowsInserted(myAttributesNames.size() - 1, myAttributesNames.size());
    }
  };

  private void setupPopup(MotionSceneModel.KeyFrame keyframe) {
    myPopupMenu.removeAll();
    String[] names = keyframe.getPossibleAttr();

    HashMap<String, Object> attributes = new HashMap<>();
    keyframe.fill(attributes);
    Set<String> keys = attributes.keySet();
    for (int i = 0; i < names.length; i++) {
      if (keys.contains(names[i])) {
        continue;
      }
      JMenuItem menuItem = new JMenuItem(names[i]);
      menuItem.addActionListener(myAddItemAction);
      myPopupMenu.add(menuItem);
    }
  }

  public void setKeyFrame(MotionSceneModel.KeyFrame keyframe) {
     myKeyAttrTableModel.setKeyFrame(keyframe);
    addRemovePanel.myAddButton.setVisible(keyframe != null);
    if (keyframe == null) {
      return;
    }
    setupPopup(keyframe);
    for (CustomAttributePanel panel : myCustomAttributePanels) {
      remove(panel);
    }
    if (keyframe instanceof MotionSceneModel.KeyAttributes) {
      MotionSceneModel.KeyAttributes ka = (MotionSceneModel.KeyAttributes)keyframe;
      for (MotionSceneModel.CustomAttributes attributes : ka.getCustomAttr()) {
        CustomAttributePanel cap = new CustomAttributePanel();
        cap.setTag(attributes);
        myCustomAttributePanels.add(cap);
        add(cap, gbc);
      }
    }
  }
  //=========================KeyAttrTableModel=====================================//

  class KeyAttrTableModel extends DefaultTableModel {

    @Override
    public int getRowCount() {
      if (myAttributes == null) {
        return 0;
      }
      return myAttributes.size();
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public String getColumnName(int columnIndex) {
      return (columnIndex == 0) ? "Name" : "Value";
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return columnIndex == 1 ? String.class :EditorUtils.AttributesNamesHolder.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex == 1;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      if (myAttributesNames == null || myAttributes.size() < rowIndex) {
        return null;
      }
      if (columnIndex == 0) {
        return myAttributesNames.get(rowIndex);
      }
      return myAttributes.get(myAttributesNames.get(rowIndex));
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      // TODO update attribute
      myAttributes.put(myAttributesNames.get(rowIndex), aValue);

      if (myPanel.myCurrentKeyframe == null) {
        return;
      }
      String key = getValueAt(rowIndex, 0).toString();
      myPanel.myCurrentKeyframe.setValue(myPanel.myNlModel, key, aValue.toString());
    }

    public void setKeyFrame(MotionSceneModel.KeyFrame keyframe) {
      if (myAttributes == null) {
        myAttributes = new HashMap<>();
        myAttributesNames = new ArrayList<>();
      }
      else {
        myAttributes.clear();
        myAttributesNames.clear();
      }
      if (keyframe != null) {
        HashMap<String, Object> tmp = new HashMap<>();
        keyframe.fill(tmp);
        ArrayList<String> attributesNames =  new ArrayList<String>(tmp.keySet());
        attributesNames.sort(EditorUtils.compareAttributes);

        for (String s : attributesNames) {
          EditorUtils.AttributesNamesHolder holder = new EditorUtils.AttributesNamesHolder(s);
          myAttributesNames.add(holder);
          myAttributes.put(holder, tmp.get(s));
        }
        myTitle.setText(keyframe.getName());
      }
      fireTableDataChanged();
    }
  }
}
