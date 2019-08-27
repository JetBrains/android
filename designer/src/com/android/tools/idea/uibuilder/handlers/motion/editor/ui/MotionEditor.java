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
import com.android.tools.idea.uibuilder.handlers.motion.editor.createDialogs.CreateConstraintSet;
import com.android.tools.idea.uibuilder.handlers.motion.editor.createDialogs.CreateOnClick;
import com.android.tools.idea.uibuilder.handlers.motion.editor.createDialogs.CreateOnSwipe;
import com.android.tools.idea.uibuilder.handlers.motion.editor.createDialogs.CreateTransition;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditorSelector.TimeLineListener;
import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.Debug;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;

/**
 * The main MotionEditor Panel
 */
public class MotionEditor extends JPanel {
  public final static boolean DEBUG = false;
  MeModel mMeModel;
  MotionEditorSelector mMotionEditorSelector = new MotionEditorSelector();
  JTabbedPane mTabbedTopPane = new JTabbedPane();
  MotionScenePanel mMotionSceneTabb = new MotionScenePanel();
  private TransitionPanel mTransitionPanel = new TransitionPanel(this);
  ConstraintSetPanel mConstraintSetPanel = new ConstraintSetPanel();
  LayoutPanel mLayoutPanel = new LayoutPanel();
  CombinedListPanel mCombinedListPanel = new CombinedListPanel();
  OverviewPanel mOverviewPanel = new OverviewPanel();
  JScrollPane mOverviewScrollPane = new JScrollPane(mOverviewPanel);
  CardLayout mCardLayout = new CardLayout();
  JPanel mCenterPanel = new JPanel(mCardLayout);
  private static String LAYOUT_PANEL = "Layout";
  private static String TRANSITION_PANEL = "Transition";
  private static String CONSTRAINTSET_PANEL = "ConstraintSet";
  CreateConstraintSet mCreateConstraintSet = new CreateConstraintSet();
  CreateOnClick mCreateOnClick = new CreateOnClick();
  CreateOnSwipe mCreateOnSwipe = new CreateOnSwipe();
  CreateTransition mCreateTransition = new CreateTransition();
  JSplitPane mTopPanel;

  enum LayoutMode {
    VERTICAL_LAYOUT,
    HORIZONTAL_LAYOUT,
    OVERVIEW_ONLY_LAYOUT
  }

  LayoutMode mLayoutMode = null;

  private MTag mSelectedTag;

  public void dataChanged() {
    setMTag(mMeModel);
  }

  public MotionEditor() {
    super(new BorderLayout());
    mOverviewScrollPane.setBorder(BorderFactory.createEmptyBorder());

    JPanel ui = new JPanel(new GridLayout(2, 1));

    mCombinedListPanel.setSelectionListener(e -> {
      listSelection();
    });
    mMotionEditorSelector.addSelectionListener(new MotionEditorSelector.Listener() {
      @Override
      public void selectionChanged(MotionEditorSelector.Type selection, MTag[] tag) {
        if (DEBUG) {
          Debug.log(" selectionChanged  " + selection);
        }
        mMeModel.setSelected(selection, tag);
      }
    });
    ui.setBackground(MEUI.ourPrimaryPanelBackground);
    mCombinedListPanel.setPreferredSize(new Dimension(10, 100));
    mTopPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mCombinedListPanel, mOverviewScrollPane);
    //  layoutTop(LayoutMode.HORIZONTAL_LAYOUT);

    ui.add(mTopPanel);
    ui.add(mCenterPanel, BorderLayout.CENTER);
    mCenterPanel.add(mTransitionPanel, TRANSITION_PANEL);
    mCenterPanel.add(mConstraintSetPanel, CONSTRAINTSET_PANEL);
    mCenterPanel.add(mLayoutPanel, LAYOUT_PANEL);
    mTransitionPanel.setListeners(mMotionEditorSelector);
    mConstraintSetPanel.setListeners(mMotionEditorSelector);
    mLayoutPanel.setListeners(mMotionEditorSelector);

    mCenterPanel.setBackground(MEUI.ourPrimaryPanelBackground);
    mLayoutPanel.setBackground(MEUI.ourPrimaryPanelBackground);
    mConstraintSetPanel.setBackground(MEUI.ourPrimaryPanelBackground);
    mCombinedListPanel.setBackground(MEUI.ourPrimaryPanelBackground);
    mOverviewPanel.setBorder(BorderFactory.createEmptyBorder());

    mOverviewPanel.setSelectionListener(e -> {
      selectTag(e);
    });

