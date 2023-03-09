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

import com.android.tools.idea.uibuilder.handlers.motion.editor.actions.CreateTransitionAction;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEIcons;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEUI;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.timeline.TimeLinePanel;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditorSelector.TimeLineListener;
import com.google.common.collect.ImmutableList;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * The main transition Panel that shows the timeline
 */
public class TransitionPanel extends JPanel {
  TimeLinePanel mTimeLinePanel = new TimeLinePanel();
  MTag mTransitionTag;
  private MeModel mMeModel;
  MotionEditorSelector mMotionEditorSelector;

  public TransitionPanel(MotionEditor motionEditor) {
    super(new BorderLayout());
    JPanel left = new JPanel(new GridBagLayout());
    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JPanel top = new JPanel(new BorderLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    top.add(left, BorderLayout.WEST);
    top.add(right, BorderLayout.EAST);
    top.setBorder(MEUI.getPanelBottomBorder());
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridy = 0;
    gbc.gridx = 0;
    gbc.ipadx = 16;
    left.add(new JLabel("Transition ", MEIcons.LIST_TRANSITION, SwingConstants.CENTER), gbc);
    right.add(new DefaultToolbarImpl(this, "TransitionPanel", ImmutableList.of(new CreateTransitionAction(motionEditor))));
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

  public void clearSelection() {
    mTimeLinePanel.clearSelection();
  }

  public void setActionListener(MTagActionListener listener) {
    mTimeLinePanel.setActionListener(listener);
  }

  public void stopAnimation() {
    mTimeLinePanel.stopAnimation();
  }
}
