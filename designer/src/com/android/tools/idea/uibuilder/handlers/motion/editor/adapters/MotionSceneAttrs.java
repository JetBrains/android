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

import com.android.SdkConstants;
import java.util.Arrays;
import java.util.HashSet;

/**
 * List of attributes used to copy tags to during conversion to Constraints
 */
public final class MotionSceneAttrs {

  public static final String MOTION = "motion";
  public static final String ANDROID = "android";

  public static final String ATTR_ANDROID_TRANSLATIONX = "translationX";
  public static final String ATTR_ANDROID_TRANSLATIONY = "translationY";
  public static final String ATTR_ANDROID_SCALEX = "scaleX";
  public static final String ATTR_ANDROID_SCALEY = "scaleY";
  public static final String ATTR_ANDROID_ROTATION = "rotation";
  public static final String ATTR_ANDROID_ROTATIONX = "rotationX";
  public static final String ATTR_ANDROID_ROTATIONY = "rotationY";
  public static final String ATTR_ANDROID_TRANSLATIONZ = "translationZ";
  public static final String ATTR_ANDROID_ELEVATION = SdkConstants.ATTR_ELEVATION;

  public static final String ATTR_TOOLS_SCREEN_ROTATION = "screenRotation";

  public static final String ATTR_ANDROID_LAYOUT_WIDTH = SdkConstants.ATTR_LAYOUT_WIDTH;
  public static final String ATTR_ANDROID_LAYOUT_HEIGHT = SdkConstants.ATTR_LAYOUT_HEIGHT;
  public static final String ATTR_ANDROID_LAYOUT_MARGIN_LEFT = SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
  public static final String ATTR_ANDROID_LAYOUT_MARGIN_RIGHT = SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT;
  public static final String ATTR_ANDROID_LAYOUT_MARGIN_START = SdkConstants.ATTR_LAYOUT_MARGIN_START;
  public static final String ATTR_ANDROID_LAYOUT_MARGIN_END = SdkConstants.ATTR_LAYOUT_MARGIN_END;
  public static final String ATTR_ANDROID_LAYOUT_MARGIN_TOP = SdkConstants.ATTR_LAYOUT_MARGIN_TOP;
  public static final String ATTR_ANDROID_LAYOUT_MARGIN_BOTTOM =  SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM;
  public static final String ATTR_ANDROID_ID = SdkConstants.ATTR_ID;
  public static final String ATTR_ANDROID_VISIBILITY = SdkConstants.ATTR_VISIBILITY;
  public static final String ATTR_ANDROID_ORIENTATION =  SdkConstants.ATTR_ORIENTATION;
  public static final String ATTR_ANDROID_ALPHA = SdkConstants.ATTR_ALPHA;

  public static final String ATTR_LAYOUT_EDITOR_ABSOLUTE_X = "layout_editor_absoluteX";
  public static final String ATTR_LAYOUT_EDITOR_ABSOLUTE_Y = "layout_editor_absoluteY";
  public static final String ATTR_LAYOUT_LEFT_CREATOR = "layout_constraintLeft_creator";
  public static final String ATTR_LAYOUT_RIGHT_CREATOR = "layout_constraintRight_creator";
  public static final String ATTR_LAYOUT_TOP_CREATOR = "layout_constraintTop_creator";
  public static final String ATTR_LAYOUT_BOTTOM_CREATOR = "layout_constraintBottom_creator";
  public static final String ATTR_LAYOUT_BASELINE_CREATOR = "layout_constraintBaseline_creator";
  public static final String ATTR_LAYOUT_CENTER_CREATOR = "layout_constraintCenter_creator";
  public static final String ATTR_LAYOUT_CENTER_X_CREATOR = "layout_constraintCenterX_creator";
  public static final String ATTR_LAYOUT_CENTER_Y_CREATOR = "layout_constraintCenterY_creator";
  public static final String ATTR_LAYOUT_LEFT_TO_LEFT_OF = "layout_constraintLeft_toLeftOf";
  public static final String ATTR_LAYOUT_LEFT_TO_RIGHT_OF = "layout_constraintLeft_toRightOf";
  public static final String ATTR_LAYOUT_RIGHT_TO_LEFT_OF = "layout_constraintRight_toLeftOf";
  public static final String ATTR_LAYOUT_RIGHT_TO_RIGHT_OF = "layout_constraintRight_toRightOf";
  public static final String ATTR_LAYOUT_TOP_TO_TOP_OF = "layout_constraintTop_toTopOf";
  public static final String ATTR_LAYOUT_TOP_TO_BOTTOM_OF = "layout_constraintTop_toBottomOf";
  public static final String ATTR_LAYOUT_BOTTOM_TO_TOP_OF = "layout_constraintBottom_toTopOf";
  public static final String ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF = "layout_constraintBottom_toBottomOf";
  public static final String ATTR_LAYOUT_BASELINE_TO_BASELINE_OF = "layout_constraintBaseline_toBaselineOf";
  public static final String ATTR_LAYOUT_BASELINE_TO_TOP_OF = "layout_constraintBaseline_toTopOf";
  public static final String ATTR_LAYOUT_BASELINE_TO_BOTTOM_OF = "layout_constraintBaseline_toBottomOf";

