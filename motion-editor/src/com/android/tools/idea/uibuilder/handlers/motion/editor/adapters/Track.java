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
package com.android.tools.idea.uibuilder.handlers.motion.editor.adapters;

import com.android.tools.idea.common.surface.DesignSurface;
import com.google.wireless.android.sdk.stats.MotionLayoutEditorEvent.MotionLayoutEditorEventType;

/**
 * This is the place holder class for Analytics
 */
public class Track {

  // TODO: Make sure to clean this so we don't leak surface.
  private DesignSurface<?> ourSurface;

  // TODO: We need to know when editor is open / closed so I can track that too.

  public void init(DesignSurface<?> surface) {
    ourSurface = surface;
  }

  private void track(MotionLayoutEditorEventType motionType) {
    InternalMotionTrackerFactory.Companion.getInstance(ourSurface).track(motionType);
  }

  private void createConstraintSet() {
    track(MotionLayoutEditorEventType.CREATE_CONSTRAINT);
  }

  private void motionEditorEdit() {
    track(MotionLayoutEditorEventType.UNKNOWN_EVENT_TYPE); // TODO: Better type
  }

  private void createTransition() {
    track(MotionLayoutEditorEventType.CREATE_TRANSITION);
  }

  private void createOnClick() {
    track(MotionLayoutEditorEventType.CREATE_ONCLICK);
  }

  private void createOnSwipe() {
    track(MotionLayoutEditorEventType.CREATE_ONCLICK);
  }

  private void createKeyCycle() {
    track(MotionLayoutEditorEventType.CREATE_KEY_CYCLE);
  }

  private void createKeyTimeCycle() {
    track(MotionLayoutEditorEventType.CREATE_KEY_TIME_CYCLE);
  }

  private void createKeyTrigger() {
    track(MotionLayoutEditorEventType.CREATE_KEY_TRIGGER);
  }

  private void createKeyPosition() {
    track(MotionLayoutEditorEventType.CREATE_KEY_POSITION);
  }

  private void createKeyAttribute() {
    track(MotionLayoutEditorEventType.CREATE_KEY_ATTRIBUTES);
  }

  private void playAnimation() {
    track(MotionLayoutEditorEventType.MOTION_PLAY);
  }

  private void animationSpeed() {
    track(MotionLayoutEditorEventType.MOTION_SPEED);
  }

  private void animationEnd() {
    track(MotionLayoutEditorEventType.MOTION_PAUSE);
  }

  private void animationStart() {
    track(MotionLayoutEditorEventType.MOTION_PAUSE);
  }

  private void animationDirectionToggle() {
    track(MotionLayoutEditorEventType.MOTION_DIRETION_TOGGLE);
  }

  private void createConstraint() {
    track(MotionLayoutEditorEventType.CONVERT_TO_CONSTRAINT);
  }

  private void clearConstraint() {
    track(MotionLayoutEditorEventType.CLEAR_CONSTRAINT);
  }

  private void selectConstraint() {
    track(MotionLayoutEditorEventType.CONSTRAINT_TABLE_SELECT);
  }

  private void changeLayout() {
    track(MotionLayoutEditorEventType.TOGGLE_LAYOUT);
  }

  private void showConstraintSetTable() {
    track(MotionLayoutEditorEventType.SHOW_CONSTRAINTSET);
  }

  private void transitionSelection() {
    track(MotionLayoutEditorEventType.SHOW_TIMELINE);
  }

  private void showLayoutTable() {
    track(MotionLayoutEditorEventType.SHOW_LAYOUT);
  }
  private void layoutTableSelect() {
    track(MotionLayoutEditorEventType.LAYOUT_TABLE_SELECT);
  }
  private void timelineTableSelect() {
    track(MotionLayoutEditorEventType.TIMELINE_TABLE_SELECT);
  }

  public static void createConstraintSet(Track track) {
    if (track != null) {
        track.createConstraintSet();
    }
  }

  public static void motionEditorEdit(Track track) {
    if (track != null) {
      track.motionEditorEdit();
    }
  }

  public static void createTransition(Track track) {
    if (track != null) {
      track.createTransition();
    }
  }

  public static void createOnClick(Track track) {
    if (track != null) {
      track.createOnClick();
    }
  }

  public static void createOnSwipe(Track track) {
    if (track != null) {
      track.createOnSwipe();
    }
  }

  public static void createKeyCycle(Track track) {
    if (track != null) {
      track.createKeyCycle();
    }
  }

  public static void createKeyTimeCycle(Track track) {
    if (track != null) {
      track.createKeyTimeCycle();
    }
  }

  public static void createKeyTrigger(Track track) {
    if (track != null) {
      track.createKeyTrigger();
    }
  }

  public static void createKeyPosition(Track track) {
    if (track != null) {
      track.createKeyPosition();
    }
  }

  public static void createKeyAttribute(Track track) {
    if (track != null) {
      track.createKeyAttribute();
    }
  }

  public static void playAnimation(Track track) {
    if (track != null) {
      track.playAnimation();
    }
  }

  public static void animationSpeed(Track track) {
    if (track != null) {
      track.animationSpeed();
    }
  }

  public static void animationEnd(Track track) {
    if (track != null) {
      track.animationEnd();
    }
  }

  public static void animationStart(Track track) {
    if (track != null) {
      track.animationStart();
    }
  }

  public static void animationDirectionToggle(Track track) {
    if (track != null) {
      track.animationDirectionToggle();
    }
  }

  public static void createConstraint(Track track) {
    if (track != null) {
      track.createConstraint();
    }
  }

  public static void clearConstraint(Track track) {
    if (track != null) {
      track.clearConstraint();
    }
  }

  public static void selectConstraint(Track track) {
    if (track != null) {
      track.selectConstraint();
    }
  }

  public static void changeLayout(Track track) {
    if (track != null) {
      track.changeLayout();
    }
  }

  public static void showConstraintSetTable(Track track) {
    if (track != null) {
      track.showConstraintSetTable();
    }
  }

  public static void transitionSelection(Track track) {
    if (track != null) {
      track.transitionSelection();
    }
  }

  public static void showLayoutTable(Track track) {
    if (track != null) {
      track.showLayoutTable();
    }
  }

  public static void layoutTableSelect(Track track) {
    if (track != null) {
      track.layoutTableSelect();
    }
  }
  public static void timelineTableSelect(Track track) {
    if (track != null) {
      track.timelineTableSelect();
    }
  }
}
