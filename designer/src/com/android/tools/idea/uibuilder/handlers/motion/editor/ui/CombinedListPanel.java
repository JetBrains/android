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
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEUI;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Vector;

/**
 * This displays the combined list fo top level tags you can select
 * It shows ConstrainsSet's Transitions and the source layout.
 */
public class CombinedListPanel extends JPanel {
  MTag mMotionScene;
  MTag mMotionLayout;
  ListSelectionListener mListSelectionListener;
  private boolean mSplitView = false;

  static class Row {
    public int mCount = 0;

    enum Type {
      LAYOUT,
      CONSTRAINT_SET,
      TRANSITION,
      OTHER
    }

    Type mType;
    MTag mTag;
    String myString;

    Row(MTag tag) {
      mTag = tag;
      if (mTag == null) {
        mType = Type.LAYOUT;
        myString = "MotionLayout";
        return;
      }
      String name = mTag.getTagName();
      switch (name) {
        case "ConstraintSet":
          mType = Type.CONSTRAINT_SET;
          myString = buildListCSString(mTag);
          break;
        case "Transition":
          mType = Type.TRANSITION;
          myString = buildListTransString(mTag);
          break;
        default:
          mType = Type.OTHER;
      }
    }

    String buildListCSString(MTag tag) {
      String cid = Utils.stripID(tag.getAttributeValue("id"));
      int noc = tag.getChildTags().length;
      return "" + cid + "(" + noc + ")";
    }

    String buildListTransString(MTag tag) {
      String tid = tag.getAttributeValue("id");
      tid = (tid == null) ? "T" : Utils.stripID(tid);
      String start = Utils.stripID(tag.getAttributeValue("constraintSetStart"));
      String end = Utils.stripID(tag.getAttributeValue("constraintSetEnd"));
      return tid + "(" + start + " -> " + end + ")";
    }
  }

  boolean building = false;
  JList<Row> mMainList = new JList<>();
  JScrollPane mTListPane = new JScrollPane(mMainList);
  JList<Row> mConstraintSetList = new JList<>();
  JScrollPane mConstraintSetPane = new JScrollPane(mConstraintSetList);
  JList<Row> mTransitionList = new JList<>();
  JScrollPane mTransitionPane = new JScrollPane(mTransitionList);

  ListCellRenderer<Row> rowRenderer = new ListCellRenderer<Row>() {
    JLabel label = new JLabel();
    JLabel title = new JLabel();
    JPanel panel = new JPanel(new BorderLayout());
    Border b1 = BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY);
    Border mChangeBorder = BorderFactory.createCompoundBorder(b1, BorderFactory.createEmptyBorder(1, 0, 0, 0));
    Border mNoBorder = BorderFactory.createEmptyBorder(2, 0, 0, 0);
    Color mUnselectedColor = MEUI.ourSecondaryPanelBackground;
    Color mSelectedColor = MEUI.ourMySelectedLineColor;

