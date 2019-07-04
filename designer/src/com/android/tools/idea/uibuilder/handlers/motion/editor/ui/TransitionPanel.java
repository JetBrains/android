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
import com.android.tools.idea.uibuilder.handlers.motion.editor.createDialogs.CreateKeyAttribute;
import com.android.tools.idea.uibuilder.handlers.motion.editor.createDialogs.CreateKeyCycle;
import com.android.tools.idea.uibuilder.handlers.motion.editor.createDialogs.CreateKeyPosition;
import com.android.tools.idea.uibuilder.handlers.motion.editor.createDialogs.CreateKeyTimeCycle;
import com.android.tools.idea.uibuilder.handlers.motion.editor.createDialogs.CreateKeyTrigger;
import com.android.tools.idea.uibuilder.handlers.motion.editor.timeline.TimeLinePanel;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditorSelector.TimeLineListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

/**
 * The main transition Panel that shows the timeline
 */
class TransitionPanel extends JPanel {

  private boolean mShowAll;

  TimeLinePanel mTimeLinePanel = new TimeLinePanel();
  MTag mTransitionTag;
  MTag mMotionSceneTag;
  MTag mLayoutTag;
  private MeModel mMeModel;
  MotionEditorSelector mMotionEditorSelector;
  CreateKeyPosition mCreateKeyPosition = new CreateKeyPosition();
  CreateKeyAttribute mCreateKeyAttribute = new CreateKeyAttribute();
  CreateKeyTrigger mCreateKeyTrigger = new CreateKeyTrigger();
  CreateKeyCycle mCreateKeyCycle = new CreateKeyCycle();
  CreateKeyTimeCycle mCreateKeyTimeCycle = new CreateKeyTimeCycle();

  public TransitionPanel(MotionEditor motionEditor) {
    super(new BorderLayout());
    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JPanel top = new JPanel(new BorderLayout());
    top.add(left, BorderLayout.WEST);
    top.add(right, BorderLayout.EAST);
    left.add(new JLabel("KeyFrames ", MEIcons.LIST_TRANSITION, SwingConstants.LEFT));
    JButton create = MEUI.createToolBarButton(MEIcons.CREATE_KEYFRAME, "Create KeyFrames");
    create.setContentAreaFilled(false);
    right.add(create);
    JPopupMenu popupMenu = new JPopupMenu();
    popupMenu.add(mCreateKeyPosition.getAction(create, motionEditor));
    popupMenu.add(mCreateKeyAttribute.getAction(create, motionEditor));
    popupMenu.add(mCreateKeyTrigger.getAction(create, motionEditor));
    popupMenu.add(mCreateKeyCycle.getAction(create, motionEditor));
    popupMenu.add(mCreateKeyTimeCycle.getAction(create, motionEditor));

    create.addActionListener(e -> {
      popupMenu.show(create, 0, 0);
    });
    add(top, BorderLayout.NORTH);
    add(mTimeLinePanel, BorderLayout.CENTER);

    mTimeLinePanel.addTimeLineListener(new TimeLineListener() {
      @Override
      public void command(MotionEditorSelector.TimeLineCmd cmd, float pos) {
        if (mMeModel != null) {
          mMeModel.setProgress(pos);
        }
      }
    });

  }

  public void setMTag(MTag transitionTag, MeModel model) {
    mTransitionTag = transitionTag;
    mMeModel = model;
    mTimeLinePanel.setMTag(transitionTag, model);
  }

  public void setListeners(MotionEditorSelector listeners) {
    mMotionEditorSelector = listeners;
    mTimeLinePanel.setListeners(listeners);
  }

  public void addTimeLineListener(TimeLineListener timeLineListener) {
    mTimeLinePanel.addTimeLineListener(timeLineListener);
  }
}