  public static final String ATTR_LAYOUT_START_TO_END_OF = "layout_constraintStart_toEndOf";
  public static final String ATTR_LAYOUT_START_TO_START_OF = "layout_constraintStart_toStartOf";
  public static final String ATTR_LAYOUT_END_TO_START_OF = "layout_constraintEnd_toStartOf";
  public static final String ATTR_LAYOUT_END_TO_END_OF = "layout_constraintEnd_toEndOf";
  public static final String ATTR_LAYOUT_GONE_MARGIN_LEFT = "layout_goneMarginLeft";
  public static final String ATTR_LAYOUT_GONE_MARGIN_TOP = "layout_goneMarginTop";
  public static final String ATTR_LAYOUT_GONE_MARGIN_RIGHT = "layout_goneMarginRight";
  public static final String ATTR_LAYOUT_GONE_MARGIN_BOTTOM = "layout_goneMarginBottom";
  public static final String ATTR_LAYOUT_GONE_MARGIN_START = "layout_goneMarginStart";
  public static final String ATTR_LAYOUT_GONE_MARGIN_END = "layout_goneMarginEnd";

  public static final String ATTR_LAYOUT_HORIZONTAL_BIAS = "layout_constraintHorizontal_bias";
  public static final String ATTR_LAYOUT_VERTICAL_BIAS = "layout_constraintVertical_bias";

  public static final String ATTR_LAYOUT_WIDTH_DEFAULT = "layout_constraintWidth_default";
  public static final String ATTR_LAYOUT_HEIGHT_DEFAULT = "layout_constraintHeight_default";
  public static final String ATTR_LAYOUT_WIDTH_MIN = "layout_constraintWidth_min";
  public static final String ATTR_LAYOUT_WIDTH_MAX = "layout_constraintWidth_max";
  public static final String ATTR_LAYOUT_WIDTH_PERCENT = "layout_constraintWidth_percent";
  public static final String ATTR_LAYOUT_HEIGHT_MIN = "layout_constraintHeight_min";
  public static final String ATTR_LAYOUT_HEIGHT_MAX = "layout_constraintHeight_max";
  public static final String ATTR_LAYOUT_HEIGHT_PERCENT = "layout_constraintHeight_percent";
  public static final String ATTR_LAYOUT_DIMENSION_RATIO = "layout_constraintDimensionRatio";
  public static final String ATTR_LAYOUT_VERTICAL_CHAIN_STYLE = "layout_constraintVertical_chainStyle";
  public static final String ATTR_LAYOUT_HORIZONTAL_CHAIN_STYLE = "layout_constraintHorizontal_chainStyle";
  public static final String ATTR_LAYOUT_VERTICAL_WEIGHT = "layout_constraintVertical_weight";
  public static final String ATTR_LAYOUT_HORIZONTAL_WEIGHT = "layout_constraintHorizontal_weight";
  public static final String ATTR_LAYOUT_CHAIN_SPREAD = "spread";
  public static final String ATTR_LAYOUT_CHAIN_SPREAD_INSIDE = "spread_inside";
  public static final String ATTR_LAYOUT_CHAIN_PACKED = "packed";
  public static final String ATTR_LAYOUT_CHAIN_HELPER_USE_RTL = "chainUseRtl";
  public static final String ATTR_LAYOUT_CONSTRAINTSET = "constraintSet";
  public static final String ATTR_LAYOUT_CONSTRAINT_CIRCLE = "layout_constraintCircle";
  public static final String ATTR_LAYOUT_CONSTRAINT_CIRCLE_ANGLE = "layout_constraintCircleAngle";
  public static final String ATTR_LAYOUT_CONSTRAINT_CIRCLE_RADIUS = "layout_constraintCircleRadius";
  public static final String ATTR_LAYOUT_CONSTRAINED_HEIGHT = "layout_constrainedHeight";
  public static final String ATTR_LAYOUT_CONSTRAINED_WIDTH = "layout_constrainedWidth";

