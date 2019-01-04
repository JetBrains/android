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
package com.android.tools.idea.naveditor.scene.draw

import com.android.tools.idea.common.scene.draw.DrawCommand
import org.jetbrains.android.AndroidTestCase

fun assertDrawCommandsEqual(expected: DrawNavScreen, actual: DrawCommand) {
  val navScreen = actual as DrawNavScreen
  AndroidTestCase.assertEquals(expected.rectangle, navScreen.rectangle)
  AndroidTestCase.assertEquals(expected.image, navScreen.image)
}

fun assertDrawCommandsEqual(expected: DrawPlaceholder, actual: DrawCommand) {
  val placeHolder = actual as DrawPlaceholder
  AndroidTestCase.assertEquals(expected.rectangle, placeHolder.rectangle)
}

fun assertDrawCommandsEqual(expected: DrawFragment, actual: DrawCommand) {
  val drawFragment = actual as DrawFragment
  AndroidTestCase.assertEquals(expected.rectangle, drawFragment.rectangle)
  AndroidTestCase.assertEquals(expected.scale, drawFragment.scale)
  AndroidTestCase.assertEquals(expected.highlightColor, drawFragment.highlightColor)
  AndroidTestCase.assertEquals(expected.image, drawFragment.image)
}

fun assertDrawCommandsEqual(expected: DrawActivity, actual: DrawCommand) {
  val drawActivity = actual as DrawActivity
  AndroidTestCase.assertEquals(expected.rectangle, drawActivity.rectangle)
  AndroidTestCase.assertEquals(expected.imageRectangle, drawActivity.imageRectangle)
  AndroidTestCase.assertEquals(expected.scale, drawActivity.scale)
  AndroidTestCase.assertEquals(expected.frameColor, drawActivity.frameColor)
  AndroidTestCase.assertEquals(expected.frameThickness, drawActivity.frameThickness)
  AndroidTestCase.assertEquals(expected.textColor, drawActivity.textColor)
  AndroidTestCase.assertEquals(expected.image, drawActivity.image)
}