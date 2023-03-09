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

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import java.util.ArrayList;

/**
 * This defines the selection in the motion editor panel
 * The types and a listener
 */
public class MotionEditorSelector {

  public enum Type {
    TRANSITION,
    CONSTRAINT_SET,
    CONSTRAINT,
    LAYOUT,
    LAYOUT_VIEW,
    KEY_FRAME_GROUP, //defined as a set of keyFrames of the same target
    KEY_FRAME, //defined as a set of keyFrames of the same target
  }

  public interface Listener {
    int CONTROL_FLAG = MTagActionListener.CONTROL_FLAG;
    public void selectionChanged(Type selection, MTag[] tag, int flags);
  }

  ArrayList<Listener> mListeners = new ArrayList<>();

  public void addSelectionListener(Listener listener) {
    mListeners.add(listener);
  }

  public void notifyListeners(Type type, MTag[] tags, int flags) {
    for (Listener listener : mListeners) {
      listener.selectionChanged(type, tags, flags);
    }
  }

  /**
   * The time line controls animation
   * The position is set by motion_progress
   * Before it starts playing it will issue a MOTION_PLAY followed by many MOTION_PROGRESSES
   */
  public enum TimeLineCmd {
    MOTION_PROGRESS,
    MOTION_PLAY,
    MOTION_STOP,
    MOTION_SCRUB,
    MOTION_CAPTURE
  }

  public interface TimeLineListener {
    public void command(TimeLineCmd cmd, float pos);
  }

}
