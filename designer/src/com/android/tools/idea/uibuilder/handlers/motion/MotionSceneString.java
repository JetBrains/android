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

      public static final  String MotionSceneKeyFrames = "KeyFrames";
      public static final  String MotionSceneTransition = "Transition";
      public static final  String MotionSceneOnSwipe = "OnSwipe";

      public static final  String MotionSceneConstraintSet = "ConstraintSet";
      public static final  String ConstraintSetConstrainView = "ConstrainView";

      public static final String  TransitionConstraintSetEnd = "constraintSetEnd";
      public static final String  TransitionConstraintSetStart= "constraintSetStart";
      public static final String  TransitionDuration = "duration";

      public static final  String KeyTypeCycle="KeyCycle";
      public static final  String KeyTypeAttributes="KeyAttributes";
      public static final  String KeyTypePositionCartesian="KeyPositionCartesian";
      public static final  String KeyTypePositionPath="KeyPositionPath";

      public static final  String Key_framePosition="framePosition";
      public static final  String Key_frameTarget="target";
      public static final  String Key_frameTransitionEasing="transitionEasing";
      public static final  String KeyAttributes_android_orientation="orientation";
      public static final  String KeyAttributes_android_visibility="visibility";
      public static final  String KeyAttributes_android_alpha="alpha";
      public static final  String KeyAttributes_android_translationX="translationX";
      public static final  String KeyAttributes_android_translationY="translationY";
      public static final  String KeyAttributes_android_scaleX="scaleX";
      public static final  String KeyAttributes_android_scaleY="scaleY";
      public static final  String KeyAttributes_android_rotation="rotation";
      public static final  String KeyAttributes_android_rotationX="rotationX";
      public static final  String KeyAttributes_android_rotationY="rotationY";
      public static final  String KeyAttributes_android_translationZ="translationZ";
      public static final  String KeyAttributes_android_elevation="elevation";
      public static final  String KeyAttributes_curveFit="curveFit";
      public static final  String KeyAttributes_framePosition="framePosition";
      public static final  String KeyAttributes_progress="progress";
      public static final  String KeyAttributes_sizePercent="sizePercent";
      public static final  String KeyAttributes_target="target";
      public static final  String KeyAttributes_transitionEasing="transitionEasing";
      public static final  String KeyAttributes_transitionPathRotate="transitionPathRotate";
      public static final  String KeyAttributes_customAttribute="CustomAttribute";

      public static final  String CustomAttributes_attributeName="attributeName";
      public static final  String CustomAttributes_customColorValue="customColorValue";
      public static final  String CustomAttributes_customIntegerValue="customIntegerValue";
      public static final  String CustomAttributes_customFloatValue="customFloatValue";
      public static final  String CustomAttributes_customStringValue="customStringValue";
      public static final  String CustomAttributes_customDimensionValue="customDimension";
      public static final  String CustomAttributes_customBooleanValue="customBoolean";

      public static final  String KeyCycle_android_alpha="alpha";
      public static final  String KeyCycle_android_translationX="translationX";
      public static final  String KeyCycle_android_translationY="translationY";
      public static final  String KeyCycle_android_scaleX="scaleX";
      public static final  String KeyCycle_android_scaleY="scaleY";
      public static final  String KeyCycle_android_rotation="rotation";
      public static final  String KeyCycle_android_rotationX="rotationX";
      public static final  String KeyCycle_android_rotationY="rotationY";
      public static final  String KeyCycle_android_translationZ="translationZ";
      public static final  String KeyCycle_android_elevation="elevation";
      public static final  String KeyCycle_curveFit="curveFit";
      public static final  String KeyCycle_framePosition="framePosition";
      public static final  String KeyCycle_progress="progress";
      public static final  String KeyCycle_target="target";
      public static final  String KeyCycle_transitionEasing="transitionEasing";
      public static final  String KeyCycle_transitionPathRotate="transitionPathRotate";
      public static final  String KeyCycle_waveOffset="waveOffset";
      public static final  String KeyCycle_wavePeriod="wavePeriod";
      public static final  String KeyCycle_waveShape="waveShape";
      public static final  String KeyCycle_waveVariesBy="waveVariesBy";

      public static final  String KeyPositionAbsolute_circleRadius="circleRadius";
      public static final  String KeyPositionAbsolute_curveFit="curveFit";
      public static final  String KeyPositionAbsolute_deltaEndX="deltaEndX";
      public static final  String KeyPositionAbsolute_deltaEndY="deltaEndY";
      public static final  String KeyPositionAbsolute_deltaStartX="deltaStartX";
      public static final  String KeyPositionAbsolute_deltaStartY="deltaStartY";
      public static final  String KeyPositionAbsolute_drawPath="drawPath";
      public static final  String KeyPositionAbsolute_framePosition="framePosition";
      public static final  String KeyPositionAbsolute_parentX="parentX";
      public static final  String KeyPositionAbsolute_parentY="parentY";
      public static final  String KeyPositionAbsolute_target="target";
      public static final  String KeyPositionAbsolute_transitionEasing="transitionEasing";

      public static final  String KeyPositionCartesian_circleRadius="circleRadius";
      public static final  String KeyPositionCartesian_curveFit="curveFit";
      public static final  String KeyPositionCartesian_drawPath="drawPath";
      public static final  String KeyPositionCartesian_framePosition="framePosition";
      public static final  String KeyPositionCartesian_horizontalPercent="horizontalPercent";
      public static final  String KeyPositionCartesian_horizontalPosition_inDeltaX="horizontalPosition_inDeltaX";
      public static final  String KeyPositionCartesian_horizontalPosition_inDeltaY="horizontalPosition_inDeltaY";
      public static final  String KeyPositionCartesian_sizePercent="sizePercent";
      public static final  String KeyPositionCartesian_target="target";
      public static final  String KeyPositionCartesian_transitionEasing="transitionEasing";
      public static final  String KeyPositionCartesian_verticalPercent="verticalPercent";
      public static final  String KeyPositionCartesian_verticalPosition_inDeltaX="verticalPosition_inDeltaX";
      public static final  String KeyPositionCartesian_verticalPosition_inDeltaY="verticalPosition_inDeltaY";

      public static final  String KeyPositionPath_circleRadius="circleRadius";
      public static final  String KeyPositionPath_curveFit="curveFit";
      public static final  String KeyPositionPath_drawPath="drawPath";
      public static final  String KeyPositionPath_framePosition="framePosition";
      public static final  String KeyPositionPath_path_percent="path_percent";
      public static final  String KeyPositionPath_perpendicularPath_percent="perpendicularPath_percent";
      public static final  String KeyPositionPath_sizePercent="sizePercent";
      public static final  String KeyPositionPath_target="target";
      public static final  String KeyPositionPath_transitionEasing="transitionEasing";

      public static final String [] ourStandardAttributes = {
        "progress", "waveShape", "wavePeriod", "waveOffset", "waveVariesBy", "transitionPathRotate", "android:alpha", "android:elevation",
        "android:rotation", "android:rotationX", "android:rotationY", "android:scaleX", "android:scaleY", "android:translationX",
        "android:translationY", "android:translationZ"
      };
      public static HashSet<String> ourStandardSet = new HashSet<>(Arrays.asList(ourStandardAttributes));
}