    add(ui);
    JPanel toolbarLeft = new JPanel(new FlowLayout(FlowLayout.LEFT));
    JPanel toolbarRight = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JPanel toolbar = new JPanel(new BorderLayout());
    toolbar.add(toolbarLeft, BorderLayout.WEST);
    toolbar.add(toolbarRight, BorderLayout.EAST);

    JButton create_constraintSet = MEUI.createToolBarButton(MEIcons.CREATE_MENU, "Create MotionScene Objects");
    toolbarLeft.add(create_constraintSet);
    JButton create_transition = MEUI.createToolBarButton(MEIcons.CREATE_TRANSITION, "Create MotionScene Objects");
    toolbarLeft.add(create_transition);
    JButton create_touch = MEUI.createToolBarButton(MEIcons.CREATE_ON_STAR, "Create MotionScene Objects");
    toolbarLeft.add(create_touch);
    create_constraintSet.setAction(mCreateConstraintSet.getAction(create_constraintSet, this));
    create_transition.setAction(mCreateTransition.getAction(create_transition, this));
    create_constraintSet.setHideActionText(true);
    create_transition.setHideActionText(true);
    create_touch.setHideActionText(true);

    JPopupMenu popupMenu = new JPopupMenu();

    popupMenu.add(mCreateOnClick.getAction(create_touch, this));
    popupMenu.add(mCreateOnSwipe.getAction(create_touch, this));

    create_touch.addActionListener(e -> {
      popupMenu.show(create_constraintSet, 0, 0);
    });

    JButton cycle = MEUI.createToolBarButton(MEIcons.CYCLE_LAYOUT, "Vertical view");

    toolbarRight.add(cycle);

    cycle.addActionListener(e -> {
      layoutTop();
    });

    add(toolbar, BorderLayout.NORTH);

