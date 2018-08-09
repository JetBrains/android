package com.android.tools.idea.uibuilder.handlers.motion;

import java.util.Arrays;
import java.util.HashSet;

public class MotionSceneString {
  public static final String CustomLabel = "Custom...";
  public static final String TransitionTitle = "Transition";
  public static final String KeyAttributesTitle = "Targeted Attributes";
  public static final String KeyPositionTitle = "Position";
  public static final String KeyCycleTitle = "Cycle";
  public static final String OnSwipeTitle = "OnSwipe";

  public static final String MotionNameSpace = "motion:";
  public static final String AndroidNameSpace = "android:";

  public static final String MotionSceneKeyFrameSet = "KeyFrameSet";
  public static final String MotionSceneTransition = "Transition";
  public static final String MotionSceneOnSwipe = "OnSwipe";

  public static final String MotionSceneConstraintSet = "ConstraintSet";
  public static final String ConstraintSetConstraint = "Constraint";

  public static final String TransitionConstraintSetEnd = "constraintSetEnd";
  public static final String TransitionConstraintSetStart = "constraintSetStart";
  public static final String TransitionDuration = "duration";

  public static final String KeyTypeTimeCycle = "KeyTimeCycle";
  public static final String KeyTypeCycle = "KeyCycle";
  public static final String KeyTypeAttribute = "KeyAttribute";
  public static final String KeyTypePosition = "KeyPosition";

  public static final String KeyPosition_type = "keyPositionType";
  public static final String KeyPosition_type_cartesian = "deltaRelative";

  public static final String Key_framePosition = "framePosition";
  public static final String Key_frameTarget = "target";
  public static final String Key_frameTransitionEasing = "transitionEasing";
  public static final String KeyAttributes_android_orientation = "orientation";
  public static final String KeyAttributes_android_visibility = "visibility";
  public static final String KeyAttributes_android_alpha = "alpha";
  public static final String KeyAttributes_android_translationX = "translationX";
  public static final String KeyAttributes_android_translationY = "translationY";
  public static final String KeyAttributes_android_scaleX = "scaleX";
  public static final String KeyAttributes_android_scaleY = "scaleY";
  public static final String KeyAttributes_android_rotation = "rotation";
  public static final String KeyAttributes_android_rotationX = "rotationX";
  public static final String KeyAttributes_android_rotationY = "rotationY";
  public static final String KeyAttributes_android_translationZ = "translationZ";
  public static final String KeyAttributes_android_elevation = "elevation";
  public static final String KeyAttributes_curveFit = "curveFit";
  public static final String KeyAttributes_framePosition = "framePosition";

  public static final String KeyAttributes_progress = "progress";
  public static final String KeyAttributes_sizePercent = "sizePercent";
  public static final String KeyAttributes_target = "target";
  public static final String KeyAttributes_transitionEasing = "transitionEasing";
  public static final String KeyAttributes_transitionPathRotate = "transitionPathRotate";
  public static final String KeyAttributes_customAttribute = "CustomAttribute";

  public static final String CustomAttributes_attributeName = "attributeName";
  public static final String CustomAttributes_customColorValue = "customColorValue";
  public static final String CustomAttributes_customIntegerValue = "customIntegerValue";
  public static final String CustomAttributes_customFloatValue = "customFloatValue";
  public static final String CustomAttributes_customStringValue = "customStringValue";
  public static final String CustomAttributes_customDimensionValue = "customDimension";
  public static final String CustomAttributes_customBooleanValue = "customBoolean";

  public static final String KeyCycle_android_alpha = "alpha";
  public static final String KeyCycle_android_translationX = "translationX";
  public static final String KeyCycle_android_translationY = "translationY";
  public static final String KeyCycle_android_scaleX = "scaleX";
  public static final String KeyCycle_android_scaleY = "scaleY";
  public static final String KeyCycle_android_rotation = "rotation";
  public static final String KeyCycle_android_rotationX = "rotationX";
  public static final String KeyCycle_android_rotationY = "rotationY";
  public static final String KeyCycle_android_translationZ = "translationZ";
  public static final String KeyCycle_android_elevation = "elevation";
  public static final String KeyCycle_curveFit = "curveFit";
  public static final String KeyCycle_framePosition = "framePosition";
  public static final String KeyCycle_progress = "progress";
  public static final String KeyCycle_target = "target";
  public static final String KeyCycle_transitionEasing = "transitionEasing";
  public static final String KeyCycle_transitionPathRotate = "transitionPathRotate";
  public static final String KeyCycle_waveOffset = "waveOffset";
  public static final String KeyCycle_wavePeriod = "wavePeriod";
  public static final String KeyCycle_waveShape = "waveShape";
  public static final String KeyCycle_waveVariesBy = "waveVariesBy";


  public static final String KeyPosition_framePosition = "framePosition";

  public static final String KeyPosition_transitionEasing = "transitionEasing";


  public static final String KeyPositionPath_circleRadius = "circleRadius";
  public static final String KeyPositionPath_curveFit = "curveFit";
  public static final String KeyPositionPath_drawPath = "drawPath";
  public static final String KeyPositionPath_framePosition = "framePosition";
  public static final String KeyPositionPath_path_percent = "path_percent";
  public static final String KeyPositionPath_perpendicularPath_percent = "perpendicularPath_percent";
  public static final String KeyPosition_percentX = "percentX";
  public static final String KeyPosition_percentY = "percentY";
  public static final String KeyPositionPath_sizePercent = "sizePercent";
  public static final String KeyPositionPath_target = "target";
  public static final String KeyPositionPath_transitionEasing = "transitionEasing";

  public static final String[] ourStandardAttributes = {
    "progress", "waveShape", "wavePeriod", "waveOffset", "waveVariesBy", "transitionPathRotate", "android:alpha", "android:elevation",
    "android:rotation", "android:rotationX", "android:rotationY", "android:scaleX", "android:scaleY", "android:translationX",
    "android:translationY", "android:translationZ"
  };
  public static HashSet<String> ourStandardSet = new HashSet<>(Arrays.asList(ourStandardAttributes));
}
