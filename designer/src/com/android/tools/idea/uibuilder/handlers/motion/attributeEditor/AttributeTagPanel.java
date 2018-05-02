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

import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.handlers.motion.MotionLayoutAttributePanel;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.MotionSceneModel;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.TimeLineIcons;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import static com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString.CustomLabel;
import static com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString.KeyPositionCartesian_transitionEasing;

/**
 * Panel used to show KeyFrames (Pos, Cycle and Attributes)
 */
public class AttributeTagPanel extends TagPanel {
  public KeyAttrTableModel myKeyAttrTableModel = new KeyAttrTableModel();
  MotionSceneModel.KeyFrame myKeyframe;
  JBPopupMenu myPopupMenu = new JBPopupMenu("Add Attribute");
  ArrayList<CustomAttributePanel> myCustomAttributePanels = new ArrayList<>();
  GridBagConstraints gbc = new GridBagConstraints();
  public HashMap<EditorUtils.AttributesNamesHolder, Object> myAttributes;
  public ArrayList<EditorUtils.AttributesNamesHolder> myAttributesNames;
  EasingCurve myEasingCurve;

  public AttributeTagPanel(MotionLayoutAttributePanel panel) {
    super(panel);
    myBasePanel = panel;
    myTable = new JBTable(myKeyAttrTableModel);
    myRemoveTagButton = EditorUtils.makeButton(TimeLineIcons.REMOVE_TAG);
    setup();

    myTable.setDefaultRenderer(EditorUtils.AttributesNamesHolder.class, new EditorUtils.AttributesNamesCellRenderer());
    myTable.setDefaultRenderer(String.class, new EditorUtils.AttributesValueCellRenderer());

    myPopupMenu.add(new JMenuItem("test1"));

    myAddRemovePanel.myAddButton.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        myPopupMenu.show(e.getComponent(), e.getX(), e.getY());
      }
    });

    myAddRemovePanel.myRemoveButton.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
         deleteAttr(myBasePanel.myNlModel,myTable.getSelectedRow());
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
    add(myRemoveTagButton, gbc);

    gbc.gridwidth = 2;
    gbc.gridx = 0;
    gbc.gridy++;
    gbc.weightx = 1;
    gbc.fill = GridBagConstraints.BOTH;
    add(myTable, gbc);

    gbc.gridy++;
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.WEST;
    add(myAddRemovePanel, gbc);
  }

  public ActionListener myAddItemAction = new ActionListener() {
    @Override
    public void actionPerformed(ActionEvent e) {
      String s = ((JMenuItem)e.getSource()).getText();
      if (CustomLabel.equals(s)) { // add custom attribute
        return;
      }
      EditorUtils.AttributesNamesHolder holder = new EditorUtils.AttributesNamesHolder(s);
      String []def = myKeyframe.getDefault(s);
      String value = def !=null && def.length > 0 ? def[0] : "";
      myKeyframe.setValue(myBasePanel.myNlModel, s, value);
      myAttributes.put(holder, value);
      myAttributesNames.add(holder);
      myKeyAttrTableModel.fireTableRowsInserted(myAttributesNames.size() - 1, myAttributesNames.size());
    }
  };

  @Override
  protected void deleteAttr(NlModel nlModel, int selection) {
    if (myKeyframe.deleteAttribute(nlModel, myKeyAttrTableModel.getValueAt(selection, 0).toString())) {
      myKeyAttrTableModel.removeRow(selection);
    }
  }

  @Override
  protected void deleteTag(NlModel nlModel) {
    if (myKeyframe.deleteTag(nlModel)) {
      setVisible(false);
      myBasePanel.clearSelectedKeyframe();
    }
  }

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

  private void setEasing(String points) {
    for (int i = 0; i < myAttributesNames.size(); i++) {
      if (KeyPositionCartesian_transitionEasing.equals(myAttributesNames.get(i).toString())) {
        myAttributes.put(myAttributesNames.get(i), points);
        myKeyAttrTableModel.fireTableCellUpdated(i,1);
      }
    }
  }

  private void saveEasing(String value) {
    myBasePanel.myCurrentKeyframe.setValue(myBasePanel.myNlModel, KeyPositionCartesian_transitionEasing, value);
  }

  public void setKeyFrame(MotionSceneModel.KeyFrame keyframe) {
    myKeyAttrTableModel.setKeyFrame(keyframe);
    myAddRemovePanel.myAddButton.setVisible(keyframe != null);
    setVisible(keyframe != null);
    if (keyframe == null) {

      return;
    }
    setupPopup(keyframe);
    if (myEasingCurve != null) {
      remove(myEasingCurve);
      myEasingCurve = null;
    }
    String easing = keyframe.getEasingCurve();
    if (easing != null) {
      myEasingCurve = new EasingCurve();
      myEasingCurve.setControlPoints(easing);
      myEasingCurve.setPreferredSize(new Dimension(200, JBUI.scale(200)));
      myEasingCurve.addActionListener(e -> setEasing(myEasingCurve.getControlPoints()));
      myEasingCurve.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
          saveEasing(myEasingCurve.getControlPoints());
        }
      });
      gbc.gridy++;
      gbc.gridwidth=2;
      gbc.fill = GridBagConstraints.BOTH;
      add(myEasingCurve, gbc);
    }

    for (CustomAttributePanel panel : myCustomAttributePanels) {
      remove(panel);
    }
    if (keyframe instanceof MotionSceneModel.KeyAttributes) {
      MotionSceneModel.KeyAttributes ka = (MotionSceneModel.KeyAttributes)keyframe;
      for (MotionSceneModel.CustomAttributes attributes : ka.getCustomAttr()) {
        CustomAttributePanel cap = new CustomAttributePanel(myBasePanel);
        cap.setTag(attributes);
        myCustomAttributePanels.add(cap);
        gbc.gridy++;
        gbc.gridwidth=2;
        gbc.fill = GridBagConstraints.BOTH;
        add(cap, gbc);

      }
    }
  }

  //=========================KeyAttrTableModel=====================================//

  class KeyAttrTableModel extends AbstractTableModel {

    @Override
    public int getRowCount() {
      if (myAttributes == null) {
        return 0;
      }
      return myAttributes.size();
    }

    public void removeRow(int rowIndex) {
      if (rowIndex < 0 || rowIndex >= getRowCount()) {
        throw new ArrayIndexOutOfBoundsException(rowIndex);
      }
      EditorUtils.AttributesNamesHolder holder = myAttributesNames.get(rowIndex);
      myAttributesNames.remove(rowIndex);
      myAttributes.remove(holder);
      fireTableRowsDeleted(rowIndex, rowIndex);
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
      return columnIndex == 1 ? String.class : EditorUtils.AttributesNamesHolder.class;
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

      if (myBasePanel.myCurrentKeyframe == null) {
        return;
      }
      String key = getValueAt(rowIndex, 0).toString();
      myBasePanel.myCurrentKeyframe.setValue(myBasePanel.myNlModel, key, aValue.toString());
    }

    public void setKeyFrame(MotionSceneModel.KeyFrame keyframe) {
      myKeyframe = keyframe;
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
        ArrayList<String> attributesNames = new ArrayList<String>(tmp.keySet());
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