    layoutTop();

  }

  public void addSelectionListener(MotionEditorSelector.Listener listener) {
    mMotionEditorSelector.addSelectionListener(listener);
  }

  private void notifyListeners(MotionEditorSelector.Type type, MTag[] tags) {
    mMotionEditorSelector.notifyListeners(type, tags);
  }

  public MeModel getMeModel() {
    return mMeModel;
  }

  public MTag getSelectedTag() {
    return mSelectedTag;
  }

  private void selectTag(MTag tag) {
    mSelectedTag = tag;
    mCombinedListPanel.selectTag(tag);
  }

  public void setMTag(MTag motionScene, MTag layout, String layoutFileName,
                      String motionSceneFileName) {
    setMTag(new MeModel(motionScene, layout, layoutFileName, motionSceneFileName));
  }

  public void setMTag(MeModel model) {
    mMeModel = model;
    mMotionSceneTabb.setMTag(mMeModel.motionScene);
    mCombinedListPanel.setMTag(mMeModel.motionScene, mMeModel.layout);
    mOverviewPanel.setMTag(mMeModel.motionScene, mMeModel.layout);
    if (mMeModel.getSelectedType() != null) {
      switch (mMeModel.getSelectedType()) {
        case TRANSITION:
          mTransitionPanel.setMTag(mMeModel.getSelected()[0], mMeModel);
          break;
        case KEY_FRAME:
        case KEY_FRAME_GROUP:
          mTransitionPanel.setMTag(mMeModel.getSelected()[0].getParent().getParent(), mMeModel);

      }
    }
  }

  private void layoutTop() {
    if (mLayoutMode == null) {
      mLayoutMode = LayoutMode.OVERVIEW_ONLY_LAYOUT;
    } else {
      mLayoutMode = LayoutMode.values()[(mLayoutMode.ordinal() + 1) % LayoutMode.values().length];
    }
    switch (mLayoutMode) {
      case VERTICAL_LAYOUT:
        mCombinedListPanel.setSplitView(true);
        mTopPanel.setOrientation(JSplitPane.VERTICAL_SPLIT);
        mTopPanel.remove(mCombinedListPanel);
        mTopPanel.remove(mOverviewScrollPane);
        mTopPanel.setBottomComponent(mCombinedListPanel);
        mTopPanel.setTopComponent(mOverviewScrollPane);
        mTopPanel.setResizeWeight(0.666);
        mTopPanel.setDividerLocation(0.666);
        mTopPanel.setEnabled(false);
        mTopPanel.setDividerSize(0);
        break;
      case HORIZONTAL_LAYOUT:
        mCombinedListPanel.setSplitView(false);
        mTopPanel.setOrientation(JSplitPane.HORIZONTAL_SPLIT);
        mTopPanel.remove(mCombinedListPanel);
        mTopPanel.remove(mOverviewScrollPane);
        mTopPanel.setRightComponent(mOverviewScrollPane);
        mTopPanel.setLeftComponent(mCombinedListPanel);
        mTopPanel.setResizeWeight(0.333);
        mTopPanel.setDividerLocation(0.333);
        mTopPanel.setEnabled(false);
        mTopPanel.setDividerSize(0);
        break;
      case OVERVIEW_ONLY_LAYOUT:
        mCombinedListPanel.setSplitView(false);
        mTopPanel.setOrientation(JSplitPane.HORIZONTAL_SPLIT);
        mTopPanel.remove(mCombinedListPanel);
        mTopPanel.remove(mOverviewScrollPane);
        mTopPanel.setRightComponent(mOverviewScrollPane);
        mTopPanel.setResizeWeight(0.0);
        mTopPanel.setDividerLocation(0.0);
        mTopPanel.setEnabled(false);
        mTopPanel.setDividerSize(0);
        break;
    }
    mTopPanel.validate();
  }

  void listSelection() {
    int index = mCombinedListPanel.getSelectedConstraintSet();
    if (index >= 0) {
      constraintSetSelection();
    } else {
      transitionSelection();
    }

  }

  public void setSelection(MotionEditorSelector.Type type , MTag[] tag) {
    mSelectedTag = tag[0];
    notifyListeners(type,tag);
  }

  void constraintSetSelection() {

    int index = mCombinedListPanel.getSelectedConstraintSet();
    mOverviewPanel.setConstraintSetIndex(index);

    if (index >= 0) {
      MTag[] c_sets = mCombinedListPanel.mMotionScene.getChildTags("ConstraintSet");
      if (0 < index) {
        mCardLayout.show(mCenterPanel, CONSTRAINTSET_PANEL);
        MTag selectedConstraintSet = c_sets[index - 1];
        mConstraintSetPanel.setMTag(selectedConstraintSet, mMeModel);
        notifyListeners(MotionEditorSelector.Type.CONSTRAINT_SET,
          new MTag[]{selectedConstraintSet});
        notifyListeners(MotionEditorSelector.Type.CONSTRAINT_SET,
          new MTag[]{selectedConstraintSet});
        mSelectedTag = selectedConstraintSet;
      } else {
        mCardLayout.show(mCenterPanel, LAYOUT_PANEL);
        mLayoutPanel.setMTag(mCombinedListPanel.mMotionLayout, mMeModel);
        notifyListeners(MotionEditorSelector.Type.LAYOUT,
          (mCombinedListPanel.mMotionLayout == null) ? new MTag[0] :
            new MTag[]{mCombinedListPanel.mMotionLayout});
        mSelectedTag = mCombinedListPanel.mMotionLayout;
      }
    }
  }

  void transitionSelection() {
    int index = mCombinedListPanel.getSelectedTransition();
    mOverviewPanel.setTransitionSetIndex(index);
    mCardLayout.show(mCenterPanel, TRANSITION_PANEL);
    MTag[] transitions = mCombinedListPanel.mMotionScene.getChildTags("Transition");
    if (transitions.length == 0) {
      constraintSetSelection();
      return;
    }
    MTag selectedTransition = transitions[index];
    mTransitionPanel.setMTag(selectedTransition, mMeModel);
    notifyListeners(MotionEditorSelector.Type.TRANSITION, new MTag[]{selectedTransition});
    mSelectedTag = selectedTransition;
  }

  public void selectTransition() {
    mCombinedListPanel.selectTransition();
    transitionSelection();
  }

  public void addTimeLineListener(TimeLineListener timeLineListener) {
    mTransitionPanel.addTimeLineListener(timeLineListener);
  }

  //////////////////////////////////////////////////////////////////////////////////////////////
  class MotionScenePanel extends JPanel {

    Icon icon = UIManager.getIcon("Tree.closedIcon");
    JTextField mDefaultDurationText = new JTextField();
    JButton mTransitionHeader = new JButton("Transitions (1)", icon);
    DefaultTableModel mTransitionModel = new DefaultTableModel(
      new String[]{"id", "start", "end", "duration"}, 0);
    JTable mTransitionTable = new JTable(mTransitionModel);
    JScrollPane mSPTransition = new JScrollPane(mTransitionTable);

    JButton mConstraintSetHeader = new JButton("ConstraintSets (2)", icon);
    DefaultTableModel mConstraintSetModel = new DefaultTableModel(
      new String[]{"id", "derived", "Constraints"}, 0);

    JTable mConstraintSetTable = new JTable(mConstraintSetModel);
    JScrollPane mSPConstraintSet = new JScrollPane(mConstraintSetTable);

    private void stripButton(JButton button) {
      button.setBackground(getBackground());
      button.setBorderPainted(false);
      button.setContentAreaFilled(false);
      button.setFocusPainted(false);
      button.setOpaque(false);
      button.setAlignmentX(RIGHT_ALIGNMENT);
    }

    public void setMTag(MTag motionScene) {
      String value = motionScene.getAttributeValue("defaultDuration");
      mDefaultDurationText.setText((value == null) ? "" : value);
      MTag[] transitions = motionScene.getChildTags("Transition");
      mTransitionHeader.setText("Transitions (" + transitions.length + ")");
      String[] table_data = new String[4];
      mTransitionModel.setNumRows(0);
      for (int i = 0; i < transitions.length; i++) {
        MTag t = transitions[i];
        String id = t.getAttributeValue("id");
        String start = t.getAttributeValue("constraintSetStart");
        String end = t.getAttributeValue("constraintSetEnd");
        String duration = t.getAttributeValue("duration");
        table_data[0] = id;
        table_data[1] = start;
        table_data[2] = end;
        table_data[3] = duration;
        mTransitionModel.addRow(table_data);
      }
      mTransitionModel.fireTableDataChanged();
      MTag[] sets = motionScene.getChildTags("ConstraintSet");
      mConstraintSetModel.setNumRows(0);
      for (int i = 0; i < sets.length; i++) {
        MTag t = sets[i];
        String id = t.getAttributeValue("id");
        String derive = t.getAttributeValue("deriveConstraintsFrom");
        if (derive == null) {
          derive = "(base)";
        }
        MTag[] children = t.getChildTags();
        table_data[0] = id;
        table_data[1] = derive;
        table_data[2] = "" + children.length;
        mConstraintSetModel.addRow(table_data);
      }
      mConstraintSetModel.fireTableDataChanged();
    }

    MotionScenePanel() {
      super(new GridBagLayout());
      stripButton(mTransitionHeader);
      stripButton(mConstraintSetHeader);
      Insets table_inset = new Insets(0, 20, 0, 20);
      Insets default_inset;
      GridBagConstraints gbc = new GridBagConstraints();

      default_inset = gbc.insets;
      gbc.weighty = 0;
      gbc.gridx = 0;
      gbc.gridy = 0;
      gbc.gridheight = 1;
      gbc.gridwidth = 1;
      JLabel label;
      label = new JLabel("Default Duration");
      //label.setFont(label.getFont().deriveFont(Font.BOLD));
      add(label, gbc);
      gbc.gridx = 1;
      gbc.weightx = 1;
      gbc.anchor = GridBagConstraints.WEST;
      mDefaultDurationText.setText("XXXXXXXX");
      mDefaultDurationText.setPreferredSize(mDefaultDurationText.getPreferredSize());
      mDefaultDurationText.setText(" 1000ms");
      add(mDefaultDurationText, gbc);
      gbc.gridx = 0;
      gbc.gridy++;
      gbc.weightx = 0;
      gbc.gridwidth = 1;
      add(mTransitionHeader, gbc);
      gbc.gridx = 1;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      add(new JSeparator(), gbc);
      gbc.gridy++;
      gbc.gridx = 0;
      gbc.gridwidth = 2;
      gbc.fill = GridBagConstraints.BOTH;
      gbc.insets = table_inset;
      mSPTransition.setPreferredSize(mTransitionTable.getPreferredSize());
      gbc.weighty = 1;
      add(mSPTransition, gbc);
      gbc.weighty = 0;
      gbc.gridx = 0;
      gbc.gridy++;
      gbc.weightx = 0;
      gbc.gridwidth = 1;
      gbc.insets = default_inset;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      add(mConstraintSetHeader, gbc);
      gbc.gridx = 1;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      add(new JSeparator(), gbc);
      gbc.gridy++;
      gbc.gridx = 0;
      gbc.gridwidth = 2;
      gbc.weighty = 1;
      gbc.fill = GridBagConstraints.BOTH;
      gbc.insets = new Insets(0, 20, 0, 20);
      mSPConstraintSet.setPreferredSize(mConstraintSetTable.getPreferredSize());
      add(mSPConstraintSet, gbc);
      gbc.weighty = 0;
      gbc.gridy++;
      gbc.weighty = 0;
      add(new JComponent() {
      }, gbc);

    }
  }

  // ======================== Base class for both List ==============================
  class BaseListPanel extends JPanel {

    MTag mMotionScene;
    MTag mMotionLayout;
    ListSelectionListener mListSelectionListener;

    BaseListPanel() {
      super(new BorderLayout());
    }

    void setSelectionListener(ListSelectionListener l) {
      mListSelectionListener = l;
    }

    void fireSelection(ListSelectionEvent e) {
      mListSelectionListener.valueChanged(e);
    }
  }

  // ======================== Transition List ==============================

  class TransitionListPanel extends BaseListPanel {

    boolean building = false;
    String[] mTransitionStrings = {
      "<html> <b> Transition_A </b><br>base -> first_state </html>"
    };
    JList<String> mTransitionJList = new JList<>(mTransitionStrings);
    JScrollPane mTListPane = new JScrollPane(mTransitionJList);

    TransitionListPanel() {
      add(mTListPane, BorderLayout.CENTER);
      add(new JLabel("Transitions"), BorderLayout.NORTH);
      mTransitionJList.addListSelectionListener(e -> {
        if (e.getValueIsAdjusting() || building) {
          return;
        }
        select(e);
      });
    }

    void select(ListSelectionEvent e) {
      fireSelection(e);
    }

    String buildListString(MTag tag) {
      String tid = tag.getAttributeValue("id");
      tid = (tid == null) ? "T" : Utils.stripID(tid);
      String start = Utils.stripID(tag.getAttributeValue("constraintSetStart"));
      String end = Utils.stripID(tag.getAttributeValue("constraintSetEnd"));
      return "<html> <b> " + tid + "</b>(" + start + " -> " + end + ") </html>";
    }

    public void setMTag(MTag motionScene) {
      building = true;
      mMotionScene = motionScene;
      int selected = mTransitionJList.getSelectedIndex();
      selected = Math.max(0, selected);
      MTag[] transitions = motionScene.getChildTags("Transition");
      String[] tStrings = new String[transitions.length];
      for (int i = 0; i < tStrings.length; i++) {
        tStrings[i] = buildListString(transitions[i]);
      }
      selected = Math.min(tStrings.length - 1, selected);
      if (selected == -1) {
        selected = 0;
      }
      mTransitionJList.setListData(tStrings);
      mTransitionJList.setSelectedIndex(selected);
      building = false;
    }

    public void clearSelection() {
      building = true;
      mTransitionJList.clearSelection();
      building = false;
    }

    public int getSelected() {
      return mTransitionJList.getSelectedIndex();
    }

    public void setSelectedIndex(int index) {
      mTransitionJList.setSelectedIndex(index);
    }
  }

  // ======================== ConstraintSet List ==============================
  class ConstraintSetListPanel extends BaseListPanel {

    boolean building = false;
    JList<String> mConstraintSetList = new JList<>();
    JScrollPane mTListPane = new JScrollPane(mConstraintSetList);

    ConstraintSetListPanel() {
      add(mTListPane, BorderLayout.CENTER);
      mConstraintSetList.addListSelectionListener(e -> {
        if (e.getValueIsAdjusting() || building) {
          return;
        }
        select(e);
      });

      add(new JLabel("States"), BorderLayout.NORTH);
    }

    void select(ListSelectionEvent e) {
      fireSelection(e);
    }

    String buildListString(MTag tag) {
      String cid = Utils.stripID(tag.getAttributeValue("id"));
      int noc = tag.getChildTags().length;
      String end = tag.getAttributeValue("constraintSetEnd");
      return "<html> <b> " + cid + "(" + noc + ")</html>";
    }

    public void setMTag(MTag motionScene, MTag layout) {
      building = true;
      mMotionScene = motionScene;
      mMotionLayout = layout;
      int selected = mConstraintSetList.getSelectedIndex();
      selected = Math.max(0, selected);
      MTag[] sets = motionScene.getChildTags("ConstraintSet");
      String[] tStrings = new String[sets.length + 1];
      for (int i = 0; i < tStrings.length - 1; i++) {
        tStrings[i] = buildListString(sets[i]);
      }
      int count = (mMotionLayout == null) ? -1 : mMotionLayout.getChildTags().length;
      tStrings[tStrings.length - 1] = (count == -1) ? "layout" : "layout(" + count + ")";
      selected = Math.min(tStrings.length - 1, selected);
      if (selected == -1) {
        selected = 0;
      }
      mConstraintSetList.setListData(tStrings);
      mConstraintSetList.setSelectedIndex(selected);
      building = false;
    }

    public void clearSelection() {
      building = true;
      mConstraintSetList.clearSelection();
      building = false;
    }

    public int getSelected() {
      return mConstraintSetList.getSelectedIndex();
    }
  }
}