  public static final String ATTR_GUIDELINE_ORIENTATION_HORIZONTAL = "horizontal";
  public static final String ATTR_GUIDELINE_ORIENTATION_VERTICAL = "vertical";
  public static final String LAYOUT_CONSTRAINT_GUIDE_BEGIN = "layout_constraintGuide_begin";
  public static final String LAYOUT_CONSTRAINT_GUIDE_END = "layout_constraintGuide_end";
  public static final String LAYOUT_CONSTRAINT_GUIDE_PERCENT = "layout_constraintGuide_percent";
  public static final String LAYOUT_CONSTRAINT_DEPRECATED_GUIDE_PERCENT = "layout_constraintGuide_Percent";
  public static final String ATTR_LOCKED = "locked";
  public static final String ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION;

  public static final String ATTR_CUSTOM_ATTRIBUTE_NAME = SdkConstants.MotionSceneAttributes.ATTR_CUSTOM_ATTRIBUTE_NAME;
  public static final String ATTR_CUSTOM_COLOR_VALUE = SdkConstants.MotionSceneAttributes.ATTR_CUSTOM_COLOR_VALUE;
  public static final String ATTR_CUSTOM_COLOR_DRAWABLE_VALUE = SdkConstants.MotionSceneAttributes.ATTR_CUSTOM_COLOR_DRAWABLE_VALUE;
  public static final String ATTR_CUSTOM_INTEGER_VALUE = SdkConstants.MotionSceneAttributes.ATTR_CUSTOM_INTEGER_VALUE;
  public static final String ATTR_CUSTOM_FLOAT_VALUE = SdkConstants.MotionSceneAttributes.ATTR_CUSTOM_FLOAT_VALUE;
  public static final String ATTR_CUSTOM_STRING_VALUE = SdkConstants.MotionSceneAttributes.ATTR_CUSTOM_STRING_VALUE;
  public static final String ATTR_CUSTOM_DIMENSION_VALUE = SdkConstants.MotionSceneAttributes.ATTR_CUSTOM_DIMENSION_VALUE;
  public static final String ATTR_CUSTOM_PIXEL_DIMENSION_VALUE = SdkConstants.MotionSceneAttributes.ATTR_CUSTOM_PIXEL_DIMENSION_VALUE;
  public static final String ATTR_CUSTOM_BOOLEAN_VALUE = SdkConstants.MotionSceneAttributes.ATTR_CUSTOM_BOOLEAN_VALUE;

  public static final String MOTION_ANIMATE_RELATIVE_TO = "animate_relativeTo";
  public static final String MOTION_TRANSITION_EASING = "transitionEasing";
  public static final String MOTION_PATH_MOTION_ARC = "pathMotionArc";
  public static final String MOTION_MOTION_STAGGER= "motionStagger";
  public static final String MOTION_MOTION_PATH_ROTATE = "motionPathRotate";
  public static final String MOTION_DRAW_PATH = "drawPath";

