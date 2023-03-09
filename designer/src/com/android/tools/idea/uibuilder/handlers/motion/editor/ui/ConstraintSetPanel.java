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
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.StringMTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.Track;
import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.Debug;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.Vector;
import java.util.stream.Collectors;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

/**
 * This displays the constraint panel
 */
class ConstraintSetPanel extends JPanel {

  private MTag mSelectedTag; // the Primary selection
  private MTag[] mMultiSelectedTag; // the list if you are supporting multi-select
  MotionEditorSelector mListeners;
  private static boolean DEBUG = false;
  ArrayList<MTag> mParent; // mParent.get(0) is the direct parent
  MTag mConstraintSet; // The currently displayed constraintSet
  ArrayList<MTag> mDisplayedRows = new ArrayList<>();
  JPopupMenu myPopupMenu = new JPopupMenu();

  DefaultTableModel mConstraintSetModel = new DefaultTableModel(
    new String[]{"Constraint", "ID", "Source"}, 0) {

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
      Track.createConstraint(mMeModel.myTrack);
      ConstraintSetPanelCommands.createConstraint(mSelectedTag, mConstraintSet);
      buildTable();
    }
  };

  AbstractAction createAllConstraints = new AbstractAction("Create All Constraints") {
    @Override
    public void actionPerformed(ActionEvent e) {
      Track.createConstraint(mMeModel.myTrack);
      ConstraintSetPanelCommands.createAllConstraints(mDisplayedRows, mConstraintSet);
      buildTable();
    }
  };

  AbstractAction createSectionedConstraint = new AbstractAction("Create Sectioned Constraint") {
    @Override
    public void actionPerformed(ActionEvent e) {
      ConstraintSetPanelCommands.createSectionedConstraint(mMultiSelectedTag, mConstraintSet);
      buildTable();
    }
  };

  AbstractAction clearConstraint = new AbstractAction("Clear Constraint") {
    @Override
    public void actionPerformed(ActionEvent e) {
      Track.clearConstraint(mMeModel.myTrack);
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

  private String mConstraintSetId;

  ConstraintSetPanel() {
    super(new BorderLayout());
    JPanel left = new JPanel(new GridBagLayout());
    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JPanel top = new JPanel(new BorderLayout());
    top.add(left, BorderLayout.WEST);
    top.add(right, BorderLayout.EAST);
    top.setBorder(MEUI.getPanelBottomBorder());
    mConstraintSetTable.setShowHorizontalLines(false);
    mConstraintSetTable.setAlignmentY(0.0f);
    mConstraintSetTable.getColumnModel().getColumn(0).setPreferredWidth(MEUI.scale(32));
    mConstraintSetTable.setDefaultRenderer(String.class, new DefaultTableCellRenderer() {
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
    mConstraintSetTable.setDefaultRenderer(Icon.class, new TableCellRenderer() {
      JLabel myLabel = new JLabel();

      @Override
      public Component getTableCellRendererComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     boolean hasFocus,
                                                     int row,
                                                     int column) {
        myLabel.setIcon((Icon)value);
        myLabel.setHorizontalAlignment(SwingConstants.CENTER);
        myLabel.setSize(new Dimension(MEUI.scale(18), MEUI.scale(12)));
        if (isSelected) {
          if (value == MEIcons.LIST_STATE_DERIVED) {
            myLabel.setIcon(MEIcons.LIST_STATE_DERIVED_SELECTED);
          }
          else if (value == MEIcons.LIST_STATE) {
            myLabel.setIcon(MEIcons.LIST_STATE_SELECTED);
          }
          myLabel.setBackground(table.hasFocus() ? MEUI.CSPanel.our_SelectedFocusBackground : MEUI.CSPanel.our_SelectedBackground);
          myLabel.setOpaque(true);
        }
        else {
          myLabel.setOpaque(false);
          myLabel.setBackground(MEUI.ourPrimaryPanelBackground);
        }
        return myLabel;
      }
    });

    GridBagConstraints gbc = new GridBagConstraints();
    JLabel leftLabel = new JLabel("ConstraintSet (", MEIcons.CONSTRAINT_SET, SwingConstants.CENTER);
    mTitle = new JLabel("", SwingConstants.CENTER);
    JLabel rightLabel = new JLabel(")", SwingConstants.CENTER);

    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridy = 0;
    gbc.gridx = 0;
    gbc.weightx = 0;
    gbc.ipadx = 16;
    left.add(leftLabel, gbc);
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridy = 0;
    gbc.gridx += 1;
    gbc.weightx = 0.5;
    gbc.ipadx = 0;
    left.add(mTitle, gbc);
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridy = 0;
    gbc.gridx = 2;
    gbc.weightx = 1;
    gbc.ipadx = 16;
    left.add(rightLabel,gbc);

    makeRightMenu(right);

    ActionListener copyListener = e -> copy();
    ActionListener pasteListener = e -> {
      paste();
    };

    MEUI.addCopyPaste(copyListener, pasteListener, mConstraintSetTable);

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
          return;
        }
        mMultiSelectedTag = new MTag[allSelect.length];
        for (int i = 0; i < allSelect.length; i++) {
          int k = allSelect[i];
          mMultiSelectedTag[i] = mDisplayedRows.get(k);
        }
        MTag[] tag = mDisplayedRows.isEmpty() ? new MTag[0] : new MTag[]{mSelectedTag = mDisplayedRows.get(index)};
        mListeners.notifyListeners(MotionEditorSelector.Type.CONSTRAINT, tag, 0);
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
    }
    else {
      createConstraint.setEnabled(true);
      createSectionedConstraint.setEnabled(true);
      clearConstraint.setEnabled(false);
      moveConstraint.setEnabled(false);
      overrideConstraint.setEnabled(false);
    }
    if (tags.length == mDisplayedRows.size()) {
      createAllConstraints.setEnabled(false);
    }
    else {
      createAllConstraints.setEnabled(true);
    }
  }

  private void copy() {
    MEUI.copy(mSelectedTag);
  }

  private void paste() {
    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

    try {
      String buff = (String)(clipboard.getContents(this).getTransferData(DataFlavor.stringFlavor));
      StringMTag pastedTag = StringMTag.parse(buff);
      HashMap<String, Attribute> attr = pastedTag.getAttrList();

      if (mSelectedTag != null) {
        String tagName = mSelectedTag.getTagName();

        if ("Constraint".equals(tagName)) { // overwriting a constraint
          HashMap<String, Attribute> toDel = new HashMap<>(mSelectedTag.getAttrList());

          toDel.remove(MotionSceneAttrs.ATTR_ANDROID_ID);

          MTag.TagWriter writer = mSelectedTag.getTagWriter();
          if (writer == null) {
            return;
          }

          for (String s : toDel.keySet()) {
            Attribute a = toDel.get(s);
            writer.setAttribute(a.mNamespace, a.mAttribute, null);
          }
          for (String s : attr.keySet()) {
            Attribute a = attr.get(s);
            if (a == null || a.mAttribute.equals("id")) {
              continue;
            }

            writer.setAttribute(a.mNamespace, a.mAttribute, a.mValue);
          }
          MTag[] children = pastedTag.getChildTags();
          for (int i = 0; i < children.length; i++) {
            MTag child = children[i];
            MTag.TagWriter cw = writer.getChildTagWriter(child.getTagName());
            HashMap<String, Attribute> cwAttrMap = pastedTag.getAttrList();
            for (String cwAttrStr : cwAttrMap.keySet()) {
              Attribute cwAttr = cwAttrMap.get(cwAttrStr);
              cw.setAttribute(cwAttr.mNamespace, cwAttr.mAttribute, cwAttr.mValue);
            }
          }
          writer.commit("paste");
        }
        else if (!"Guideline".equals(tagName)) { // overwriting a
          String id = mSelectedTag.getAttributeValue(MotionSceneAttrs.ATTR_ANDROID_ID);
          MTag.TagWriter writer = mConstraintSet.getChildTagWriter("Constraint");
          for (String s : attr.keySet()) {
            Attribute a = attr.get(s);
            if (a == null || a.mAttribute.equals(MotionSceneAttrs.ATTR_ANDROID_ID)) {
              writer.setAttribute(a.mNamespace, a.mAttribute, "@+id/" + Utils.stripID(id));
            }
            else {

              writer.setAttribute(a.mNamespace, a.mAttribute, a.mValue);
            }
          }
          MTag[] children = pastedTag.getChildTags();
          for (int i = 0; i < children.length; i++) {
            MTag child = children[i];
            MTag.TagWriter cw = writer.getChildTagWriter(child.getTagName());
            HashMap<String, Attribute> cwAttrMap = pastedTag.getAttrList();
            for (String cwAttrStr : cwAttrMap.keySet()) {
              Attribute cwAttr = cwAttrMap.get(cwAttrStr);
              cw.setAttribute(cwAttr.mNamespace, cwAttr.mAttribute, cwAttr.mValue);
            }
          }
          writer.commit("paste");
        }
      }
    }
    catch (UnsupportedFlavorException e) {
      e.printStackTrace();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void makeRightMenu(JPanel right) {
    mModifyMenu = MEUI.createToolBarButton(MEIcons.EDIT_MENU, MEIcons.EDIT_MENU_DISABLED, "modify constraint set");
    right.add(mModifyMenu);
    mModifyMenu.setEnabled(false);
    myPopupMenu.add(createConstraint);
    myPopupMenu.add(createAllConstraints);
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
        if (DEBUG) {
          for (int i = 0; i < sets.length; i++) {
            MTag set = sets[i];
            Debug.log(i+" "+set.getTagName() + " "+ set.getTreeId());
          }

        }
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
            row[2] = row[3] = (derived == null) ? "layout" : findFirstDefOfView(layoutId, mConstraintSet);
            row[0] = ("layout".equals(row[3])) ? null : MEIcons.LIST_STATE_DERIVED;
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

  private void updateModelIfNecessary() {
    Set<String> ids = Arrays.stream(mConstraintSet.getChildTags(MotionSceneAttrs.Tags.CONSTRAINT))
      .map(view -> view.getAttributeValue(MotionSceneAttrs.ATTR_ANDROID_ID))
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
    if (showAll && mMeModel.layout != null) {
      Arrays.stream(mMeModel.layout.getChildTags())
        .map(view -> Utils.stripID(view.getAttributeValue(MotionSceneAttrs.ATTR_ANDROID_ID)))
        .filter(Objects::nonNull)
        .forEach(ids::add);
    }

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

  public void setMTag(@Nullable MTag constraintSet, @NotNull MeModel meModel) {
    if (DEBUG) {
      if (constraintSet == null) {
        Debug.logStack("setMTag constraintSet = null", 4);
      }
      Debug.log("ConstraintSetPanel.setMTag constraintSet = " + constraintSet);
      Debug.log("ConstraintSetPanel.setMTag motionScene = " + meModel.motionScene);
      Debug.log("ConstraintSetPanel.setMTag layout = " + meModel.layout);
    }
    String[] selected = mMeModel != null ? mMeModel.getSelectedViewIDs() : new String[0];
    mMeModel = meModel;

    mConstraintSet = constraintSet;
    mDerived = null;
    if (mConstraintSet != null) {
      mMeModel.setSelected(MotionEditorSelector.Type.CONSTRAINT_SET, new MTag[]{constraintSet});
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

    if (constraintSet != null) {
      HashSet<String> selectedSet = new HashSet<>(Arrays.asList(selected));
      for (int i = 0; i < mConstraintSetModel.getRowCount(); i++) {
        String id = (String)mConstraintSetModel.getValueAt(i, 1);
        if (selectedSet.contains(id)) {
          mConstraintSetTable.addRowSelectionInterval(i, i);
        }
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
      public void selectionChanged(MotionEditorSelector.Type selection, MTag[] tag, int flags) {
        ArrayList<String> selectedIds = new ArrayList<>();
        if (in) { // simple block for selection triggering selection.
          return;
        }
        in = true;
        mBuildingTable = true;
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
        mBuildingTable = false;
      }
    });
  }

  public void selectById(String[] ids) {
    if (mConstraintSet == null) {
      if (mConstraintSetModel != null) {
        int count = mConstraintSetModel.getRowCount();
        for (int i = 0; i < count; i++) {
          mConstraintSetModel.removeRow(0);
        }
      }
      return;
    }
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
}
