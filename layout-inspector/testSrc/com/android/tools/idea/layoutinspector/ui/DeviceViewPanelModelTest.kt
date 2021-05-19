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
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.model.VIEW2
import com.android.tools.idea.layoutinspector.model.VIEW3
import com.android.tools.idea.layoutinspector.model.VIEW4
import com.android.tools.idea.layoutinspector.view
import com.google.common.base.Objects
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
      ComparingTransform(0.995, 0.0, 0.0, 1.0, -49.749, -100.0),
      ComparingTransform(0.995, 0.0, 0.0, 1.0, -34.749, -100.0))

    checkRects(expectedTransforms, 0.1, 0.0)
  }

  @Test
  fun test2dRects() {
    val expectedTransforms = mutableListOf(
      ComparingTransform(0.995, -0.010, -0.010, 0.980, -63.734, -127.468),
      ComparingTransform(0.995, -0.010, -0.010, 0.980, -48.734, -97.468),
      ComparingTransform(0.995, -0.010, -0.010, 0.980, -48.734, -97.468),
      ComparingTransform(0.995, -0.010, -0.010, 0.980, -33.734, -67.468))

    checkRects(expectedTransforms, 0.1, 0.2)
  }

  @Test
  fun testOverlappingRects() {
    val rects = listOf(
      Rectangle(0, 0, 100, 100),
      Rectangle(0, 0, 100, 50),
      Rectangle(0, 50, 100, 50),
      Rectangle(0, 0, 100, 50),
      Rectangle(0, 0, 100, 50),
      Rectangle(40, 40, 20, 20))

    val model = model {
      view(ROOT, rects[0]) {
        view(VIEW1, rects[1]) {
          view(VIEW2, rects[3]) {
            image()
          }
        }
        view(VIEW3, rects[2])
        view(VIEW4, rects[5])
      }
    }

    val expectedTransforms = mutableListOf(
      ComparingTransform(0.866, 0.0, 0.0, 1.0, -193.301, -50.0),
      ComparingTransform(0.866, 0.0, 0.0, 1.0, -118.301, -50.0),
      ComparingTransform(0.866, 0.0, 0.0, 1.0, -118.301, -50.0),
      ComparingTransform(0.866, 0.0, 0.0, 1.0, -43.301, -50.0),
      ComparingTransform(0.866, 0.0, 0.0, 1.0, -43.301, -50.0),
      ComparingTransform(0.866, 0.0, 0.0, 1.0, 31.698, -50.0)
    )

    checkModel(model, 0.5, 0.0, expectedTransforms, rects)
  }

  @Test
  fun testOverlappingRects2() {
    val rects = listOf(
      Rectangle(0, 0, 100, 100),
      Rectangle(0, 0, 100, 100),
      Rectangle(0, 0, 10, 10),
      Rectangle(0, 0, 10, 10),
      Rectangle(0, 0, 100, 100),
      Rectangle(40, 40, 20, 20))

    val model = model {
      view(ROOT, rects[0]) {
        view(VIEW1, rects[1]) {
          view(VIEW2, rects[2]) {
            image()
          }
        }
        view(VIEW3, rects[4]) {
          view(VIEW4, rects[5])
        }
      }
    }

    val expectedTransforms = mutableListOf(
      ComparingTransform(0.866, 0.0, 0.0, 1.0, -193.301, -50.0),
      ComparingTransform(0.866, 0.0, 0.0, 1.0, -118.301, -50.0),
      ComparingTransform(0.866, 0.0, 0.0, 1.0, -43.301, -50.0),
      ComparingTransform(0.866, 0.0, 0.0, 1.0, -43.301, -50.0),
      ComparingTransform(0.866, 0.0, 0.0, 1.0, 31.699, -50.0),
      ComparingTransform(0.866, 0.0, 0.0, 1.0, 106.699, -50.0)
    )

    checkModel(model, 0.5, 0.0, expectedTransforms, rects)

  }

  @Test
  fun testResetRotation() {
    val model = model {
      view(ROOT, Rectangle(0, 0, 100, 200)) {
        view(VIEW1, Rectangle(10, 10, 50, 100)) {
          image()
        }
      }
    }

    val panelModel = DeviceViewPanelModel(model)
    panelModel.rotate(0.1, 0.2)
    assertEquals(ComparingTransform(0.995, -0.010, -0.010, 0.980, -63.734, -127.468),
                 panelModel.hitRects[0].transform)

    panelModel.resetRotation()
    assertEquals(ComparingTransform(1.0, 0.0, 0.0, 1.0, -50.0, -100.0),
                 panelModel.hitRects[0].transform)
  }

  @Test
  fun testSwitchDevices() {
    val model = model {
      view(ROOT) {
        image()
        view(VIEW1) {
          image()
        }
      }
    }

    val panelModel = DeviceViewPanelModel(model)
    panelModel.rotate(0.1, 0.2)
    assertThat(panelModel.isRotated).isTrue()

    // Switch to a new model
    val model2 =
      view(VIEW3) {
        image()
        view(VIEW1) {
          image()
        }
      }

    model.update(AndroidWindow(model2, VIEW3), listOf(VIEW3), 0)
    panelModel.refresh()

    assertThat(panelModel.isRotated).isTrue()

    // Now switch to a legacy model with only an image on the root
    val legacyModel =
      view(VIEW2) {
        image()
        view(VIEW1)
      }

    model.update(AndroidWindow(legacyModel, VIEW2), listOf(VIEW2), 0)
    panelModel.refresh()

    assertThat(panelModel.isRotated).isFalse()
  }

  @Test
  fun testFindViewsAt() {
    var model = model {
      view(ROOT, 0, 0, 100, 100) {
        view(VIEW1, 0, 0, 30, 30) {
          view(VIEW2, 0, 0, 10, 10)
        }
        image()
        view(VIEW3, 50, 50, 20, 20)
      }
    }
    var panelModel = DeviceViewPanelModel(model)
    // Note that coordinates are transformed to center the view, so (-45, -45) below corresponds to (5, 5)
    assertEquals(listOf(VIEW2, VIEW1, ROOT), panelModel.findViewsAt(-45.0, -45.0).map { it.drawId }.toList())
    assertEquals(listOf(ROOT), panelModel.findViewsAt(-1.0, -1.0).map { it.drawId }.toList())
    assertEquals(listOf(VIEW3, ROOT), panelModel.findViewsAt(10.0, 10.0).map { it.drawId }.toList())

    model = model {
      view(ROOT, 0, 0, 100, 100) {
        view(VIEW1, 0, 0, 100, 100) {
          view(VIEW2, 0, 0, 100, 100)
        }
        view(VIEW3, 0, 0, 100, 100)
      }
    }
    panelModel = DeviceViewPanelModel(model)
    assertEquals(listOf(VIEW3, VIEW2, VIEW1, ROOT), panelModel.findViewsAt(0.0, 0.0).map { it.drawId }.toList())
  }

  private fun checkRects(expectedTransforms: MutableList<ComparingTransform>, xOff: Double, yOff: Double) {
    val rects = listOf(
      Rectangle(0, 0, 100, 200),
      Rectangle(0, 0, 50, 60),
      Rectangle(60, 70, 10, 10),
      Rectangle(10, 20, 30, 40))

    val model = model {
      view(ROOT, rects[0]) {
        view(VIEW1, rects[1]) {
          view(VIEW3, rects[3]) {
            image()
          }
        }
        view(VIEW2, rects[2])
      }
    }

    checkModel(model, xOff, yOff, expectedTransforms.plus(expectedTransforms.last()), rects)
  }

  private fun checkModel(model: InspectorModel,
                         xOff: Double,
                         yOff: Double,
                         expectedTransforms: List<ComparingTransform>,
                         rects: List<Rectangle>) {
    val panelModel = DeviceViewPanelModel(model)
    panelModel.rotate(xOff, yOff)

    val actualTransforms = panelModel.hitRects.map { it.transform }
    assertEquals(expectedTransforms, actualTransforms)

    val transformedRects = rects.zip(actualTransforms) { rect, transform -> transform.createTransformedShape(rect) }
    val zip = transformedRects.zip(panelModel.hitRects.map { it.bounds })
    zip.forEach { (expected, actual) -> assertPathEqual(expected, actual) }
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

  override fun hashCode() = Objects.hashCode(translateX, translateY, shearX, shearY, scaleX, scaleY)
}