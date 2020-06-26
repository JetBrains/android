/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.jetbrains.android.dom.transition;

import java.util.Arrays;
import java.util.List;

public final class TransitionDomUtil {
  public static final String TRANSITION_MANAGER_TAG = "transitionManager";
  public static final String TRANSITION_TAG = "transition";
  public static final String TRANSITION_SET_TAG = "transitionSet";
  public static final String FADE_TAG = "fade";
  public static final String TARGETS_TAG = "targets";
  public static final String TARGET_TAG = "target";
  public static final String CHANGE_BOUNDS_TAG = "changeBounds";
  public static final String AUTO_TRANSITION_TAG = "autoTransition";
  public static final String SLIDE_TAG = "slide";
  public static final String EXPLODE_TAG = "explode";
  public static final String CHANGE_IMAGE_TRANSFORM_TAG = "changeImageTransform";
  public static final String CHANGE_TRANSFORM_TAG = "changeTransform";
  public static final String CHANGE_CLIP_BOUNDS_TAG = "changeClipBounds";
  public static final String RECOLOR_TAG = "recolor";
  public static final String CHANGE_SCROLL_TAG = "changeScroll";
  public static final String ARC_MOTION_TAG = "arcMotion";
  public static final String PATH_MOTION_TAG = "pathMotion";
  public static final String PATTERN_PATH_MOTION_TAG = "patternPathMotion";

  public static final String DEFAULT_ROOT = TRANSITION_MANAGER_TAG;
  private static final String[] ROOTS =
    new String[] {
      TRANSITION_MANAGER_TAG,
      TRANSITION_SET_TAG,
      TARGETS_TAG,
      FADE_TAG,
      CHANGE_BOUNDS_TAG,
      SLIDE_TAG,
      EXPLODE_TAG,
      CHANGE_IMAGE_TRANSFORM_TAG,
      CHANGE_TRANSFORM_TAG,
      CHANGE_CLIP_BOUNDS_TAG,
      AUTO_TRANSITION_TAG,
      RECOLOR_TAG,
      CHANGE_SCROLL_TAG,
      ARC_MOTION_TAG,
      PATH_MOTION_TAG,
      PATTERN_PATH_MOTION_TAG
    };

  private TransitionDomUtil() {
  }

  public static List<String> getPossibleRoots() {
    return Arrays.asList(ROOTS);
  }
}
