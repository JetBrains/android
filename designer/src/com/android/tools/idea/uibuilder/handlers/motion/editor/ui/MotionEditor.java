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

import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.NlComponentTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.Annotations.NotNull;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.Annotations.Nullable;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEIcons;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEScrollPane;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.METabbedPane;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEUI;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.Tags;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.Track;
import com.android.tools.idea.uibuilder.handlers.motion.editor.createDialogs.CreateConstraintSet;
import com.android.tools.idea.uibuilder.handlers.motion.editor.createDialogs.CreateOnClick;
import com.android.tools.idea.uibuilder.handlers.motion.editor.createDialogs.CreateOnSwipe;
import com.android.tools.idea.uibuilder.handlers.motion.editor.createDialogs.CreateTransition;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditorSelector.TimeLineListener;
import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.Debug;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;

/**
 * The main MotionEditor Panel
 */
public class MotionEditor extends JPanel {
  public final static boolean DEBUG = false;
  private final JPanel mMainPanel;
  private CardLayout mErrorSwitchCard;
  public Track myTrack = new Track();
  ErrorPanel myErrorPanel = new ErrorPanel();
  MeModel mMeModel;
  MotionEditorSelector mMotionEditorSelector = new MotionEditorSelector();
  JTabbedPane mTabbedTopPane = new METabbedPane();
  MotionScenePanel mMotionSceneTabb = new MotionScenePanel();
  private TransitionPanel mTransitionPanel = new TransitionPanel(this);
  ConstraintSetPanel mConstraintSetPanel = new ConstraintSetPanel();
  LayoutPanel mLayoutPanel = new LayoutPanel();
  CombinedListPanel mCombinedListPanel = new CombinedListPanel();
  OverviewPanel mOverviewPanel = new OverviewPanel();
  JScrollPane mOverviewScrollPane = new MEScrollPane(mOverviewPanel);
  CardLayout mCardLayout = new CardLayout();
  JPanel mCenterPanel = new JPanel(mCardLayout);
  JButton mCreateGestureToolbarButton;
  JButton mCreateTransitionToolbarButton;
  private static final String LAYOUT_PANEL = "Layout";
  private static final String TRANSITION_PANEL = "Transition";
  private static final String CONSTRAINTSET_PANEL = "ConstraintSet";
  private String mCurrentlyDisplaying = CONSTRAINTSET_PANEL;
  private final List<Command> myCommandListeners = new ArrayList<>();

  CreateConstraintSet mCreateConstraintSet = new CreateConstraintSet();
  CreateOnClick mCreateOnClick = new CreateOnClick();
  CreateOnSwipe mCreateOnSwipe = new CreateOnSwipe();
  CreateTransition mCreateTransition = new CreateTransition();
  JSplitPane mTopPanel;
  boolean mUpdatingModel;
  JPopupMenu myPopupMenu = new JPopupMenu();
  private static final String MAIN_PANEL = "main";
  private static final String ERROR_PANEL = "error";
  private int mFlags;

