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
package com.android.tools.idea.uibuilder.handlers.motion.editor.adapters;

/**
 * List of attributes used to copy tags to during conversion to Constraints
 */
public final class MotionLayoutAttrs {

  public static String[] KeyAttributesAll = {
    "framePosition",
    "motionTarget",
    "transitionEasing",
    "curveFit",
    "motionProgress",
    "visibility",
    "alpha",
    "elevation",
    "rotation",
    "rotationX",
    "rotationY",
    "transitionPathRotate",
    "scaleX",
    "scaleY",
    "translationX",
    "translationY",
    "translationZ",
  };

  public static String[] KeyAttributesKey = {
    "motionProgress",
    "visibility",
    "alpha",
    "elevation",
    "rotation",
    "rotationX",
    "rotationY",
    "transitionPathRotate",
    "scaleX",
    "scaleY",
    "translationX",
    "translationY",
    "translationZ",
  };

  public static String[] KeyPositionAll = {
    "keyPositionType",
    "percentX",
    "percentY",
    "percentWidth",
    "percentHeight",
    "framePosition",
    "motionTarget",
    "transitionEasing",
    "pathMotionArc",
    "curveFit",
    "drawPath",
    "sizePercent"
  };

  public static String[] KeyPositionKey = {
  };

  public static String[] KeyCycleAll = {
    "motionTarget",
    "curveFit",
    "framePosition",
    "transitionEasing",
    "motionProgress",
    "waveShape",
    "wavePeriod",
    "waveOffset",
    "transitionPathRotate",
    "alpha",
    "elevation",
    "rotation",
    "rotationX",
    "rotationY",
    "scaleX",
    "scaleY",
    "translationX",
    "translationY",
    "translationZ",
  };

  public static String[] KeyCycleKey = {
    "motionProgress",
    "transitionPathRotate",
    "alpha",
    "elevation",
    "rotation",
    "rotationX",
    "rotationY",
    "scaleX",
    "scaleY",
    "translationX",
    "translationY",
    "translationZ",
  };

  public static String[] KeyTimeCycleAll = {
    "framePosition",
    "motionTarget",
    "transitionEasing",
    "curveFit",
    "waveShape",
    "wavePeriod",
    "motionProgress",
    "waveOffset",
    "waveDecay",
    "alpha",
    "elevation",
    "rotation",
    "rotationX",
    "rotationY",
    "transitionPathRotate",
    "scaleX",
    "scaleY",
    "translationX",
    "translationY",
    "translationZ"
  };

  public static String[] KeyTimeCycleKey = {
    "motionProgress",
    "alpha",
    "elevation",
    "rotation",
    "rotationX",
    "rotationY",
    "transitionPathRotate",
    "scaleX",
    "scaleY",
    "translationX",
    "translationY",
    "translationZ"
  };

  public static String[] KeyTriggerAll = {
    "framePosition",
    "motionTarget",
    "triggerReceiver",
    "onNegativeCross",
    "onPositiveCross",
    "onCross",
    "triggerSlack",
    "triggerId",
    "motion_postLayoutCollision",
    "motion_triggerOnCollision",
  };
  public static String[] KeyTriggerKey = {};
}
