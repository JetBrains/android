/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.editor.timeline;

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEIcons;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEUI;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * This show the main control buttons in the top left hand corner of the timeline panel.
 */
public class TimeLineTopLeft extends JPanel {

  JButton mForward = MEUI.createToolBarButton(MEIcons.FORWARD, "Jump to the end of the Transition");
  JButton mBackward =  MEUI.createToolBarButton(MEIcons.BACKWARD, "Jump to the start of the Transition");
  JButton mPlay =  MEUI.createToolBarButton(MEIcons.PLAY,"Play the transition");
  JButton mSlow =  MEUI.createToolBarButton(MEIcons.SMALL_DOWN_ARROW, "x1");
  JButton mLoop = MEUI.createToolBarButton(MEIcons.LOOP_FORWARD,"Cycle between forward, backward, and yoyo");
  JButton[] buttons = {mLoop, mBackward, mPlay, mForward, mSlow};
  Icon[]loop_cycle = {MEIcons.LOOP_FORWARD, MEIcons.LOOP_BACKWARD, MEIcons.LOOP_YOYO };
  int loopMode = 0;
  public enum TimelineCommands {
    LOOP,
    START,
    PLAY,
    END,
    SPEED,
    PAUSE,
  }

  boolean mIsPlaying = false;

  @Override
  public void updateUI() {
    super.updateUI();
    if (buttons == null) {
      return;
    }
    setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, MEUI.ourBorder));
    setPreferredSize(new Dimension(MEUI.ourLeftColumnWidth, MEUI.ourHeaderHeight));
    Dimension size = new Dimension(MEUI.scale(13), MEUI.scale(13));
    for (int i = 0; i < buttons.length; i++) {
      JButton button = buttons[i];
      button.setPreferredSize(size);
    }
  }

  TimeLineTopLeft() {
    super(new GridBagLayout());
    mSlow.setText("1.0x");
    setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, MEUI.ourBorder));
    setBackground(MEUI.ourSecondaryPanelBackground);
    Dimension size = new Dimension(MEUI.scale(13), MEUI.scale(13));
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 1;
    gbc.insets = new Insets(MEUI.scale(1), MEUI.scale(1), MEUI.scale(1), MEUI.scale(1));

    for (int i = 0; i < buttons.length; i++) {
      JButton button = buttons[i];
      button.setBackground(this.getBackground());
      TimelineCommands cmd = TimelineCommands.values()[i];
      if (cmd == TimelineCommands.SPEED) {
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        button.setForeground(MEUI.ourTextColor);
        button.setHorizontalTextPosition(SwingConstants.LEFT);
        button.setFont(MEUI.getToolBarButtonSmallFont());
      }
      else {
        gbc.weightx = 0.05;
        gbc.fill = GridBagConstraints.NONE;
      }
      button.setPreferredSize(size);
      add(button, gbc);
      gbc.gridx++;
      gbc.insets.left = 0;

    }
    mBackward.addActionListener(e -> {
        displayPlay();
        command(TimelineCommands.START, 0);
    });
    mForward.addActionListener(e -> {
      displayPlay();
      command(TimelineCommands.END, 0);
    });

    mPlay.addActionListener(e -> {
        if (mIsPlaying) {
          displayPlay();
          command(TimelineCommands.PAUSE, 0);
        } else {
          displayPause();
          command(TimelineCommands.PLAY, 0);
        }
    });

    mSlow.addActionListener(e -> {
        command(TimelineCommands.SPEED, 0);
    });
    mLoop.addActionListener(e -> {
        mLoop.setIcon(loop_cycle[loopMode = (loopMode+1)%3]);
      command(TimelineCommands.LOOP, loopMode);

    });
    setPreferredSize(new Dimension(MEUI.ourLeftColumnWidth, MEUI.ourHeaderHeight));
  }

  void command(TimelineCommands commands, int mode) {
    notifyTimeLineListeners(commands, mode);
  }

  public interface ControlsListener {
    public void action(TimelineCommands cmd, int mode);
  }

  ArrayList<ControlsListener> mTimeLineListeners = new ArrayList<>();

  public void addControlsListener(ControlsListener listener) {
    mTimeLineListeners.add(listener);
  }

  public void notifyTimeLineListeners(TimelineCommands cmd,int mode) {
    for (ControlsListener listener : mTimeLineListeners) {
      listener.action(cmd, mode);
    }
  }

  public void displayPlay() {
    mIsPlaying = false;
    mPlay.setIcon(MEIcons.PLAY);
  }

  public void displayPause() {
    mIsPlaying = true;
    mPlay.setIcon(MEIcons.PAUSE);
  }

}