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
package com.android.tools.idea.uibuilder.handlers.motion.property

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ALPHA
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.ATTR_TEXT_SIZE
import com.android.SdkConstants.AUTO_URI
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.ATTR_ANDROID_ROTATION
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.ConstraintSet.DERIVE_CONSTRAINTS_FROM
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.MOTION_MOTION_STAGGER
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.MOTION_PATH_MOTION_ARC
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.ON_CLICK
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.OnClick.ATTR_TARGET_ID
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.OnSwipe.ATTR_DRAG_DIRECTION
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.Tags.CONSTRAINT
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.Tags.CONSTRAINTSET
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.Tags.KEY_ATTRIBUTE
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.Tags.KEY_CYCLE
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.Tags.KEY_POSITION
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.Tags.KEY_TIME_CYCLE
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.Tags.LAYOUT
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.Tags.MOTION
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.Tags.ON_SWIPE
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.Tags.PROPERTY_SET
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.Tags.TRANSITION
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.Transition.ATTR_DURATION
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.Transition.ATTR_MOTION_INTERPOLATOR
import com.android.tools.idea.uibuilder.handlers.motion.property.testutil.DEFAULT_LAYOUT_FILE
import com.android.tools.idea.uibuilder.handlers.motion.property.testutil.DEFAULT_SCENE_FILE
import com.android.tools.idea.uibuilder.handlers.motion.property.testutil.MotionAttributeRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private const val LAYOUT_FILE = DEFAULT_LAYOUT_FILE
private const val SCENE_FILE = DEFAULT_SCENE_FILE
private const val ATTR_PIVOT_X = "pivotX"

@RunsInEdt
class NavigationTest {

  @JvmField @Rule val projectRule = AndroidProjectRule.withSdk()

  @JvmField @Rule val motionRule = MotionAttributeRule(projectRule)

  @JvmField @Rule val edtRule = EdtRule()

  @Before
  fun setUp() {
    motionRule.enableFileOpenCaptures()
  }

  @Test
  fun testConstraintSet() {
    motionRule.selectConstraintSet("start")
    check(ANDROID_URI, ATTR_ID, CONSTRAINTSET, SCENE_FILE, 6, "android:id=\"@+id/start\"")
    check(
      AUTO_URI,
      DERIVE_CONSTRAINTS_FROM,
      CONSTRAINTSET,
      SCENE_FILE,
      67,
      "<ConstraintSet android:id=\"@+id/base\">",
    )
  }

  @Test
  fun testConstraint() {
    motionRule.selectConstraint("start", "widget")
    check(
      ANDROID_URI,
      ATTR_LAYOUT_WIDTH,
      CONSTRAINT,
      SCENE_FILE,
      11,
      "android:layout_width=\"64dp\"",
    )
    check(
      ANDROID_URI,
      ATTR_LAYOUT_HEIGHT,
      CONSTRAINT,
      SCENE_FILE,
      12,
      "android:layout_height=\"64dp\"",
    )
    check(
      "",
      ATTR_TEXT_SIZE,
      CUSTOM_ATTRIBUTE,
      SCENE_FILE,
      29,
      "motion:customPixelDimension=\"2sp\"/>",
    )
  }

  @Test
  fun testConstraintFromBaseConstraint() {
    motionRule.selectConstraint("start", "image")
    check(
      ANDROID_URI,
      ATTR_LAYOUT_WIDTH,
      CONSTRAINT,
      SCENE_FILE,
      71,
      "android:layout_width=\"100dp\"",
    )
    check(
      ANDROID_URI,
      ATTR_LAYOUT_HEIGHT,
      CONSTRAINT,
      SCENE_FILE,
      72,
      "android:layout_height=\"100dp\"",
    )
  }

  @Test
  fun testConstraintFromSectionedBaseConstraint() {
    motionRule.selectConstraint("end", "button")
    check(AUTO_URI, MOTION_MOTION_STAGGER, CONSTRAINT, SCENE_FILE, 91, "motion:motionStagger=\"1\"")
    check(
      ANDROID_URI,
      ATTR_LAYOUT_WIDTH,
      CONSTRAINT,
      LAYOUT_FILE,
      42,
      "android:layout_width=\"32dp\"",
    )
    check(
      ANDROID_URI,
      ATTR_LAYOUT_HEIGHT,
      CONSTRAINT,
      LAYOUT_FILE,
      43,
      "android:layout_height=\"10dp\"",
    )
    check(
      "",
      ATTR_TEXT_SIZE,
      CUSTOM_ATTRIBUTE,
      SCENE_FILE,
      95,
      "motion:customPixelDimension=\"2sp\"/>",
    )
  }

