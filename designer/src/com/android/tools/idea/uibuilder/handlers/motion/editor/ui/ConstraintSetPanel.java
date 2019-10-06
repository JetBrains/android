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

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.Annotations.NotNull;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.Annotations.Nullable;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEIcons;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEJTable;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEScrollPane;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEUI;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag.Attribute;
import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.Debug;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;

/**
 * This displays the constraint panel
 */
class ConstraintSetPanel extends JPanel {

  private MTag mSelectedTag; // the Primary selection
  private MTag[] mMultiSelectedTag; // the list if you are supporting multi-select
  MotionEditorSelector mListeners;
  private static boolean DEBUG = false;
  ArrayList<MTag> mParent; // mParent.get(0) is the direct parent
  MTag mConstraintSet;
  ArrayList<MTag> mDisplayedRows = new ArrayList<>();
  boolean building = false;
  Icon[] types = {MEIcons.LIST_STATE, MEIcons.LIST_GRAY_STATE, MEIcons.LIST_LAYOUT};
  String[] mask = {"Value", "Layout", "Motion", "Transform", "PropertySet"};
  JPopupMenu myPopupMenu = new JPopupMenu();

  DefaultTableModel mConstraintSetModel = new DefaultTableModel(
    new String[]{"Included", "ID", "Defined In"}, 0) {

    @Override
    public Class getColumnClass(int column) {
      return (column == 0) ? Icon.class : String.class;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
      return false;
    }
  };

  JTable mConstraintSetTable = new MEJTable(mConstraintSetModel);
  private String mDerived;
  boolean showAll = true;
  private MeModel mMeModel;
  private final JLabel mTitle;
  JButton mModifyMenu;
  boolean mBuildingTable;

  AbstractAction createConstraint = new AbstractAction("Create Constraint") {
    @Override
    public void actionPerformed(ActionEvent e) {
      ConstraintSetPanelCommands.createConstraint(mSelectedTag, mConstraintSet);
      buildTable();
    }
  };

  AbstractAction createSectionedConstraint = new AbstractAction("Create Sectioned Constraint") {
    @Override
    public void actionPerformed(ActionEvent e) {
      System.out.println(mSelectedTag == null ? "null" : mSelectedTag.getTagName());
      ConstraintSetPanelCommands.createSectionedConstraint(mMultiSelectedTag, mConstraintSet);
      buildTable();
    }
  };

  AbstractAction clearConstraint = new AbstractAction("Clear Constraint") {
    @Override
    public void actionPerformed(ActionEvent e) {
      ConstraintSetPanelCommands.clearConstraint(mSelectedTag, mConstraintSet);
      buildTable();
    }
  };
  AbstractAction moveConstraint = new AbstractAction("Move Constraints to layout") {
    @Override
    public void actionPerformed(ActionEvent e) {
      ConstraintSetPanelCommands.moveConstraint(mSelectedTag, mConstraintSet);
    }
  };

  AbstractAction overrideConstraint = new AbstractAction("Convert from sectioned constraints") {
    @Override
    public void actionPerformed(ActionEvent e) {
      ConstraintSetPanelCommands.convertFromSectioned(mSelectedTag, mConstraintSet);
    }
  };
  AbstractAction limitConstraint = new AbstractAction("Limit constraints to sections") {
    @Override
    public void actionPerformed(ActionEvent e) {

    }
  };
  private String mConstraintSetId;

  ConstraintSetPanel() {
    super(new BorderLayout());
    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JPanel top = new JPanel(new BorderLayout());
    top.add(left, BorderLayout.WEST);
    top.add(right, BorderLayout.EAST);
    mConstraintSetTable.getColumnModel().getColumn(0).setPreferredWidth(MEUI.scale(16));
    mConstraintSetTable.setShowHorizontalLines(false);
    JCheckBox cbox = new JCheckBox("All");
    cbox.setSelected(true);
    cbox.addActionListener(e -> {
                             showAll = cbox.isSelected();
                             buildTable();
                           }
    );
    JLabel label;
    left.add(label = new JLabel("ConstraintSet (", MEIcons.CONSTRAINT_SET, SwingConstants.LEFT));
    left.add(mTitle = new JLabel("", SwingConstants.LEFT));
    left.add(label = new JLabel(")", SwingConstants.LEFT));
    makeRightMenu(right);
    right.add(cbox);

    mConstraintSetTable.getSelectionModel().addListSelectionListener(
      e -> {
        if (mBuildingTable) {
          return;
        }
        int index = mConstraintSetTable.getSelectedRow();
        int[] allSelect = mConstraintSetTable.getSelectedRows();

        mModifyMenu.setEnabled(index != -1);
        mSelectedTag = null;

        if (index == -1) {
          mSelectedTag = null;
          if (mConstraintSet != null) {
            mListeners
              .notifyListeners(MotionEditorSelector.Type.CONSTRAINT_SET, new MTag[]{mConstraintSet});
          }
          return;
        }
        mMultiSelectedTag = new MTag[allSelect.length];
        for (int i = 0; i < allSelect.length; i++) {
          int k = allSelect[i];
          mMultiSelectedTag[i] = mDisplayedRows.get(k);
        }
        MTag[] tag = mDisplayedRows.isEmpty() ? new MTag[0] : new MTag[]{mSelectedTag = mDisplayedRows.get(index)};
        mListeners.notifyListeners(MotionEditorSelector.Type.CONSTRAINT, tag);
        enableMenuItems(tag);
      }
    );
    JScrollPane transitionProperties = new MEScrollPane(mConstraintSetTable);
    transitionProperties.setBorder(BorderFactory.createEmptyBorder());
    add(transitionProperties, BorderLayout.CENTER);
    add(top, BorderLayout.NORTH);
  }