  @Override
  public void updateUI() {
    super.updateUI();
    if (mMotionSceneTabb != null) { // any are not null they have been initialized
      myErrorPanel.updateUI();
      mMotionSceneTabb.updateUI();
      mTransitionPanel.updateUI();
      mConstraintSetPanel.updateUI();
      mLayoutPanel.updateUI();
      mCombinedListPanel.updateUI();
      mOverviewScrollPane.updateUI();
      mCenterPanel.updateUI();
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

  /**
   * This will selected the views or ConstraintSets based on the ids
   *
   * @param ids
   */
  public void selectById(String[] ids) {
    switch (mCurrentlyDisplaying) {
      case LAYOUT_PANEL:
        mLayoutPanel.selectByIds(ids);
        break;
      case CONSTRAINTSET_PANEL:
        mConstraintSetPanel.selectById(ids);
        break;
      case TRANSITION_PANEL:
    }
  }

  public void addCommandListener(Command command) {
    myCommandListeners.add(command);
  }

  private void fireCommand(Command.Action action, MTag[] tags) {
    for (Command listener : myCommandListeners) {
      listener.perform(action, tags);
    }
  }

  public void stopAnimation() {
    mTransitionPanel.mTimeLinePanel.stopAnimation();
  }

  enum LayoutMode {
    VERTICAL_LAYOUT,
    HORIZONTAL_LAYOUT,
    OVERVIEW_ONLY_LAYOUT
  }

  LayoutMode mLayoutMode = null;

  /**
   * The selected tag in the motion editor.
   *
   * This will be one of:
   * <ul>
   *   <li>ConstraintSet</li>
   *   <li>Transition</li>
   *   <li>MotionLayout</li>
   * </ul>
   *
   * Constraints and KeyFrames are not stored as the selected tag here. Instead
   * those selection is handles by sub selections:
   * <ul>
   *    <li>A constraint by the selected view id</li>
   *    <li>A view by the selected view id</li>
   *    <li>A key frame by the mSelectedKeyFrame in the TimeLine panel</li>
   * </ul>
   */
  private MTag mSelectedTag;

  public void dataChanged() {
    setMTag(mMeModel);
  }

  public MotionEditor() {
    super(new CardLayout());
    mErrorSwitchCard = (CardLayout)getLayout();
    mMainPanel = new JPanel(new BorderLayout());
    add(mMainPanel, MAIN_PANEL);
    add(myErrorPanel, ERROR_PANEL);

    mErrorSwitchCard.show(this, MAIN_PANEL);
    mOverviewScrollPane.setBorder(BorderFactory.createEmptyBorder());

    JPanel ui = new JPanel(new GridLayout(2, 1));

    mCombinedListPanel.setSelectionListener(e -> {
      listSelection();
    });
    mMotionEditorSelector.addSelectionListener(new MotionEditorSelector.Listener() {
      @Override
      public void selectionChanged(MotionEditorSelector.Type selection, MTag[] tag, int flags) {
        if (DEBUG) {
          Debug.log(" selectionChanged  " + selection);
          Debug.logStack(" selectionChanged  " + selection, 5);
        }
        mMeModel.setSelected(selection, tag);
      }
    });
    ui.setBackground(MEUI.ourPrimaryPanelBackground);
    mCombinedListPanel.setPreferredSize(new Dimension(10, 100));
    mTopPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mCombinedListPanel, mOverviewScrollPane);
    mTopPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, MEUI.ourBorder));

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
    mTransitionPanel.addTimeLineListener(new TimeLineListener() {
      @Override
      public void command(MotionEditorSelector.TimeLineCmd cmd, float pos) {
        switch (cmd) {
          case MOTION_PROGRESS:
            mOverviewPanel.setTransitionProgress(pos);
            break;
          case MOTION_SCRUB:
          case MOTION_PLAY:
            break;
          case MOTION_STOP:
            mOverviewPanel.setTransitionProgress(Float.NaN);
            break;
        }
      }
    });
    MTagActionListener mTagActionListener = new MTagActionListener() {
      @Override
      public void select(MTag selected, int flags) {
        selectTag(selected, flags);
      }

      @Override
      public void performAction(int type) {
       if (type == SAVE_GIF) {
         mTransitionPanel.mTimeLinePanel.notifyTimeLineListeners(MotionEditorSelector.TimeLineCmd.MOTION_CAPTURE, 0f);
       }
      }

      @Override
      public void delete(MTag[] tags, int flags) {
        fireCommand(Command.Action.DELETE, tags);
      }
    };
    mOverviewPanel.setActionListener(mTagActionListener);
    mTransitionPanel.setActionListener(mTagActionListener);
    mMainPanel.add(ui);
    JPanel toolbarLeft = new JPanel(new FlowLayout(FlowLayout.LEFT));
    JPanel toolbarRight = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JPanel toolbar = new JPanel(new BorderLayout());
    toolbar.add(toolbarLeft, BorderLayout.WEST);
    toolbar.add(toolbarRight, BorderLayout.EAST);

    JButton create_constraintSet = MEUI.createToolBarButton(MEIcons.CREATE_MENU, "Create ConstraintSet");
    toolbarLeft.add(create_constraintSet);
    mCreateTransitionToolbarButton = MEUI.createToolBarButton(MEIcons.CREATE_TRANSITION, MEIcons.LIST_TRANSITION, "Create Transition between ConstraintSets");
    toolbarLeft.add(mCreateTransitionToolbarButton);
    mCreateGestureToolbarButton = MEUI.createToolBarButton(MEIcons.CREATE_ON_STAR, MEIcons.GESTURE, "Create click or swipe handler");
    toolbarLeft.add(mCreateGestureToolbarButton);
    create_constraintSet.setAction(mCreateConstraintSet.getAction(create_constraintSet, this));
    mCreateTransitionToolbarButton.setAction(mCreateTransition.getAction(mCreateTransitionToolbarButton, this));
    create_constraintSet.setHideActionText(true);
    mCreateTransitionToolbarButton.setHideActionText(true);
    mCreateGestureToolbarButton.setHideActionText(true);


