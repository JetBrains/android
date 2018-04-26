/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.timeline;

import icons.StudioIcons;

import javax.swing.*;
import java.awt.*;

/**
 * Icons used byt TimelinePanel
 */
public class TimeLineIcons {
  public static final Icon CIRCLE_PLAY = StudioIcons.MotionLayoutUI.CIRCLE_PLAY;
  public static final Icon PLAY = StudioIcons.MotionLayoutUI.PLAY;
  public static final Icon FORWARD = StudioIcons.MotionLayoutUI.FORWARD;
  public static final Icon BACKWARD = StudioIcons.MotionLayoutUI.BACKWARD;
  public static final Icon LOOP = StudioIcons.MotionLayoutUI.LOOP;
  public static final Icon ADD_KEYFRAME = StudioIcons.MotionLayoutUI.ADD_KEYFRAME;
  public static final Icon VIEW = StudioIcons.MotionLayoutUI.VIEW;
  public static final Icon CHART = StudioIcons.MotionLayoutUI.CHART;
  public static final Icon PAUSE = StudioIcons.MotionLayoutUI.PAUSE;
  /* ==================== Simple empty icon needed as a place holder =========================*/
  public static final Icon EMPTY = new Icon() {
    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {

    }

    @Override
    public int getIconWidth() {
      return 0;
    }

    @Override
    public int getIconHeight() {
      return 0;
    }
  };
}
