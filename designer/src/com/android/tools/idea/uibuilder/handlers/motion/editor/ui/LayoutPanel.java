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

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEIcons;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEJTable;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag.Attribute;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Shows list of views used when some one clicks on the layout in the overview.
 */
class LayoutPanel extends JPanel {

  private static boolean DEBUG = false;
  ArrayList<MTag> mParent; // mParent.get(0) is the direct parent
  MTag mMotionLayout;
  boolean building = false;

  String[] mask = {"Value", "Layout", "Motion", "Transform", "PropertySet"};
  ArrayList<MTag> mDisplayedRows = new ArrayList<>();
  DefaultTableModel mConstraintSetModel = new DefaultTableModel(
    new String[]{"Type", "id", "Constrained"}, 0);
  JTable mConstraintSetTable = new MEJTable(mConstraintSetModel);
  private String mDerived;
  boolean showAll = false;
  private MeModel mMeModel;
  private JLabel mTitle;
  private MotionEditorSelector mMotionEditorSelector;

  LayoutPanel() {
    super(new BorderLayout());
    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JPanel top = new JPanel(new BorderLayout());
    top.add(left, BorderLayout.WEST);
    top.add(right, BorderLayout.EAST);
    mConstraintSetTable.setShowHorizontalLines(false);

    left.add(mTitle = new JLabel("Layout ", MEIcons.LIST_LAYOUT, SwingConstants.LEFT));
    JScrollPane transitionProperties = new JScrollPane(mConstraintSetTable);
    transitionProperties.setBorder(BorderFactory.createEmptyBorder());
    add(transitionProperties, BorderLayout.CENTER);
    add(top, BorderLayout.NORTH);
    mConstraintSetTable.getSelectionModel().addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting()) {
        tableSelection();
      }
    });
  }

  private void tableSelection() {
    int index = mConstraintSetTable.getSelectedRow();

    MTag[] tag = (index == -1 || mDisplayedRows.size() == 0) ? new MTag[0] : new MTag[]{mDisplayedRows.get(index)};
    mMotionEditorSelector.notifyListeners(MotionEditorSelector.Type.LAYOUT_VIEW, tag);
  }

  String buildListString(MTag tag) {
    String cid = tag.getAttributeValue("id");
    int noc = tag.getChildTags().length;
    String end = tag.getAttributeValue("constraintSetEnd");
    return "<html> <b> " + cid + " </b><br>" + noc + " Constraint" + ((noc == 1) ? "" : "s")
      + "</html>";
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
    HashSet<String> found = new HashSet<>();
    mConstraintSetModel.setNumRows(0);
    mDisplayedRows.clear();
    if (mMotionLayout == null) {
      return;
    } else {
      MTag[] sets = mMotionLayout.getChildTags();
      for (int i = 0; i < sets.length; i++) {
        MTag constraint = sets[i];
        String[] row = new String[3];
        String id = Utils.stripID(constraint.getAttributeValue("id"));

        row[0] = constraint.getTagName();
        ArrayList<MTag> children = constraint.getChildren();
        HashMap<String, Attribute> attrs = constraint.getAttrList();
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
        mDisplayedRows.add(constraint);
        mConstraintSetModel.addRow(row);
      }
      if (showAll) {
        MTag[] allViews = mMeModel.layout.getChildTags();
        for (int j = 0; j < allViews.length; j++) {
          String[] row = new String[3];
          MTag view = allViews[j];
          String layoutId = view.getAttributeValue("id");
          if (layoutId == null) {
            row[0] = view.getTagName().substring(1 + view.getTagName().lastIndexOf("/"));
            continue;
          }

          layoutId = Utils.stripID(layoutId);
          if (found.contains(layoutId)) {
            continue;
          }

          row[0] = "(" + layoutId + ")";
          row[1] = "";
          row[2] = getDerived(layoutId, row);
          mDisplayedRows.add(view);
          mConstraintSetModel.addRow(row);

        }

      }

    }

    mConstraintSetModel.fireTableDataChanged();
  }

  private String getDerived(String viewId, String[] row) {
    if (mDerived != null && mParent != null && mParent.size() > 0) {
      for (MTag cSet : mParent) {
        for (MTag child : cSet.getChildren()) {
          String setName = Utils.stripID(child.getAttributeValue("id"));
          if (setName.endsWith(viewId)) {
            if (row != null) {
              row[1] = getMask(child.getChildren(), child.getAttrList(), setName);
            }
            return Utils.stripID(cSet.getAttributeValue("id"));
          }
        }
      }
    }
    return "layout";
  }

  private String getMask(ArrayList<MTag> children, HashMap<String, Attribute> attrs, String id) {
    if (children.size() == 0 || attrs.size() > 1 && id != null) {
      return "all";
    } else {
      String mask = "";
      for (MTag child : children) {
        mask += (mask.equals("") ? "" : "|") + child.getTagName();
      }
      return mask;
    }

  }

  public void setMTag(MTag layout, MeModel meModel) {
    if (DEBUG) {
      System.out.println("constraintSet = " + layout);
      System.out.println("motionScene = " + meModel.motionScene);
      System.out.println("layout = " + meModel.layout);
    }
    mMeModel = meModel;
    mMotionLayout = layout;
    mDerived = null;
    if (mMotionLayout != null) {

      MTag[] constraintSets = meModel.motionScene.getChildTags();

    }
    String label = "Layout " + (meModel.layoutFileName);
    if (layout != null) {
      label += " (" + Utils.stripID(layout.getAttributeValue("id")) + ")";
    }
    mTitle.setText(label);

    buildTable();
  }

  public void setListeners(MotionEditorSelector listeners) {
    mMotionEditorSelector = listeners;
  }

  public void selectByIds(String[] ids) {
    HashSet<String> selectedSet = new HashSet<>(Arrays.asList(ids));
    mConstraintSetTable.clearSelection();
    for (int i = 0; i < mConstraintSetModel.getRowCount(); i++) {
      String id = (String)mConstraintSetModel.getValueAt(i, 1);
      if (selectedSet.contains(id)) {
        mConstraintSetTable.addRowSelectionInterval(i, i);
      }
    }
  }
}
