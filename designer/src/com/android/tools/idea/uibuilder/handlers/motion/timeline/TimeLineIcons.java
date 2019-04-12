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

import com.intellij.icons.AllIcons;
import icons.StudioIcons;

import javax.swing.*;

/**
 * Icons used by TimelinePanel
 */
public class TimeLineIcons {
  public static final Icon SLOW_MOTION = StudioIcons.LayoutEditor.Motion.SLOW_MOTION;
  public static final Icon PLAY = StudioIcons.LayoutEditor.Motion.PLAY;
  public static final Icon FORWARD = StudioIcons.LayoutEditor.Motion.GO_TO_END;
  public static final Icon BACKWARD = StudioIcons.LayoutEditor.Motion.GO_TO_START;
  public static final Icon LOOP = StudioIcons.LayoutEditor.Motion.LOOP;
  public static final Icon ADD_KEYFRAME = AllIcons.General.Add;
  public static final Icon REMOVE_KEYFRAME = StudioIcons.Common.REMOVE;
  public static final Icon REMOVE_TAG = StudioIcons.Common.DELETE;
  public static final Icon PAUSE = StudioIcons.LayoutEditor.Motion.PAUSE;
  public static final Icon END_CONSTRAINT = StudioIcons.LayoutEditor.Motion.END_CONSTRAINT;
  public static final Icon START_CONSTRAINT = StudioIcons.LayoutEditor.Motion.START_CONSTRAINT;

}
