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
package com.android.tools.idea.uibuilder.handlers.motion;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.api.AccessoryPanelInterface;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.MotionSceneModel;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.TimeLineIcons;
import com.android.tools.idea.uibuilder.surface.AccessoryPanel;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.ui.JBColor;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

import static com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString.CustomLabel;

/**
 * Provide the Panel that is displayed during editing a KeyFrame attribute
 */
class MotionLayoutAttributePanel implements AccessoryPanelInterface {
  static Color ourNameColor = new JBColor(0x0000ff, 0xafafaf);
  static Color ourTagColor = new JBColor(0x000080, 0xe5b764);
  static Color ourValueColor = new JBColor(0x008000, 0x537f4e);
  private final ViewGroupHandler.AccessoryPanelVisibility myVisibilityCallback;
  private final NlComponent myMotionLayout;
  private JPanel myPanel;
  private NlComponent mySelection;
  KeyAttrTableModel myKeyAttrTableModel = new KeyAttrTableModel();

  private MotionLayoutTimelinePanel myTimelinePanel;
  HashMap<AttributesNamesHolder, Object> myAttributes;
  ArrayList<AttributesNamesHolder> myAttributesNames;
  private MotionSceneModel.KeyFrame myCurrentKeyframe;
  private NlModel myNlModel;
  JPanel myAttribGroups;
  TransitionPanel myTransitionPanel = new TransitionPanel();
  AttributeTagPanel myAttributeTagPanel = new AttributeTagPanel();

  public MotionLayoutAttributePanel(@NotNull NlComponent parent, @NotNull ViewGroupHandler.AccessoryPanelVisibility visibility) {
    myMotionLayout = parent;
    myVisibilityCallback = visibility;
  }

  @Override
  public @NotNull
  JPanel getPanel() {
    if (myPanel == null) {
      myPanel = createPanel(AccessoryPanel.Type.EAST_PANEL);
    }
    return myPanel;
  }

  @Override
  public @NotNull
  JPanel createPanel(@NotNull AccessoryPanel.Type type) {
    JPanel panel = new JPanel(new BorderLayout()) {
      {
        setPreferredSize(new Dimension(250, 250));
      }
    };

    myAttribGroups = new JPanel();
    myAttribGroups.setLayout(new BoxLayout(myAttribGroups, BoxLayout.Y_AXIS));
    myAttribGroups.add(myTransitionPanel);
    myAttribGroups.add(Box.createRigidArea(JBUI.size(0, 10)));
    myAttribGroups.add(myAttributeTagPanel);
    myAttribGroups.add(Box.createRigidArea(JBUI.size(0, 10)));
    myAttribGroups.add(Box.createVerticalGlue());
    panel.add(myAttribGroups, BorderLayout.CENTER);
    return panel;
  }

  @Override
  public void updateAccessoryPanelWithSelection(@NotNull AccessoryPanel.Type type, @NotNull List<NlComponent> selection) {
    if (selection.isEmpty()) {
      mySelection = null;
      myTimelinePanel = null;
      return;
    }
    mySelection = selection.get(0);
    Object property = mySelection.getClientProperty(MotionLayoutTimelinePanel.TIMELINE);
    if (property == null && mySelection.getParent() != null) {
      // need to grab the timeline from the MotionLayout component...
      // TODO: walk the tree up until we find the MotionLayout?
      property = mySelection.getParent().getClientProperty(MotionLayoutTimelinePanel.TIMELINE);
    }
    if (property != null) {
      myTimelinePanel = (MotionLayoutTimelinePanel)property;
      myTimelinePanel.setMotionLayoutAttributePanel(this);
    }
    updatePanel();
  }

  public void updateSelection() {
    updatePanel();
  }

  @Override
  public void deactivate() {
  }

  @Override
  public void updateAfterModelDerivedDataChanged() {
    updatePanel();
  }