  @Test
  fun testConstraintFromLayout() {
    motionRule.selectConstraint("base", "widget")
    check(
      ANDROID_URI,
      ATTR_LAYOUT_WIDTH,
      CONSTRAINT,
      LAYOUT_FILE,
      11,
      "android:layout_width=\"32dp\"",
    )
    check(
      ANDROID_URI,
      ATTR_LAYOUT_HEIGHT,
      CONSTRAINT,
      LAYOUT_FILE,
      12,
      "android:layout_height=\"32dp\"",
    )
  }

  @Test
  fun testSectionedConstraint() {
    motionRule.selectConstraint("start", "button")
    check(ANDROID_URI, ATTR_LAYOUT_WIDTH, LAYOUT, SCENE_FILE, 34, "android:layout_width=\"1dp\"")
    check(
      ANDROID_URI,
      ATTR_LAYOUT_HEIGHT,
      LAYOUT,
      SCENE_FILE,
      35,
      "android:layout_height=\"1dp\"/>",
    )
    check(AUTO_URI, MOTION_MOTION_STAGGER, MOTION, SCENE_FILE, 91, "motion:motionStagger=\"1\"")
    check(
      AUTO_URI,
      MOTION_PATH_MOTION_ARC,
      MOTION,
      SCENE_FILE,
      92,
      "motion:pathMotionArc=\"startHorizontal\"/>",
    )
    check(ANDROID_URI, ATTR_ALPHA, PROPERTY_SET, LAYOUT_FILE, 45, "android:alpha=\"0.7\"")
  }

  @Test
  fun testTransition() {
    motionRule.selectTransition("start", "end")
    check(AUTO_URI, ATTR_DURATION, TRANSITION, SCENE_FILE, 103, "motion:duration=\"2000\"")
    check(
      AUTO_URI,
      ATTR_MOTION_INTERPOLATOR,
      TRANSITION,
      SCENE_FILE,
      104,
      "motion:motionInterpolator=\"linear\">",
    )
    check(
      AUTO_URI,
      ATTR_TARGET_ID,
      ON_CLICK,
      SCENE_FILE,
      106,
      "<OnClick motion:targetId=\"@+id/run\"/>",
    )
    check(
      AUTO_URI,
      ATTR_DRAG_DIRECTION,
      ON_SWIPE,
      SCENE_FILE,
      109,
      "motion:dragDirection=\"dragRight\"",
    )
  }

  @Test
  fun testKeyPosition() {
    motionRule.selectKeyFrame("start", "end", KEY_POSITION, 51, "widget")
    check(
      AUTO_URI,
      MOTION_PATH_MOTION_ARC,
      KEY_POSITION,
      SCENE_FILE,
      117,
      "motion:pathMotionArc=\"flip\"",
    )
    check(AUTO_URI, "percentX", KEY_POSITION, SCENE_FILE, 120, "motion:percentX=\"0.7\"")
  }

  @Test
  fun testKeyAttribute() {
    motionRule.selectKeyFrame("start", "end", KEY_ATTRIBUTE, 99, "widget")
    check(
      ANDROID_URI,
      ATTR_ANDROID_ROTATION,
      KEY_ATTRIBUTE,
      SCENE_FILE,
      151,
      "android:rotation=\"1\"",
    )
    check(
      "",
      ATTR_TEXT_SIZE,
      CUSTOM_ATTRIBUTE,
      SCENE_FILE,
      156,
      "motion:customPixelDimension=\"2sp\"/>",
    )
  }

  @Test
  fun testKeyCycle() {
    motionRule.selectKeyFrame("start", "end", KEY_CYCLE, 15, "widget")
    check(
      AUTO_URI,
      "transitionPathRotate",
      KEY_CYCLE,
      SCENE_FILE,
      159,
      "motion:transitionPathRotate=\"1.5\"",
    )
    check("", ATTR_PIVOT_X, CUSTOM_ATTRIBUTE, SCENE_FILE, 164, "motion:customFloatValue=\".3\"/>")
  }

  @Test
  fun testKeyTimeCycle() {
    motionRule.selectKeyFrame("start", "end", KEY_TIME_CYCLE, 25, "widget")
    check(
      AUTO_URI,
      "transitionPathRotate",
      KEY_TIME_CYCLE,
      SCENE_FILE,
      167,
      "motion:transitionPathRotate=\"1.5\"",
    )
    check("", ATTR_PIVOT_X, CUSTOM_ATTRIBUTE, SCENE_FILE, 172, "motion:customFloatValue=\".7\"/>")
  }

  private fun check(
    namespace: String,
    name: String,
    section: String,
    expectedFile: String,
    expectedLine: Int,
    expectedText: String,
  ) {
    Navigation.browseToValue(motionRule.property(namespace, name, section))
    motionRule.checkEditor(expectedFile, expectedLine, expectedText)
  }
}