  ///////////////////////////////// PLATFORM INDEPENDENT ACCESS ////////////////////////////
  public static final class Tags {
    public static final String TRANSITION = SdkConstants.MotionSceneTags.TRANSITION;
    public static final String VIEW_TRANSITION = SdkConstants.MotionSceneTags.VIEW_TRANSITION;
    public static final String CONSTRAINTSET = SdkConstants.MotionSceneTags.CONSTRAINT_SET;
    public static final String INCLUDE = SdkConstants.MotionSceneTags.INCLUDE;
    public static final String CONSTRAINT = SdkConstants.MotionSceneTags.CONSTRAINT;
    public static final String CONSTRAINT_OVERRIDE = SdkConstants.MotionSceneTags.CONSTRAINT_OVERRIDE;
    public static final String KEY_FRAME_SET = SdkConstants.MotionSceneTags.KEY_FRAME_SET;
    public static final String KEY_ATTRIBUTE = SdkConstants.MotionSceneTags.KEY_ATTRIBUTE;
    public static final String KEY_CYCLE = SdkConstants.MotionSceneTags.KEY_CYCLE;
    public static final String KEY_POSITION = SdkConstants.MotionSceneTags.KEY_POSITION;
    public static final String KEY_TRIGGER = SdkConstants.MotionSceneTags.KEY_TRIGGER;
    public static final String KEY_TIME_CYCLE = SdkConstants.MotionSceneTags.KEY_TIME_CYCLE;
    public static final String ON_CLICK = SdkConstants.MotionSceneTags.ON_CLICK;
    public static final String ON_SWIPE = SdkConstants.MotionSceneTags.ON_SWIPE;
    public static final String LAYOUT = SdkConstants.MotionSceneTags.LAYOUT;
    public static final String MOTION = SdkConstants.MotionSceneTags.MOTION;
    public static final String PROPERTY_SET = SdkConstants.MotionSceneTags.PROPERTY_SET;
    public static final String TRANSFORM = SdkConstants.MotionSceneTags.TRANSFORM;
    public static final String CUSTOM_ATTRIBUTE = SdkConstants.MotionSceneTags.CUSTOM_ATTRIBUTE;
  }

  static HashSet<String> androidNameSpace = new HashSet<>(Arrays.asList(
    ATTR_ANDROID_LAYOUT_WIDTH,
    ATTR_ANDROID_LAYOUT_HEIGHT,
    ATTR_ANDROID_LAYOUT_MARGIN_LEFT,
    ATTR_ANDROID_LAYOUT_MARGIN_RIGHT,
    ATTR_ANDROID_LAYOUT_MARGIN_START,
    ATTR_ANDROID_LAYOUT_MARGIN_END,
    ATTR_ANDROID_LAYOUT_MARGIN_TOP,
    ATTR_ANDROID_LAYOUT_MARGIN_BOTTOM,
    ATTR_ANDROID_ID,
    ATTR_ANDROID_VISIBILITY,
    ATTR_ANDROID_ORIENTATION,
    ATTR_ANDROID_ALPHA,
    ATTR_ANDROID_ELEVATION,
    ATTR_ANDROID_TRANSLATIONX,
    ATTR_ANDROID_TRANSLATIONY,
    ATTR_ANDROID_SCALEX,
    ATTR_ANDROID_SCALEY,
    ATTR_ANDROID_ROTATION,
    ATTR_ANDROID_ROTATIONX,
    ATTR_ANDROID_ROTATIONY,
    ATTR_ANDROID_TRANSLATIONZ)
  );

  public static String lookupName(MTag.Attribute attr) {
    if (androidNameSpace.contains(attr.mAttribute)) {
      return SdkConstants.ANDROID_URI;
    }
    return SdkConstants.AUTO_URI;
  }

  public static final String ON_CLICK = "OnClick";

  public static final class MotionScene {
    public static final String ATTR_DEFAULT_DURATION = "defaultDuration";
  }
  public static final class ConstraintSet {
    public static final String ATTR_ID = ATTR_ANDROID_ID;
    public static final String DERIVE_CONSTRAINTS_FROM = "deriveConstraintsFrom";

  }
  public static final class Transition {
    public static final String ATTR_ID = "id";
    public static final String ATTR_CONSTRAINTSET_START = "constraintSetStart";
    public static final String ATTR_CONSTRAINTSET_END = "constraintSetEnd";
    public static final String ATTR_TRANSITION_DISABLE = "transitionDisable";
    public static final String ATTR_AUTO_TRANSITION = "autoTransition";
    public static final String ATTR_MOTION_INTERPOLATOR = "motionInterpolator";
    public static final String ATTR_DURATION = "duration";
    public static final String ATTR_STAGGERED = "staggered";
  }

