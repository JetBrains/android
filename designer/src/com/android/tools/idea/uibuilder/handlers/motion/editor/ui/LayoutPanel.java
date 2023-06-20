/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.editor.ui;

import static com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MeModel.EMPTY_STRING_ARRAY;

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.Annotations.Nullable;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEIcons;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEJTable;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEUI;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag.Attribute;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.Track;
import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.Debug;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.Vector;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * Shows list of views used when some one clicks on the layout in the overview.
 */
class LayoutPanel extends JPanel {

  private static boolean DEBUG = false;
  ArrayList<MTag> mParent; // mParent.get(0) is the direct parent
  MTag mMotionLayout;
  ArrayList<MTag> mDisplayedRows = new ArrayList<>();
  DefaultTableModel mConstraintSetModel = new DefaultTableModel(
    new String[]{"Type", "id", "Constrained"}, 0);
  JTable mConstraintSetTable = new MEJTable(mConstraintSetModel);
  private String mDerived;
  private MeModel mMeModel;
  private JLabel mTitle;
  private MotionEditorSelector mMotionEditorSelector;
  private boolean mBuildingTable;

  LayoutPanel() {
    super(new BorderLayout());
    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JPanel top = new JPanel(new BorderLayout());
    top.add(left, BorderLayout.WEST);
    top.add(right, BorderLayout.EAST);
    top.setBorder(MEUI.getPanelBottomBorder());
    mConstraintSetTable.setShowHorizontalLines(false);

    left.add(mTitle = new JLabel("Layout ", MEIcons.LIST_LAYOUT, SwingConstants.LEFT));
    JScrollPane transitionProperties = new JScrollPane(mConstraintSetTable);
    transitionProperties.setBorder(BorderFactory.createEmptyBorder());
    add(transitionProperties, BorderLayout.CENTER);
    add(top, BorderLayout.NORTH);
    mConstraintSetTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     boolean hasFocus,
                                                     int row,
                                                     int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        setBorder(noFocusBorder);
        return this;
      }
    });
    mConstraintSetTable.getSelectionModel().addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting()  && !mBuildingTable) {
        tableSelection();
      }
    });
  }

  private void tableSelection() {
    int index = mConstraintSetTable.getSelectedRow();
    Track.layoutTableSelect(mMeModel.myTrack);
    MTag[] tag = (index == -1 || mDisplayedRows.size() == 0) ? new MTag[0] : new MTag[]{mDisplayedRows.get(index)};
    if (tag.length != 0) {
      mMeModel.setSelectedViewIDs(Arrays.asList(Utils.stripID(tag[0].getAttributeValue("id"))));
    }
    else {
      mMeModel.setSelectedViewIDs(EMPTY_STRING_ARRAY);
    }
    mMotionEditorSelector.notifyListeners(MotionEditorSelector.Type.LAYOUT_VIEW, tag, 0);
  }

  static String[] ourLayoutDir = {"Left_", "Right_", "Start_", "End_", "Top_", "Bottom_",
    "Baseline_"};
  static String[] ourConstraintLabel = {"horizontal", "horizontal", "horizontal", "horizontal",
    "vertical", "vertical", "vertical"};
  static HashMap<String, String> labelType = new HashMap<>();

  static {
    for (int i = 0; i < ourLayoutDir.length; i++) {
      labelType.put(ourLayoutDir[i], ourConstraintLabel[i]);
    }
  }

  public void buildTable() {
    mBuildingTable = true;
    HashSet<String> found = new HashSet<>();
    mConstraintSetModel.setNumRows(0);
    mDisplayedRows.clear();
    if (mMotionLayout == null) {
      return;
    }
    else {
      MTag[] sets = mMotionLayout.getChildTags();
      for (int i = 0; i < sets.length; i++) {
        MTag view = sets[i];
        String[] row = new String[3];
        String id = Utils.stripID(view.getAttributeValue("id"));
        row[0] = view.getTagName();
        ArrayList<MTag> children = view.getChildren();
        HashMap<String, Attribute> attrs = view.getAttrList();
        row[1] = id;
        HashSet<String> constrained_sides = new HashSet<>();
        Set<String> alist = attrs.keySet();
        for (String key : alist) {
          if (key.contains("layout_")) {
            for (String s : labelType.keySet()) {
              if (key.contains(s)) {
                constrained_sides.add(labelType.get(s));
              }
            }
          }
        }
        row[2] = Arrays.toString(constrained_sides.toArray(new String[0]));
        mDisplayedRows.add(view);
        mConstraintSetModel.addRow(row);
      }
    }
    mBuildingTable = false;

    mConstraintSetModel.fireTableDataChanged();
  }

  private void updateModelIfNecessary() {
    Set<String> ids = Arrays.stream(mMotionLayout.getChildTags())
      .map(view -> Utils.stripID(view.getAttributeValue(MotionSceneAttrs.ATTR_ANDROID_ID)))
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());

    // As of JDK 11 DefaultTableModel.getDataVector has generic type Vector<Vector>, so we need to cast the resulting element to String,
    // such that the outer unchecked cast to Set<String> succeeds.
    //noinspection unchecked
    Set<String> found = (Set<String>)mConstraintSetModel.getDataVector().stream()
      .map(row -> (String)((Vector)row).get(1))
      .collect(Collectors.toSet());

    if (!ids.equals(found)) {
      buildTable();
    }
  }

  private String getMask(ArrayList<MTag> children, HashMap<String, Attribute> attrs, String id) {
    if (children.size() == 0 || attrs.size() > 1 && id != null) {
      return "all";
    }
    else {
      String mask = "";
      for (MTag child : children) {
        mask += (mask.isEmpty() ? "" : "|") + child.getTagName();
      }
      return mask;
    }
  }

  public void setMTag(@Nullable MTag layout, MeModel meModel) {
    mMeModel = meModel;
    mMotionLayout = layout;
    mDerived = null;
    String label = "Layout " + (meModel.layoutFileName);
    if (layout != null) {
      label += " (" + Utils.stripID(layout.getAttributeValue("id")) + ")";
    }
    mTitle.setText(label);

    String[] selected = mMeModel != null ? mMeModel.getSelectedViewIDs() : EMPTY_STRING_ARRAY;
    buildTable();
    if (layout != null) {
      selectByIds(selected);
    }
  }

  public void setListeners(MotionEditorSelector listeners) {
    mMotionEditorSelector = listeners;
  }

  public void selectByIds(String[] ids) {
    updateModelIfNecessary();
    HashSet<String> selectedSet = new HashSet<>(Arrays.asList(ids));
    mConstraintSetTable.clearSelection();
    for (int i = 0; i < mConstraintSetModel.getRowCount(); i++) {
      String id = (String)mConstraintSetModel.getValueAt(i, 1);
      if (selectedSet.contains(id)) {
        mConstraintSetTable.addRowSelectionInterval(i, i);
      }
    }
  }

  public void clearSelection() {
    mConstraintSetTable.clearSelection();
  }
}