  private void enableMenuItems(MTag[] selected) {
    boolean hasSelection = selected.length > 0;
    mModifyMenu.setEnabled(hasSelection);
    if (!hasSelection) {
      return;
    }
    boolean inCurrentSelection = false;

    MTag[] tags = mConstraintSet.getChildTags();
    for (int i = 0; i < tags.length; i++) {
      if (tags[i].equals(selected[0])) {
        inCurrentSelection = true;
        break;
      }
    }
    if (inCurrentSelection) {
      createConstraint.setEnabled(false);
      createSectionedConstraint.setEnabled(false);
      clearConstraint.setEnabled(true);
      moveConstraint.setEnabled(true);
      overrideConstraint.setEnabled(true);
      limitConstraint.setEnabled(true);
    }
    else {
      createConstraint.setEnabled(true);
      createSectionedConstraint.setEnabled(true);
      clearConstraint.setEnabled(false);
      moveConstraint.setEnabled(false);
      overrideConstraint.setEnabled(false);
      limitConstraint.setEnabled(false);
    }
  }

  private void makeRightMenu(JPanel right) {
    mModifyMenu = MEUI.createToolBarButton(MEIcons.EDIT_MENU, MEIcons.EDIT_MENU_DISABLED, "modify constraint set");
    right.add(mModifyMenu);
    mModifyMenu.setEnabled(false);
    myPopupMenu.add(createConstraint);
    myPopupMenu.add(clearConstraint);
    if (DEBUG) {
      myPopupMenu.add(moveConstraint);
      myPopupMenu.add(createSectionedConstraint);
      myPopupMenu.add(overrideConstraint);
    }
    mModifyMenu.addActionListener(e -> {
      myPopupMenu.show(mModifyMenu, 0, 0);
    });
  }

  @Override
  public void updateUI() {
    super.updateUI();
    if (myPopupMenu != null) { // any are not null they have been initialized
      myPopupMenu.updateUI();
      int n = myPopupMenu.getComponentCount();
      for (int i = 0; i < n; i++) {
        Component component = myPopupMenu.getComponent(i);
        if (component instanceof JComponent) {
          ((JComponent)component).updateUI();
        }
      }
    }
  }

  public void buildTable() {
    mBuildingTable = true;
    try {
      HashSet<String> found = new HashSet<>();
      mConstraintSetModel.setNumRows(0);
      mDisplayedRows.clear();
      if (mConstraintSet == null) {
        return;
      }
      else {
        String cset_id = Utils.stripID(mConstraintSet.getAttributeValue("id"));
        MTag[] sets = mConstraintSet.getChildTags("Constraint");
        String derived = mConstraintSet.getAttributeValue("deriveConstraintsFrom");

        for (int i = 0; i < sets.length; i++) {
          MTag constraint = sets[i];
          Object[] row = new Object[4];
          String id = Utils.stripID(constraint.getAttributeValue("id"));
          found.add(id);
          row[1] = id;
          ArrayList<MTag> children = constraint.getChildren();
          HashMap<String, Attribute> attrs = constraint.getAttrList();
          row[2] = cset_id;
          row[0] = MEIcons.LIST_STATE;
          mDisplayedRows.add(constraint);
          mConstraintSetModel.addRow(row);
        }

        if (showAll && mMeModel.layout != null) {
          MTag[] allViews = mMeModel.layout.getChildTags();
          for (int j = 0; j < allViews.length; j++) {
            Object[] row = new Object[4];
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

            row[1] = layoutId;
            //row[2] = "";
            row[2] = row[3] = (derived == null) ? "layout" : findFirstDefOfView(layoutId, mConstraintSet);
            row[0] = ("layout".equals(row[3])) ? null : MEIcons.LIST_GRAY_STATE;
            mDisplayedRows.add(view);
            mConstraintSetModel.addRow(row);
          }
        }
      }
      mConstraintSetModel.fireTableDataChanged();
    }
    finally {
      mBuildingTable = false;
    }
  }