  public static final class Key {
    public static final String MOTION_TARGET = "motionTarget";
    public static final String FRAME_POSITION = "framePosition";
  }

  public static final class KeyCycle {
    public static final String WAVE_PERIOD = "wavePeriod";
    public static final String WAVE_SHAPE = "waveShape";
    public static final String WAVE_OFFSET = "waveOffset";
  }

  public static final class KeyTrigger {
    public static final String  MOTION_TRIGGER_ON_COLLISION = "motion_triggerOnCollision";
    public static final String  ON_CROSS = "onCross";
  }

  public static String[] KeyAttributeOptions = {
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
    "transitionPathRotate",
  };
  public static String[] KeyAttributeOptionsDefaultValue = {

    "0.5",
    "3dp",
    "45",
    "5",
    "5",
    "1.2",
    "1.2",
    "30dp",
    "30dp",
    "6dp",
    "90",
  };
  public static String[] KeyCycleOptionsDefaultValue = {

    "0.5",
    "3dp",
    "90",
    "5",
    "5",
    "0.2",
    "0.2",
    "10dp",
    "10dp",
    "6dp",
    "45",
  };
  public static String[] KeyCycleOptionsDefaultOffset = {

    "0.5",
    "3dp",
    "0",
    "0",
    "0",
    "1",
    "1",
    "0dp",
    "0dp",
    "3dp",
    "90",
  };

  public static String[]  KeyAttributeOptionsNameSpace = {
    ANDROID,
    ANDROID,
    ANDROID,
    ANDROID,
    ANDROID,
    ANDROID,
    ANDROID,
    ANDROID,
    ANDROID,
    ANDROID,
    MOTION
  };

  public static final class OnClick {
    public static final String ATTR_TARGET_ID = "targetId";
    public static final String ATTR_CLICK_ACTION = "clickAction";
  }

  public static final class OnSwipe {

    public static final String ATTR_DRAG_SCALE = "dragScale";
    public static final String ATTR_MAX_VELOCITY = "maxVelocity";
    public static final String ATTR_MAX_ACCELERATION = "maxAcceleration";
    public static final String ATTR_DRAG_DIRECTION = "dragDirection";
    public static final String ATTR_TOUCH_ANCHOR_ID = "touchAnchorId";
    public static final String ATTR_TOUCH_ANCHOR_SIDE = "touchAnchorSide";
    public static final String ATTR_MOVE_WHEN_SCROLL_AT_TOP = "moveWhenScrollAtTop";
    public static final String ATTR_ON_TOUCH_UP = "onTouchUp";
    public static final String ATTR_SPRING_MASS = "springMass";
    public static final String ATTR_SPRING_STIFFNESS = "springStiffness";
    public static final String ATTR_SPRING_DAMPING = "springDamping";
    public static final String ATTR_SPRING_STOP_THRESHOLD = "springStopThreshold";
    public static final String ATTR_SPRING_BOUNDARY = "springBoundary";

  }