    myPopupMenu.add(mCreateOnClick.getAction(mCreateGestureToolbarButton, this));
    myPopupMenu.add(mCreateOnSwipe.getAction(mCreateGestureToolbarButton, this));

    mCreateGestureToolbarButton.addActionListener(e -> {
      myPopupMenu.show(create_constraintSet, 0, 0);
    });

    JButton cycle = MEUI.createToolBarButton(MEIcons.CYCLE_LAYOUT, "Cycle between layouts");

    toolbarRight.add(cycle);

    cycle.addActionListener(e -> {
      layoutTop();
    });

    mMainPanel.add(toolbar, BorderLayout.NORTH);

    layoutTop();
  }

  public void addSelectionListener(MotionEditorSelector.Listener listener) {
    mMotionEditorSelector.addSelectionListener(listener);
  }

  private void notifyListeners(MotionEditorSelector.Type type, MTag[] tags, int flags) {
    mMotionEditorSelector.notifyListeners(type, tags, flags);
  }

  public MeModel getMeModel() {
    return mMeModel;
  }

  public MTag getSelectedTag() {
    return mSelectedTag;
  }

  public void selectTag(MTag tag, int flags) {
    mFlags = flags;
    String tagName = tag != null ? tag.getTagName() : null;
    if (tagName != null && tagName.equals("Transition")) {
      if (mTransitionPanel.mTimeLinePanel.isPlaying()) {
        return;
      }
      mTransitionPanel.mTimeLinePanel.resetMotionProgress();
    }
    if (tag != null && tagName != null && tag.equals(mSelectedTag)) {
      mConstraintSetPanel.clearSelection();
      mLayoutPanel.clearSelection();
      mTransitionPanel.clearSelection();
      mMeModel.setSelectedViewIDs(new ArrayList<>()); // clear out selections because of double click
      notifyListeners(findSelectionType(tagName), new MTag[]{tag}, flags);
    }
    mSelectedTag = tag;
    if (tag != null) {
      mCombinedListPanel.selectTag(tag);
    }
  }

  @NotNull
  private MotionEditorSelector.Type findSelectionType(@NotNull String tagName) {
    switch (tagName) {
      case Tags.CONSTRAINTSET:
        return MotionEditorSelector.Type.CONSTRAINT_SET;
      case Tags.TRANSITION:
        return MotionEditorSelector.Type.TRANSITION;
      default:
        return MotionEditorSelector.Type.LAYOUT;
    }
  }

  public void setMTag(@NotNull MTag motionScene, @NotNull MTag layout, @Nullable String layoutFileName,
                      @Nullable String motionSceneFileName, String setupError) {
    if (setupError == null && myErrorPanel.validateMotionScene(motionScene)) {
      mErrorSwitchCard.show(this, MAIN_PANEL);
      setMTag(new MeModel(motionScene, layout, layoutFileName, motionSceneFileName, myTrack));
    }
    else {
      if (setupError != null) {
        myErrorPanel.myErrorLabel.setText("<HTML>MotionScene error:<ul>" + setupError + "</ul></HTML>");
      }
      mErrorSwitchCard.show(this, ERROR_PANEL);
    }
  }

  @Nullable
  private MTag findSelectedTagInNewModel(MeModel newModel) {
    if (mSelectedTag instanceof MotionSceneTag) {
      return newModel.motionScene.getChildTagWithTreeId(mSelectedTag.getTagName(), mSelectedTag.getTreeId());
    }
    if (mSelectedTag instanceof NlComponentTag) {
      return newModel.layout;
    }
    return null;
  }

  @Nullable
  private static MTag asConstraintSet(@Nullable MTag selection) {
    return selection != null && selection.getTagName().equals(Tags.CONSTRAINTSET) ? selection : null;
  }

  @Nullable
  private static MTag asTransition(@Nullable MTag selection) {
    return selection != null && selection.getTagName().equals(Tags.TRANSITION) ? selection : null;
  }

  @Nullable
  private static MTag asLayout(@Nullable MTag selection) {
    return selection instanceof NlComponentTag ? selection : null;
  }

  public void setMTag(MeModel model) {
    mUpdatingModel = true;
    try {
      MTag newSelection = findSelectedTagInNewModel(model);
      model.setSelectedViewIDs(mMeModel != null ? mMeModel.getSelectedViewIDs() : EMPTY_STRING_ARRAY);
      mSelectedTag = newSelection;
      mMeModel = model;
      mMotionSceneTabb.setMTag(mMeModel.motionScene);
      mCombinedListPanel.setMTag(mMeModel.motionScene, mMeModel.layout);
      mOverviewPanel.setMTag(mMeModel.motionScene, mMeModel.layout);
      mLayoutPanel.setMTag(asLayout(newSelection), mMeModel);
      mConstraintSetPanel.setMTag(asConstraintSet(newSelection), mMeModel);
      mTransitionPanel.setMTag(asTransition(newSelection), mMeModel);
      mSelectedTag = newSelection;
      MTag[] mtags = model.motionScene.getChildTags("ConstraintSet");
      mCreateTransitionToolbarButton.setEnabled(mtags.length >= 2);
      mtags = model.motionScene.getChildTags("Transition");
      mCreateGestureToolbarButton.setEnabled(mtags.length >= 1);
    }
    finally {
      mUpdatingModel = false;
    }
  }

  public boolean isUpdatingModel() {
    return mUpdatingModel;
  }

  private void layoutTop() {
    if (mLayoutMode == null) {
      mLayoutMode = LayoutMode.OVERVIEW_ONLY_LAYOUT;
    }
    else {
      mLayoutMode = LayoutMode.values()[(mLayoutMode.ordinal() + 1) % LayoutMode.values().length];
    }
    Track.changeLayout(myTrack);
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
    }
    else {
      transitionSelection();
    }
  }

  void constraintSetSelection() {
    int index = mCombinedListPanel.getSelectedConstraintSet();
    mOverviewPanel.setConstraintSetIndex(index);
    mTransitionPanel.stopAnimation();
    if (index >= 0) {
      Track.showConstraintSetTable(myTrack);
      MTag[] c_sets = mCombinedListPanel.mMotionScene.getChildTags("ConstraintSet");
      if (0 < index) {
        mCardLayout.show(mCenterPanel, mCurrentlyDisplaying = CONSTRAINTSET_PANEL);
        MTag selectedConstraintSet = c_sets[index - 1];
        notifyListeners(MotionEditorSelector.Type.CONSTRAINT_SET,
                        new MTag[]{selectedConstraintSet}, 0);
        mSelectedTag = selectedConstraintSet;
        mConstraintSetPanel.setMTag(selectedConstraintSet, mMeModel);
      }
      else {
        Track.showLayoutTable(myTrack);
        mCardLayout.show(mCenterPanel, mCurrentlyDisplaying = LAYOUT_PANEL);
          notifyListeners(MotionEditorSelector.Type.LAYOUT,
                          (mCombinedListPanel.mMotionLayout == null) ? new MTag[0] :
                          new MTag[]{mCombinedListPanel.mMotionLayout}, 0);
        mLayoutPanel.setMTag(mCombinedListPanel.mMotionLayout, mMeModel);

        mSelectedTag = mCombinedListPanel.mMotionLayout;
      }
    }
  }


  void transitionSelection() {
    Track.transitionSelection(myTrack);
    int index = mCombinedListPanel.getSelectedTransition();
    mOverviewPanel.setTransitionSetIndex(index);
    mCardLayout.show(mCenterPanel, mCurrentlyDisplaying = TRANSITION_PANEL);
    MTag[] transitions = mCombinedListPanel.mMotionScene.getChildTags("Transition");
    if (transitions.length == 0) {
      constraintSetSelection();
      return;
    }
    MTag selectedTransition = transitions[index];
    mTransitionPanel.setMTag(selectedTransition, mMeModel);
    notifyListeners(MotionEditorSelector.Type.TRANSITION, new MTag[]{selectedTransition}, mFlags);
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

  public interface Command {
    enum Action {
      DELETE,
      COPY,
    }

    void perform(Action action, MTag[] tag);
  }

  public int getPlayMode() {
    return mTransitionPanel.mTimeLinePanel.getYoyoMode();
  }

  public float getTimeLineSpeed() {
    return mTransitionPanel.mTimeLinePanel.getSpeedMultiplier();
  }
}