  private String findFirstDefOfView(String viewId, MTag constraintSet) {
    MTag[] sets = constraintSet.getChildTags("Constraint");
    for (int i = 0; i < sets.length; i++) {
      String cid = Utils.stripID(sets[i].getAttributeValue("id"));
      if (viewId.equals(cid)) {
        return Utils.stripID(constraintSet.getAttributeValue("id"));
      }
    }
    String derive = constraintSet.getAttributeValue("deriveConstraintsFrom");
    if (derive == null) {
      return "layout";
    }
    derive = Utils.stripID(derive);
    for (MTag child : mMeModel.motionScene.getChildren()) {
      if (child.getTagName().equals("ConstraintSet")) {
        String cid = Utils.stripID(child.getAttributeValue("id"));
        if (derive.equals(cid)) {
          return findFirstDefOfView(viewId, child);
        }
      }
    }
    return "???";
  }

  private String getMask(ArrayList<MTag> children, HashMap<String, Attribute> attrs, String id) {
    if (children.size() == 0 || attrs.size() > 1 && id != null) {
      return "all";
    }
    else {
      String mask = "";
      for (MTag child : children) {
        mask += (mask.equals("") ? "" : "|") + child.getTagName();
      }
      return mask;
    }
  }

  public void setMTag(@Nullable MTag constraintSet, @NotNull MeModel meModel) {
    if (DEBUG) {
      if (constraintSet == null) {
        Debug.logStack("setMTag constraintSet = null", 4);
      }
      Debug.log("ConstraintSetPanel.setMTag constraintSet = " + constraintSet);
      Debug.log("ConstraintSetPanel.setMTag motionScene = " + meModel.motionScene);
      Debug.log("ConstraintSetPanel.setMTag layout = " + meModel.layout);
    }
    String[] selected;
    if (false) { // this approach gets the selected table
      int[] row = mConstraintSetTable.getSelectedRows();
      selected = new String[row.length];
      for (int i = 0; i < row.length; i++) {
        selected[i] = (String)mConstraintSetModel.getValueAt(row[i], 1);
      }
    }
    else {
      selected = meModel.getSelectedViewIDs();
    }
    mMeModel = meModel;

    mConstraintSet = constraintSet;
    mDerived = null;
    if (mConstraintSet != null) {
      mMeModel.setSelected(MotionEditorSelector.Type.CONSTRAINT_SET, new MTag[]{constraintSet});
      mListeners.notifyListeners(MotionEditorSelector.Type.CONSTRAINT_SET, new MTag[]{constraintSet});
      String derived = mConstraintSet.getAttributeValue("deriveConstraintsFrom");
      if (derived != null) {
        mDerived = Utils.stripID(derived);
        MTag[] constraintSets = meModel.motionScene.getChildTags("ConstraintSet");
        mParent = getDerived(constraintSets, mDerived);
      }
      mConstraintSetId = Utils.stripID(mConstraintSet.getAttributeValue("id"));
      mTitle.setText(mConstraintSetId);
    }
    else {
      if (mConstraintSetId != null) {
        mConstraintSet = mMeModel.getConstraintSet(mConstraintSetId);
      }
    }
    buildTable();

    HashSet<String> selectedSet = new HashSet<>(Arrays.asList(selected));
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

  ArrayList<MTag> getDerived(MTag[] constraintSets, String derived) {
    for (int i = 0; i < constraintSets.length; i++) {
      String id = Utils.stripID(constraintSets[i].getAttributeValue("id"));
      if (derived.equals(id)) {
        String also = constraintSets[i].getAttributeValue("deriveConstraintsFrom");
        if (also != null) {
          also = Utils.stripID(also);
          ArrayList<MTag> ret = getDerived(constraintSets, also);
          ret.add(0, constraintSets[i]);
          return ret;
        }
        else {
          ArrayList<MTag> ret = new ArrayList<>();
          ret.add(constraintSets[i]);
          return ret;
        }
      }
    }
    return new ArrayList<MTag>();
  }

  public void setListeners(MotionEditorSelector listeners) {
    mListeners = listeners;
    mListeners.addSelectionListener(new MotionEditorSelector.Listener() {
      boolean in = false;

      @Override
      public void selectionChanged(MotionEditorSelector.Type selection, MTag[] tag) {
        ArrayList<String> selectedIds = new ArrayList<>();
        if (in) { // simple block for selection triggering selection.
          return;
        }
        in = true;
        if (DEBUG) {
          Debug.log(" selectionChanged " + selection);
        }
        if (selection == MotionEditorSelector.Type.CONSTRAINT) {
          HashSet<String> selectedSet = new HashSet<>();

          for (int i = 0; i < tag.length; i++) {
            MTag mTag = tag[i];
            String id = Utils.stripID(mTag.getAttributeValue("id"));

            selectedSet.add(id);
          }
          mConstraintSetTable.clearSelection();
          for (int i = 0; i < mConstraintSetModel.getRowCount(); i++) {
            String id = (String)mConstraintSetModel.getValueAt(i, 1);
            if (selectedSet.contains(id)) {
              selectedIds.add(id);
              mConstraintSetTable.addRowSelectionInterval(i, i);
            }
          }
        }
        if (isVisible() && selection.equals(MotionEditorSelector.Type.CONSTRAINT)) {
          mMeModel.setSelectedViewIDs(selectedIds);
        }
        in = false;
      }
    });
  }

  public void selectById(String[] ids) {
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