  public static class MotionLayout {
    public static final String ATTR_LAYOUT_DESCRIPTION = "layoutDescription";
    public static final String ATTR_CURRENT_STATE = "currentState";
    public static final String ATTR_MOTION_PROGRESS = "motionProgress";
    public HashSet<String> ANDROID_ATTRS = new HashSet<>(androidNameSpace);
    public static HashSet<String> LAYOUT_ATTRS = new HashSet<>(Arrays.asList(
      ATTR_LAYOUT_EDITOR_ABSOLUTE_X,
      ATTR_LAYOUT_EDITOR_ABSOLUTE_Y,
      ATTR_LAYOUT_LEFT_CREATOR,
      ATTR_LAYOUT_RIGHT_CREATOR,
      ATTR_LAYOUT_TOP_CREATOR,
      ATTR_LAYOUT_BOTTOM_CREATOR,
      ATTR_LAYOUT_BASELINE_CREATOR,
      ATTR_LAYOUT_CENTER_CREATOR,
      ATTR_LAYOUT_CENTER_X_CREATOR,
      ATTR_LAYOUT_CENTER_Y_CREATOR,
      ATTR_LAYOUT_LEFT_TO_LEFT_OF,
      ATTR_LAYOUT_LEFT_TO_RIGHT_OF,
      ATTR_LAYOUT_RIGHT_TO_LEFT_OF,
      ATTR_LAYOUT_RIGHT_TO_RIGHT_OF,
      ATTR_LAYOUT_TOP_TO_TOP_OF,
      ATTR_LAYOUT_TOP_TO_BOTTOM_OF,
      ATTR_LAYOUT_BOTTOM_TO_TOP_OF,
      ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF,
      ATTR_LAYOUT_BASELINE_TO_BASELINE_OF,
      ATTR_LAYOUT_START_TO_END_OF,
      ATTR_LAYOUT_START_TO_START_OF,
      ATTR_LAYOUT_END_TO_START_OF,
      ATTR_LAYOUT_END_TO_END_OF,
      ATTR_LAYOUT_GONE_MARGIN_LEFT,
      ATTR_LAYOUT_GONE_MARGIN_TOP,
      ATTR_LAYOUT_GONE_MARGIN_RIGHT,
      ATTR_LAYOUT_GONE_MARGIN_BOTTOM,
      ATTR_LAYOUT_GONE_MARGIN_START,
      ATTR_LAYOUT_GONE_MARGIN_END,
      ATTR_LAYOUT_HORIZONTAL_BIAS,
      ATTR_LAYOUT_VERTICAL_BIAS,
      ATTR_LAYOUT_WIDTH_DEFAULT,
      ATTR_LAYOUT_HEIGHT_DEFAULT,
      ATTR_LAYOUT_WIDTH_MIN,
      ATTR_LAYOUT_WIDTH_MAX,
      ATTR_LAYOUT_WIDTH_PERCENT,
      ATTR_LAYOUT_HEIGHT_MIN,
      ATTR_LAYOUT_HEIGHT_MAX,
      ATTR_LAYOUT_HEIGHT_PERCENT,
      ATTR_LAYOUT_DIMENSION_RATIO,
      ATTR_LAYOUT_VERTICAL_CHAIN_STYLE,
      ATTR_LAYOUT_HORIZONTAL_CHAIN_STYLE,
      ATTR_LAYOUT_VERTICAL_WEIGHT,
      ATTR_LAYOUT_HORIZONTAL_WEIGHT,
      ATTR_LAYOUT_CHAIN_SPREAD,
      ATTR_LAYOUT_CHAIN_SPREAD_INSIDE,
      ATTR_LAYOUT_CHAIN_PACKED,
      ATTR_LAYOUT_CHAIN_HELPER_USE_RTL,
      ATTR_LAYOUT_CONSTRAINTSET,
      ATTR_LAYOUT_CONSTRAINT_CIRCLE,
      ATTR_LAYOUT_CONSTRAINT_CIRCLE_ANGLE,
      ATTR_LAYOUT_CONSTRAINT_CIRCLE_RADIUS,
      ATTR_LAYOUT_CONSTRAINED_HEIGHT,
      ATTR_LAYOUT_CONSTRAINED_WIDTH,
      ATTR_LAYOUT_BASELINE_TO_TOP_OF,
      ATTR_LAYOUT_BASELINE_TO_BOTTOM_OF,
      LAYOUT_CONSTRAINT_GUIDE_BEGIN,
      LAYOUT_CONSTRAINT_GUIDE_END,
      LAYOUT_CONSTRAINT_GUIDE_PERCENT
    ));
  }

  public static HashSet<String> VERTICAL_CONSTRAINT_ATTRS = new HashSet<>(Arrays.asList(
    ATTR_LAYOUT_TOP_TO_TOP_OF,
    ATTR_LAYOUT_TOP_TO_BOTTOM_OF,
    ATTR_LAYOUT_BOTTOM_TO_TOP_OF,
    ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF,
    ATTR_LAYOUT_BASELINE_TO_BASELINE_OF,
    ATTR_LAYOUT_BASELINE_TO_TOP_OF,
    ATTR_LAYOUT_BASELINE_TO_BOTTOM_OF,
    ATTR_LAYOUT_CONSTRAINT_CIRCLE
  ));