    {
      title.setOpaque(true);
      panel.add(label);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends Row> list, Row value, int index, boolean isSelected, boolean cellHasFocus) {
      label.setText(value.myString);
      String titleString = "";
      switch (value.mType) {
        case LAYOUT:
          label.setIcon(MEIcons.LIST_LAYOUT);
          titleString = "layout";
          break;
        case CONSTRAINT_SET:
          label.setIcon(MEIcons.LIST_STATE);
          titleString = "Constraint Set";
          break;
        case TRANSITION:
          label.setIcon(MEIcons.LIST_TRANSITION);
          titleString = "Transitions";
          break;
        case OTHER:
          label.setIcon(null);
          break;
      }

      if (mSplitView) {
        panel.remove(title);
        if (value.mCount == 0 && index != 0) {
          panel.setBorder(mChangeBorder);
        } else {
          panel.setBorder(mNoBorder);
        }
      } else {
        panel.setBorder(null);
        if (value.mCount == 0 && index != 0) {
          title.setText(titleString);
          panel.add(title, BorderLayout.NORTH);
        } else {
          panel.remove(title);
        }
      }
      label.setBackground(isSelected ? mSelectedColor : mUnselectedColor);
      panel.setBackground(isSelected ? mSelectedColor : mUnselectedColor);
      return panel;
    }
  };

  CombinedListPanel() {
    super(new BorderLayout());
    setBackground(MEUI.ourSecondaryPanelBackground);

    mTransitionPane.setColumnHeaderView(new JLabel("Transitions"));
    mTransitionPane.setBorder(BorderFactory.createEmptyBorder());

    mConstraintSetPane.setColumnHeaderView(new JLabel("Constraint Sets"));
    mMainList.setBackground(MEUI.ourSecondaryPanelBackground);
    mTransitionList.setBackground(MEUI.ourSecondaryPanelBackground);
    mConstraintSetList.setBackground(MEUI.ourSecondaryPanelBackground);

    mMainList.setCellRenderer(rowRenderer);
    mTransitionList.setCellRenderer(rowRenderer);
    mConstraintSetList.setCellRenderer(rowRenderer);

    add(mTListPane, BorderLayout.CENTER);

    ListSelectionListener listSelectionListener = e -> {
      if (e.getValueIsAdjusting() || building) {
        return;
      }
      if (e.getSource() == mConstraintSetList && mConstraintSetList.getSelectedIndex() != -1) {
        mTransitionList.clearSelection();
      } else if (e.getSource() == mTransitionList && mTransitionList.getSelectedIndex() != -1) {
        mConstraintSetList.clearSelection();
      }
      select(e);
    };
    mMainList.addListSelectionListener(listSelectionListener);
    mTransitionList.addListSelectionListener(listSelectionListener);
    mConstraintSetList.addListSelectionListener(listSelectionListener);

  }

  public void setSplitView(boolean splitView) {
    mSplitView = splitView;
    if (splitView) {
      setLayout(new GridLayout(1, 2));
      remove(mTListPane);
      add(mConstraintSetPane);
      add(mTransitionPane);
    } else {
      setLayout(new BorderLayout());
      remove(mConstraintSetPane);
      remove(mTransitionPane);
      add(mTListPane);
    }
    validate();
  }

  void select(ListSelectionEvent e) {
    fireSelection(e);
  }

  public void setMTag(MTag motionScene, MTag layout) {
    building = true;
    mMotionScene = motionScene;
    mMotionLayout = layout;
    int selected = mMainList.getSelectedIndex();
    int csSelect = mConstraintSetList.getSelectedIndex();
    int transitionSelect = mTransitionList.getSelectedIndex();

    selected = Math.max(0, selected);
    ArrayList<MTag> list = motionScene.getChildren();
    Vector<Row> csRows = new Vector<>();
    Vector<Row> tRows = new Vector<>();
    int csCount = 0;
    int tCount = 0;
    csRows.add(new Row(null));

    for (MTag tag : list) {

      Row row = new Row(tag);
      if (row.mType == Row.Type.CONSTRAINT_SET) {
        row.mCount = csCount;
        csRows.insertElementAt(row, csCount + 1);
        csCount++;
      } else if (row.mType == Row.Type.TRANSITION) {
        row.mCount = tCount;
        tRows.insertElementAt(row, tCount);
        tCount++;
      }
    }

    int count = (mMotionLayout == null) ? -1 : mMotionLayout.getChildTags().length;
    mConstraintSetList.setListData(csRows);
    mTransitionList.setListData(tRows);
    Vector<Row> rows = new Vector<>(csRows);
    rows.addAll(tRows);
    mMainList.setListData(rows);
    mMainList.setSelectedIndex(selected);
    mTransitionList.setSelectedIndex(csSelect);
    mConstraintSetList.setSelectedIndex(transitionSelect);
    building = false;
  }

  public void clearSelection() {
    building = true;
    mMainList.clearSelection();
    building = false;
  }

  public int getSelected() {
    return mMainList.getSelectedIndex();
  }

  public int getSelectedConstraintSet() {

    Row row;
    if (mSplitView) {
      row = mConstraintSetList.getSelectedValue();
    } else {
      row = mMainList.getSelectedValue();
    }
    if (row == null) {
      return -1;
    }
    if (row.mType == Row.Type.CONSTRAINT_SET) {
      return 1 + row.mCount;
    }
    if (row.mType == Row.Type.LAYOUT) {
      return 0;
    }
    return -1;
  }

  public int getSelectedTransition() {
    Row row;
    if (mSplitView) {
      row = mTransitionList.getSelectedValue();
      if (row == null) {
        row = mConstraintSetList.getSelectedValue();
      }
    } else {
      row = mMainList.getSelectedValue();
    }
    if (row == null) {
      return -1;
    }
    if (row.mType == Row.Type.TRANSITION) {
      return row.mCount;
    }
    return -1;
  }

  public void selectTransition() {
    ListModel<Row> model;
    if (mSplitView) {
      model = mTransitionList.getModel();
    } else {
      model = mMainList.getModel();
    }
    int size = model.getSize();
    for (int i = 0; i < size; i++) {
      Row row = model.getElementAt(i);
      if (row.mType == Row.Type.TRANSITION) {
        mMainList.setSelectedIndex(i);
        return;
      }
    }
  }

  void setSelectionListener(ListSelectionListener l) {
    mListSelectionListener = l;
  }

  void fireSelection(ListSelectionEvent e) {
    if (mListSelectionListener == null) {
      System.err.println(" NO SELECTION LISTENER!");
      return;
    }
    mListSelectionListener.valueChanged(e);
  }

  public void selectTag(MTag tag) {
    if (mSplitView) {
      String name = tag.getTagName();
      if (name.endsWith("MotionLayout")) {
        mConstraintSetList.setSelectedIndex(0);
        return;
      }
      if (name.endsWith("ConstraintSet")) {
        ListModel<Row> model = mConstraintSetList.getModel();
        int size = model.getSize();
        for (int i = 0; i < size; i++) {
          Row row = model.getElementAt(i);
          if (tag == row.mTag) { // TODO this should be a deep compare
            mConstraintSetList.setSelectedIndex(i);
            return;
          }
        }
      } else {
        ListModel<Row> model = mTransitionList.getModel();
        int size = model.getSize();
        for (int i = 0; i < size; i++) {
          Row row = model.getElementAt(i);
          if (tag == row.mTag) { // TODO this should be a deep compare
            mTransitionList.setSelectedIndex(i);
            return;
          }
        }
      }
    } else {
      ListModel<Row> model = mMainList.getModel();
      int size = model.getSize();
      if (tag.getTagName().endsWith("MotionLayout")) {
        mMainList.setSelectedIndex(0);
        return;
      }
      for (int i = 0; i < size; i++) {
        Row row = model.getElementAt(i);
        if (tag == row.mTag) { // TODO this should be a deep compare
          mMainList.setSelectedIndex(i);
          return;
        }

      }
    }

  }
}
