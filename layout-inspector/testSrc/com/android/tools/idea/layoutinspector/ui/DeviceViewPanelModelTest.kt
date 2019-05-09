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
package com.android.tools.idea.layoutinspector.ui

import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.model.VIEW2
import com.android.tools.idea.layoutinspector.model.VIEW3
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.awt.Rectangle
import java.awt.Shape
import java.awt.geom.AffineTransform
import kotlin.math.abs

class DeviceViewPanelModelTest {
  @Test
  fun testFlatRects() {
    val expectedTransforms = mutableListOf(
      ComparingTransform(1.0, 0.0, 0.0, 1.0, -50.0, -100.0),
      ComparingTransform(1.0, 0.0, 0.0, 1.0, -50.0, -100.0),
      ComparingTransform(1.0, 0.0, 0.0, 1.0, -50.0, -100.0),
      ComparingTransform(1.0, 0.0, 0.0, 1.0, -50.0, -100.0))

    checkRects(expectedTransforms, 0.0, 0.0)
  }


  @Test
  fun test1dRects() {
    val expectedTransforms = mutableListOf(
      ComparingTransform(0.995, 0.0, 0.0, 1.0, -64.749, -100.0),
      ComparingTransform(0.995, 0.0, 0.0, 1.0, -49.749, -100.0),
      ComparingTransform(0.995, 0.0, 0.0, 1.0, -34.749, -100.0),
      ComparingTransform(0.995, 0.0, 0.0, 1.0, -49.749, -100.0))

    checkRects(expectedTransforms, 0.1, 0.0)
  }

  @Test
  fun test2dRects() {
    val expectedTransforms = mutableListOf(
      ComparingTransform(0.995, -0.010, -0.010, 0.980, -63.734, -127.468),
      ComparingTransform(0.995, -0.010, -0.010, 0.980, -48.734, -97.468),
      ComparingTransform(0.995, -0.010, -0.010, 0.980, -33.734, -67.468),
      ComparingTransform(0.995, -0.010, -0.010, 0.980, -48.734, -97.468))

    checkRects(expectedTransforms, 0.1, 0.2)
  }

  private fun checkRects(expectedTransforms: MutableList<ComparingTransform>, xOff: Double, yOff: Double) {
    val rects = listOf(
      Rectangle(0, 0, 100, 200),
      Rectangle(0, 0, 50, 60),
      Rectangle(10, 20, 30, 40),
      Rectangle(60, 70, 10, 10))

    val model = model {
      view(ROOT, rects[0]) {
        view(VIEW1, rects[1]) {
          view(VIEW3, rects[2])
        }
        view(VIEW2, rects[3])
      }
    }

    val panelModel = DeviceViewPanelModel(model)
    panelModel.rotate(xOff, yOff)
    panelModel.refresh()

    val actualTransforms = panelModel.hitRects.map { it.second }
    assertEquals(expectedTransforms, actualTransforms)

    val transformedRects = rects.zip(actualTransforms) { rect, transform -> transform.createTransformedShape(rect) }
    transformedRects.zip(panelModel.hitRects.map { it.first }).forEach { (expected, actual) -> assertPathEqual(expected, actual) }
  }

  private fun assertPathEqual(expected: Shape, actual: Shape) {
    val expectedVals = DoubleArray(2)
    val actualVals = DoubleArray(2)
    val expectedIter = expected.getPathIterator(AffineTransform())
    val actualIter = actual.getPathIterator(AffineTransform())
    while (!expectedIter.isDone && !actualIter.isDone) {
      assertEquals(expectedIter.currentSegment(expectedVals), actualIter.currentSegment(actualVals))
      assertArrayEquals(expectedVals, actualVals, EPSILON)
      expectedIter.next()
      actualIter.next()
    }
    assertTrue(expectedIter.isDone)
    assertTrue(actualIter.isDone)
  }
}


private const val EPSILON = 0.001

private class ComparingTransform(m00: Double, m10: Double,
                                 m01: Double, m11: Double,
                                 m02: Double, m12: Double) : AffineTransform(m00, m10, m01, m11, m02, m12) {
  override fun equals(other: Any?): Boolean {
    return other is AffineTransform &&
           abs(other.translateX - translateX) < EPSILON &&
           abs(other.translateY - translateY) < EPSILON &&
           abs(other.shearX - shearX) < EPSILON &&
           abs(other.shearY - shearY) < EPSILON &&
           abs(other.scaleX - scaleX) < EPSILON &&
           abs(other.scaleY - scaleY) < EPSILON
  }

  override fun hashCode(): Int {
    fail() // should be unused
    return 0
  }
}