  public static HashSet<String> HORIZONTAL_CONSTRAINT_ATTRS = new HashSet<>(Arrays.asList(
    ATTR_LAYOUT_LEFT_TO_LEFT_OF,
    ATTR_LAYOUT_LEFT_TO_RIGHT_OF,
    ATTR_LAYOUT_RIGHT_TO_LEFT_OF,
    ATTR_LAYOUT_RIGHT_TO_RIGHT_OF,
    ATTR_LAYOUT_START_TO_END_OF,
    ATTR_LAYOUT_START_TO_START_OF,
    ATTR_LAYOUT_END_TO_START_OF,
    ATTR_LAYOUT_CONSTRAINT_CIRCLE
  ));

  public static final HashSet<String> layout_tags = new HashSet<>(Arrays.asList(
    ATTR_ANDROID_LAYOUT_WIDTH,
    ATTR_ANDROID_LAYOUT_HEIGHT,
    ATTR_ANDROID_LAYOUT_MARGIN_LEFT,
    ATTR_ANDROID_LAYOUT_MARGIN_RIGHT,
    ATTR_ANDROID_LAYOUT_MARGIN_START,
    ATTR_ANDROID_LAYOUT_MARGIN_END,
    ATTR_ANDROID_LAYOUT_MARGIN_TOP,
    ATTR_ANDROID_LAYOUT_MARGIN_BOTTOM,
    ATTR_LAYOUT_EDITOR_ABSOLUTE_X,
    ATTR_LAYOUT_EDITOR_ABSOLUTE_Y,
    ATTR_LAYOUT_LEFT_CREATOR,
    ATTR_LAYOUT_RIGHT_CREATOR,
    ATTR_LAYOUT_TOP_CREATOR,
    ATTR_LAYOUT_BOTTOM_CREATOR,
    ATTR_LAYOUT_BASELINE_CREATOR,
    ATTR_LAYOUT_CENTER_CREATOR,
    ATTR_LAYOUT_CENTER_X_CREATOR,
    ATTR_LAYOUT_CENTER_Y_CREATOR,
    ATTR_LAYOUT_LEFT_TO_LEFT_OF,
    ATTR_LAYOUT_LEFT_TO_RIGHT_OF,
    ATTR_LAYOUT_RIGHT_TO_LEFT_OF,
    ATTR_LAYOUT_RIGHT_TO_RIGHT_OF,
    ATTR_LAYOUT_TOP_TO_TOP_OF,
    ATTR_LAYOUT_TOP_TO_BOTTOM_OF,
    ATTR_LAYOUT_BOTTOM_TO_TOP_OF,
    ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF,
    ATTR_LAYOUT_BASELINE_TO_BASELINE_OF,
    ATTR_LAYOUT_START_TO_END_OF,
    ATTR_LAYOUT_START_TO_START_OF,
    ATTR_LAYOUT_END_TO_START_OF,
    ATTR_LAYOUT_END_TO_END_OF,
    ATTR_LAYOUT_GONE_MARGIN_LEFT,
    ATTR_LAYOUT_GONE_MARGIN_TOP,
    ATTR_LAYOUT_GONE_MARGIN_RIGHT,
    ATTR_LAYOUT_GONE_MARGIN_BOTTOM,
    ATTR_LAYOUT_GONE_MARGIN_START,
    ATTR_LAYOUT_GONE_MARGIN_END,
    ATTR_LAYOUT_HORIZONTAL_BIAS,
    ATTR_LAYOUT_VERTICAL_BIAS,
    ATTR_LAYOUT_WIDTH_DEFAULT,
    ATTR_LAYOUT_HEIGHT_DEFAULT,
    ATTR_LAYOUT_WIDTH_MIN,
    ATTR_LAYOUT_WIDTH_MAX,
    ATTR_LAYOUT_WIDTH_PERCENT,
    ATTR_LAYOUT_HEIGHT_MIN,
    ATTR_LAYOUT_HEIGHT_MAX,
    ATTR_LAYOUT_HEIGHT_PERCENT,
    ATTR_LAYOUT_DIMENSION_RATIO,
    ATTR_LAYOUT_VERTICAL_CHAIN_STYLE,
    ATTR_LAYOUT_HORIZONTAL_CHAIN_STYLE,
    ATTR_LAYOUT_VERTICAL_WEIGHT,
    ATTR_LAYOUT_HORIZONTAL_WEIGHT,
    ATTR_LAYOUT_CHAIN_SPREAD,
    ATTR_LAYOUT_CHAIN_SPREAD_INSIDE,
    ATTR_LAYOUT_CHAIN_PACKED,
    ATTR_LAYOUT_CHAIN_HELPER_USE_RTL,
    ATTR_LAYOUT_CONSTRAINTSET,
    ATTR_LAYOUT_CONSTRAINT_CIRCLE,
    ATTR_LAYOUT_CONSTRAINT_CIRCLE_ANGLE,
    ATTR_LAYOUT_CONSTRAINT_CIRCLE_RADIUS,
    ATTR_LAYOUT_CONSTRAINED_HEIGHT,
    ATTR_LAYOUT_CONSTRAINED_WIDTH,
    ATTR_LAYOUT_BASELINE_TO_TOP_OF,
    ATTR_LAYOUT_BASELINE_TO_BOTTOM_OF,
    LAYOUT_CONSTRAINT_GUIDE_BEGIN,
    LAYOUT_CONSTRAINT_GUIDE_END,
    LAYOUT_CONSTRAINT_GUIDE_PERCENT
  ));

