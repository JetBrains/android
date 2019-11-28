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

import com.android.tools.adtui.common.SwingLine
import com.android.tools.adtui.common.SwingPath
import com.android.tools.adtui.common.SwingShape
import com.android.tools.idea.common.scene.AnimatedValue
import com.android.tools.idea.common.scene.ConstantValue
import com.android.tools.idea.common.scene.LerpValue
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.common.scene.draw.DrawShape
import com.android.tools.idea.common.scene.draw.FillShape
import org.jetbrains.android.AndroidTestCase
import java.awt.Shape
import java.awt.geom.Line2D
import java.awt.geom.Path2D

fun assertDrawCommandsEqual(expected: DrawShape, actual: DrawCommand) {
  val drawShape = actual as DrawShape
  AndroidTestCase.assertEquals(expected.color, drawShape.color)
  AndroidTestCase.assertEquals(expected.stroke, drawShape.stroke)
  assertAnimatedShapesEqual(expected.shape, drawShape.shape)
}

fun assertDrawCommandsEqual(expected: FillShape, actual: DrawCommand) {
  val fillShape = actual as FillShape
  AndroidTestCase.assertEquals(expected.color, fillShape.color)
  assertAnimatedShapesEqual(expected.shape, fillShape.shape)
}

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

fun assertDrawCommandsEqual(expected: DrawLineToMouse, actual: DrawCommand) {
  val drawLineToMouse = actual as DrawLineToMouse
  AndroidTestCase.assertEquals(expected.center, drawLineToMouse.center)
}

fun assertDrawCommandsEqual(expected: DrawHeader, actual: DrawCommand) {
  val drawHeader = actual as DrawHeader
  AndroidTestCase.assertEquals(expected.rectangle, drawHeader.rectangle)
  AndroidTestCase.assertEquals(expected.scale, drawHeader.scale)
  AndroidTestCase.assertEquals(expected.text, drawHeader.text)
  AndroidTestCase.assertEquals(expected.isStart, drawHeader.isStart)
  AndroidTestCase.assertEquals(expected.hasDeepLink, drawHeader.hasDeepLink)
}

fun assertDrawCommandsEqual(expected: DrawSelfAction, actual: DrawCommand) {
  val drawSelfAction = actual as DrawSelfAction

  AndroidTestCase.assertEquals(expected.rectangle, drawSelfAction.rectangle)
  AndroidTestCase.assertEquals(expected.scale, drawSelfAction.scale)
  AndroidTestCase.assertEquals(expected.color, drawSelfAction.color)
  AndroidTestCase.assertEquals(expected.isPopAction, drawSelfAction.isPopAction)
}

fun assertDrawCommandsEqual(expected: DrawAction, actual: DrawCommand) {
  val drawAction = actual as DrawAction

  AndroidTestCase.assertEquals(expected.source, drawAction.source)
  AndroidTestCase.assertEquals(expected.dest, drawAction.dest)
  AndroidTestCase.assertEquals(expected.scale, drawAction.scale)
  AndroidTestCase.assertEquals(expected.color, drawAction.color)
  AndroidTestCase.assertEquals(expected.isPopAction, drawAction.isPopAction)
}

fun assertAnimatedShapesEqual(expected: AnimatedValue<SwingShape>, actual: AnimatedValue<SwingShape>) {
  val constantValue = expected as? ConstantValue<Shape>
  if(constantValue != null) {
    val actualConstantValue = actual as ConstantValue<Shape>
    AndroidTestCase.assertEquals(constantValue.getValue(0), actualConstantValue.getValue(0))
    return
  }

  val lerpValue = expected as? LerpValue<Shape>
  if(lerpValue != null) {
    val actualLerpValue = actual as LerpValue<Shape>
    AndroidTestCase.assertEquals(lerpValue.start, actualLerpValue.start)
    AndroidTestCase.assertEquals(lerpValue.end, actualLerpValue.end)
    AndroidTestCase.assertEquals(lerpValue.duration, actualLerpValue.duration)
    return
  }

  AndroidTestCase.fail("Unrecognized animated value type.")
}

// need to handle lines separately because Line2D.Float doesn't implement the equals operator
fun assertDrawLinesEqual(expected: DrawShape, actual: DrawCommand) {
  val drawShape = actual as DrawShape

  AndroidTestCase.assertEquals(expected.color, drawShape.color)
  AndroidTestCase.assertEquals(expected.stroke, drawShape.stroke)

  val expectedValue = expected.shape as ConstantValue<SwingShape>
  val expectedLine = expectedValue.getValue(0) as SwingLine

  val actualValue = actual.shape as ConstantValue<SwingShape>
  val actualLine = actualValue.getValue(0) as SwingLine

  AndroidTestCase.assertEquals(actualLine.x1, expectedLine.x1)
  AndroidTestCase.assertEquals(actualLine.y1, expectedLine.y1)
  AndroidTestCase.assertEquals(actualLine.x2, expectedLine.x2)
  AndroidTestCase.assertEquals(actualLine.y2, expectedLine.y2)
}

fun assertFillPathEqual(expected: FillShape, actual: DrawCommand) {
  val fillShape = actual as FillShape

  AndroidTestCase.assertEquals(expected.color, fillShape.color)

  val expectedValue = expected.shape as ConstantValue<SwingShape>
  val expectedPath = expectedValue.getValue(0) as SwingPath

  val actualValue = actual.shape as ConstantValue<SwingShape>
  val actualPath = actualValue.getValue(0) as SwingPath

  AndroidTestCase.assertEquals(actualPath.currentPoint, expectedPath.currentPoint)
}