  private void updatePanel() {
    if (myTimelinePanel != null && mySelection != null) {
      myNlModel = mySelection.getModel();
      MotionSceneModel.KeyFrame keyframe = myTimelinePanel.getSelectedKeyframe();
      if (keyframe != null) {
        MotionSceneModel.TransitionTag transitionTag = keyframe.getModel().getTransitionTag(0);
        myTransitionPanel.setTransitionTag(transitionTag);
      }
      myCurrentKeyframe = keyframe;

      // fill out the key frame attributes
      myAttributeTagPanel.setKeyFrame(keyframe);
    }
  }
  //============================AttributeTagPanel==================================//

  class AttributeTagPanel extends JPanel {
    JLabel myTitle = new JLabel("", JLabel.LEFT);
    JTable myTable = new JBTable(myKeyAttrTableModel);
    JBPopupMenu myPopupMenu = new JBPopupMenu("Add Attribute");

    JButton myAddButton = new JButton(TimeLineIcons.ADD_KEYFRAME);

    AttributeTagPanel() {
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

      myTable.setDefaultRenderer(AttributesNamesHolder.class, new AttributesNamesCellRenderer());

      myTable.setDefaultRenderer(String.class, new AttributesValueCellRenderer());
      myTitle.setForeground(ourTagColor);
      myTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
      myTitle.setBackground(Color.green);

      myAddButton.setAlignmentX(Component.LEFT_ALIGNMENT);
      myAddButton.setMargin(null);
      myAddButton.setBorderPainted(false);
      myAddButton.setOpaque(false);
      myAddButton.setUI(new BasicButtonUI());

      myPopupMenu.add(new JMenuItem("test1"));
      myPopupMenu.add(new JMenuItem("test2"));
      myPopupMenu.add(new JMenuItem("test3"));
      myPopupMenu.add(new JMenuItem("test4"));
      myAddButton.addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          myPopupMenu.show(e.getComponent(), e.getX(), e.getY());
        }
      });

      add(myTitle);
      add(myTable);
      add(myAddButton);
    }

    public ActionListener myAddItemAction = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        String s = ((JMenuItem)e.getSource()).getText();
        if (CustomLabel.equals(s)) { // add custom attribute

          return;
        }
        AttributesNamesHolder holder = new AttributesNamesHolder(s);
        myAttributes.put(holder, "");
        myAttributesNames.add(holder);
        myKeyAttrTableModel.fireTableRowsInserted(myAttributesNames.size() - 1, myAttributesNames.size());
      }
    };

    private void setupPopup(MotionSceneModel.KeyFrame keyframe) {
      myPopupMenu.removeAll();
      String[] names = keyframe.getPossibleAttr();
      for (int i = 0; i < names.length; i++) {
        JMenuItem menuItem = new JMenuItem(names[i]);
        menuItem.addActionListener(myAddItemAction);
        myPopupMenu.add(menuItem);
      }
    }

    void setKeyFrame(MotionSceneModel.KeyFrame keyframe) {
      myKeyAttrTableModel.setKeyFrame(keyframe);
      myAddButton.setVisible(keyframe != null);
      if (keyframe == null) {
        return;
      }
      setupPopup(keyframe);
      if (keyframe instanceof MotionSceneModel.KeyAttributes) {
        MotionSceneModel.KeyAttributes ka = (MotionSceneModel.KeyAttributes)keyframe;
        for (MotionSceneModel.CustomAttributes attributes : ka.getCustomAttr()) {
          CustomAttributePanel cap = new CustomAttributePanel();
          cap.setTag(attributes);
          add(cap);
        }
      }
    }
  }

  //============================TransitionPanel==================================//

  static class CustomAttributePanel extends JPanel {
    Vector<String> colNames = new Vector<String>(Arrays.asList("Name", "Value"));
    Vector<Vector<Object>> data = new Vector<>();
    DefaultTableModel myTableModel = new DefaultTableModel(data, colNames);
    JTable myTable = new JBTable(myTableModel);
    JBPopupMenu myPopupMenu = new JBPopupMenu("Add Attribute");

    CustomAttributePanel() {
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
      JLabel label = new JLabel("Custom");
      int pix = JBUI.scale(5);
      setBorder(new EmptyBorder(0, pix, 0, pix));

      myTable.setDefaultRenderer(AttributesNamesHolder.class, new AttributesNamesCellRenderer());
      myTable.setDefaultRenderer(String.class, new AttributesValueCellRenderer());
      label.setForeground(ourTagColor);
      label.setAlignmentX(Component.LEFT_ALIGNMENT);

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
            c.setForeground(column > 0 ? ourValueColor : ourNameColor);
          }

          return c;
        }
      });

      JButton button = new JButton(TimeLineIcons.ADD_KEYFRAME);
      button.setAlignmentX(Component.LEFT_ALIGNMENT);
      button.setMargin(null);
      button.setBorderPainted(false);
      button.setOpaque(false);
      button.setUI(new BasicButtonUI());

      myPopupMenu.add(new JMenuItem("test1"));

      button.addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          myPopupMenu.show(e.getComponent(), e.getX(), e.getY());
        }
      });

      add(label);
      add(myTable);
      add(button);
    }

    public ActionListener myAddItemAction = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        String s = ((JMenuItem)e.getSource()).getText();
        data.add(new Vector<Object>(Arrays.asList(s, "")));
        myTableModel.fireTableRowsInserted(data.size() - 1, data.size());
      }
    };

    private void setupPopup(MotionSceneModel.CustomAttributes tag) {
      myPopupMenu.removeAll();
      String[] names = tag.getPossibleAttr();
      for (int i = 0; i < names.length; i++) {
        JMenuItem menuItem = new JMenuItem(names[i]);
        menuItem.addActionListener(myAddItemAction);
        myPopupMenu.add(menuItem);
      }
    }

    public void setTag(MotionSceneModel.CustomAttributes tag) {
      HashMap<String, Object> attr = tag.getAttributes();
      data.clear();
      for (String s : attr.keySet()) {
        Vector<Object> v = new Vector<Object>(Arrays.asList(s, attr.get(s)));
        data.add(v);
      }
      myTableModel.fireTableDataChanged();
      setupPopup(tag);
    }
  }

  //============================TransitionPanel==================================//

  static class TransitionPanel extends JPanel {
    Vector<String> colNames = new Vector<String>(Arrays.asList("Name", "Value"));
    Vector<Vector<Object>> data = new Vector<>();
    DefaultTableModel myTableModel = new DefaultTableModel(data, colNames);
    JTable myTable = new JBTable(myTableModel);
    JBPopupMenu myPopupMenu = new JBPopupMenu("Add Attribute");

    TransitionPanel() {
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
      JLabel label = new JLabel("Transition");

      myTable.setDefaultRenderer(AttributesNamesHolder.class, new AttributesNamesCellRenderer());
      myTable.setDefaultRenderer(String.class, new AttributesValueCellRenderer());
      label.setForeground(ourTagColor);
      label.setAlignmentX(Component.LEFT_ALIGNMENT);

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
            c.setForeground(column > 0 ? ourValueColor : ourNameColor);
          }

          return c;
        }
      });

      JButton button = new JButton(TimeLineIcons.ADD_KEYFRAME);
      button.setAlignmentX(Component.LEFT_ALIGNMENT);
      button.setMargin(null);
      button.setBorderPainted(false);
      button.setOpaque(false);
      button.setUI(new BasicButtonUI());

      myPopupMenu.add(new JMenuItem("test1"));

      button.addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          myPopupMenu.show(e.getComponent(), e.getX(), e.getY());
        }
      });

      add(label);
      add(myTable);
      add(button);
    }

    public ActionListener myAddItemAction = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        String s = ((JMenuItem)e.getSource()).getText();
        data.add(new Vector<Object>(Arrays.asList(s, "")));
        myTableModel.fireTableRowsInserted(data.size() - 1, data.size());
      }
    };

    private void setupPopup(MotionSceneModel.TransitionTag tag) {
      myPopupMenu.removeAll();
      String[] names = tag.getPossibleAttr();
      for (int i = 0; i < names.length; i++) {
        JMenuItem menuItem = new JMenuItem(names[i]);
        menuItem.addActionListener(myAddItemAction);
        myPopupMenu.add(menuItem);
      }
    }

    public void setTransitionTag(MotionSceneModel.TransitionTag tag) {
      HashMap<String, Object> attr = tag.getAttributes();
      data.clear();
      for (String s : attr.keySet()) {
        Vector<Object> v = new Vector<Object>(Arrays.asList(s, attr.get(s)));
        data.add(v);
      }
      myTableModel.fireTableDataChanged();
      setupPopup(tag);
    }
  }

  //============================AttributesNamesCellRenderer==================================//
  static class AttributesNamesCellRenderer extends DefaultTableCellRenderer {

    @Override
    public void setValue(Object value) {
      setText(value.toString());
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus, int row, int col) {

      Component c = super.getTableCellRendererComponent(table, value,
                                                        isSelected, hasFocus, row, col);
      if (!isSelected) {
        setForeground(ourNameColor);
      }
      return c;
    }
  }

  //============================AttributesValueCellRenderer==================================//
  static class AttributesValueCellRenderer extends DefaultTableCellRenderer {

    @Override
    public void setValue(Object value) {
      setText(value.toString());
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus, int row, int col) {

      Component c = super.getTableCellRendererComponent(table, value,
                                                        isSelected, hasFocus, row, col);
      if (!isSelected) {
        setForeground(ourValueColor);
      }
      return c;
    }
  }

  //============================AttributesValueCellRenderer==================================//
  static class AttributesValueCellEditor extends AbstractCellEditor implements TableCellEditor {
    JComponent component = new JTextField();

    {
      component.setBorder(null);
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value,
                                                 boolean isSelected, int rowIndex, int vColIndex) {

      ((JTextField)component).setText(value.toString());

      return component;
    }

    @Override
    public Object getCellEditorValue() {
      return new AttributesValueHolder(((JTextField)component).getText());
    }

    @Override
    public boolean isCellEditable(EventObject anEvent) {
      return true;
    }

    @Override
    public boolean shouldSelectCell(EventObject anEvent) {
      return super.shouldSelectCell(anEvent);
    }

    @Override
    public boolean stopCellEditing() {
      return super.stopCellEditing();
    }

    @Override
    public void cancelCellEditing() {
      super.cancelCellEditing();
    }
  }
  //============================AttributesNamesHolder==================================//

  static class AttributesNamesHolder {
    String name;

    AttributesNamesHolder(String n) {
      name = n;
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }

    @Override
    public String toString() {
      return name;
    }

    @Override
    public boolean equals(Object obj) {
      return name.equals(obj);
    }
  }
  //============================AttributesValueHolder==================================//

  static class AttributesValueHolder {
    Object value;

    public AttributesValueHolder(Object o) {
      value = o;
    }

    public void setValue(Object value) {
      this.value = value;
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }

    @Override
    public String toString() {
      return value.toString();
    }

    @Override
    public boolean equals(Object obj) {
      return value.equals(obj);
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
      return columnIndex == 1 ? String.class : AttributesNamesHolder.class;
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

      if (myCurrentKeyframe == null) {
        return;
      }
      String key = getValueAt(rowIndex, 0).toString();
      myCurrentKeyframe.setValue(myNlModel, key, aValue.toString());
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
        for (String s : tmp.keySet()) {
          myAttributes.put(new AttributesNamesHolder(s), tmp.get(s));
        }
        myAttributesNames.addAll(myAttributes.keySet());
        myAttributeTagPanel.myTitle.setText(keyframe.getName());
      }
      fireTableDataChanged();
    }
  }
  //==============================================================//
}