  public final static String LAYOUT_CONSTRAINT_TAG = "layout_constraintTag";

  public static final HashSet<String> ourPropertySet_tags = new HashSet<>(Arrays.asList(
    ATTR_ANDROID_ALPHA,
    ATTR_ANDROID_VISIBILITY,
    LAYOUT_CONSTRAINT_TAG
  ));

  public static final HashSet<String> ourTransform_tags = new HashSet<>(Arrays.asList(
    ATTR_ANDROID_ELEVATION,
    ATTR_ANDROID_ROTATION,
    ATTR_ANDROID_ROTATIONX,
    ATTR_ANDROID_ROTATIONY,
    ATTR_ANDROID_SCALEX,
    ATTR_ANDROID_SCALEY,
    ATTR_ANDROID_TRANSLATIONX,
    ATTR_ANDROID_TRANSLATIONY,
    ATTR_ANDROID_TRANSLATIONZ
  ));

  public static final HashSet<String> ourMotion_tags = new HashSet<>(Arrays.asList(
    MOTION_ANIMATE_RELATIVE_TO,
    MOTION_TRANSITION_EASING,
    MOTION_DRAW_PATH,
    MOTION_MOTION_PATH_ROTATE,
    MOTION_MOTION_STAGGER,
    MOTION_PATH_MOTION_ARC
  ));

public static final String[] ourCustomAttribute = {
    ATTR_CUSTOM_COLOR_VALUE,
    ATTR_CUSTOM_COLOR_DRAWABLE_VALUE,
    ATTR_CUSTOM_INTEGER_VALUE,
    ATTR_CUSTOM_FLOAT_VALUE,
    ATTR_CUSTOM_STRING_VALUE,
    ATTR_CUSTOM_DIMENSION_VALUE,
    ATTR_CUSTOM_PIXEL_DIMENSION_VALUE,
    ATTR_CUSTOM_BOOLEAN_VALUE,
  };

  public static boolean copyToConstraint(MTag.Attribute attr) {
    return layout_tags.contains(attr.mAttribute) ||
           MotionLayout.LAYOUT_ATTRS.contains(attr.mAttribute) ||
           ourTransform_tags.contains(attr.mAttribute) ||
           ourPropertySet_tags.contains(attr.mAttribute);
  }

  public static boolean isLayoutAttribute(MTag.Attribute attr) {
    return layout_tags.contains(attr.mAttribute);
  }

  public static boolean isPropertySetAttribute(MTag.Attribute attr) {
    return ourPropertySet_tags.contains(attr.mAttribute);
  }

  public static boolean isTransformAttribute(MTag.Attribute attr) {
    return ourTransform_tags.contains(attr.mAttribute);
  }
}
