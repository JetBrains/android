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
package com.android.tools.idea.uibuilder.handlers.motion.editor.timeline;

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEIcons;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEUI;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;

/**
 * This show the main control buttons in the top left hand corner of the timeline panel.
 */
public class TimeLineTopLeft extends JPanel {

  JButton mForward = new JButton(MEIcons.FORWARD);
  JButton mBackward = new JButton(MEIcons.BACKWARD);
  JButton mPlay = new JButton(MEIcons.PLAY);
  JButton mSlow = new JButton(MEIcons.SLOW_MOTION);
  JButton mLoop = new JButton(MEIcons.LOOP_FORWARD);
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

  TimeLineTopLeft() {
    super(new GridBagLayout());
    setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, MEUI.ourBorder));
    setBackground(MEUI.ourSecondaryPanelBackground);
    Dimension size = new Dimension(MEUI.scale(13), MEUI.scale(13));
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 1;
    gbc.insets = new Insets(MEUI.scale(4), MEUI.scale(4), MEUI.scale(4), MEUI.scale(4));

    for (int i = 0; i < buttons.length; i++) {
      JButton button = buttons[i];
      button.setBorderPainted(false);
      TimelineCommands cmd = TimelineCommands.values()[i];
      button.setPreferredSize(size);
      button.setContentAreaFilled(false);
      add(button, gbc);
      gbc.gridx++;
      gbc.insets.left = 0;

    }
    mBackward.addActionListener(e -> {
        mIsPlaying = false;
        mPlay.setIcon(MEIcons.PLAY);
        command(TimelineCommands.START, 0);
    });
    mForward.addActionListener(e -> {
      mIsPlaying = false;
      mPlay.setIcon(MEIcons.PLAY);
      command(TimelineCommands.END, 0);
    });

    mPlay.addActionListener(e -> {
        if (mIsPlaying) {
          mPlay.setIcon(MEIcons.PLAY);
          command(TimelineCommands.PAUSE, 0);
        } else {
          mPlay.setIcon(MEIcons.PAUSE);
          command(TimelineCommands.PLAY, 0);
        }
        mIsPlaying = !mIsPlaying;
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
}