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

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.FakeAndroidWindow
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.ROOT2
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.model.VIEW2
import com.android.tools.idea.layoutinspector.model.VIEW3
import com.android.tools.idea.layoutinspector.model.VIEW4
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.layoutinspector.view
import com.android.tools.idea.layoutinspector.window
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.awt.Polygon
import java.awt.Rectangle
import java.awt.Shape
import java.awt.geom.AffineTransform

private val activityMain = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.LAYOUT, "activity_main")
private const val EPSILON = 0.001

class RenderModelTest {

  @Test
  fun testFlatRects() {
    val expectedTransforms = mapOf(
      ROOT to AffineTransform(1.0, 0.0, 0.0, 1.0, -50.0, -100.0),
      VIEW1 to AffineTransform(1.0, 0.0, 0.0, 1.0, -50.0, -100.0),
      VIEW2 to AffineTransform(1.0, 0.0, 0.0, 1.0, -50.0, -100.0),
      VIEW3 to AffineTransform(1.0, 0.0, 0.0, 1.0, -50.0, -100.0))

    checkRects(expectedTransforms, 0.0, 0.0)
  }

  @Test
  fun testFlatRectsWithHiddenSystemNodes() {
    val expectedTransforms = mapOf(
      VIEW2 to AffineTransform(1.0, 0.0, 0.0, 1.0, -50.0, -100.0),
      VIEW3 to AffineTransform(1.0, 0.0, 0.0, 1.0, -50.0, -100.0))

    checkRects(expectedTransforms, 0.0, 0.0, hideSystemNodes = true)
  }

  @Test
  fun test1dRects() {
    val expectedTransforms = mapOf(
      ROOT to AffineTransform(0.995, 0.0, 0.0, 1.0, -64.749, -100.0),
      VIEW1 to AffineTransform(0.995, 0.0, 0.0, 1.0, -49.749, -100.0),
      VIEW3 to AffineTransform(0.995, 0.0, 0.0, 1.0, -34.749, -100.0),
      VIEW2 to AffineTransform(0.995, 0.0, 0.0, 1.0, -49.749, -100.0))

    checkRects(expectedTransforms, 0.1, 0.0)
  }

  @Test
  fun test1dRectsWithHiddenSystemNodes() {
    val expectedTransforms = mapOf(
      VIEW2 to AffineTransform(0.995, 0.0, 0.0, 1.0, -49.749, -100.0),
      VIEW3 to AffineTransform(0.995, 0.0, 0.0, 1.0, -49.749, -100.0))

    checkRects(expectedTransforms, 0.1, 0.0, hideSystemNodes = true)
  }

  @Test
  fun test2dRects() {
    val expectedTransforms = mapOf(
      ROOT to AffineTransform(0.995, -0.010, -0.010, 0.980, -63.734, -127.468),
      VIEW1 to AffineTransform(0.995, -0.010, -0.010, 0.980, -48.734, -97.468),
      VIEW2 to AffineTransform(0.995, -0.010, -0.010, 0.980, -48.734, -97.468),
      VIEW3 to AffineTransform(0.995, -0.010, -0.010, 0.980, -33.734, -67.468))

    checkRects(expectedTransforms, 0.1, 0.2)
  }

  @Test
  fun testOverlappingRects() {
    val rectMap = mapOf(
      ROOT to Rectangle(0, 0, 100, 100),
      VIEW1 to Rectangle(0, 0, 100, 50),
      VIEW2 to Rectangle(0, 0, 100, 50),
      VIEW3 to Rectangle(0, 50, 100, 50),
      VIEW4 to Rectangle(40, 40, 20, 20))

    val model = model {
      view(ROOT, rectMap[ROOT]) {
        view(VIEW1, rectMap[VIEW1]) {
          view(VIEW2, rectMap[VIEW2]) {
            image()
          }
        }
        view(VIEW3, rectMap[VIEW3])
        view(VIEW4, rectMap[VIEW4])
      }
    }

    val expectedTransforms = mapOf(
      ROOT to AffineTransform(0.866, 0.0, 0.0, 1.0, -193.301, -50.0),
      VIEW1 to AffineTransform(0.866, 0.0, 0.0, 1.0, -118.301, -50.0),
      VIEW2 to AffineTransform(0.866, 0.0, 0.0, 1.0, -43.301, -50.0),
      VIEW3 to AffineTransform(0.866, 0.0, 0.0, 1.0, -118.301, -50.0),
      VIEW4 to AffineTransform(0.866, 0.0, 0.0, 1.0, 31.698, -50.0)
    )

    checkModel(model, 0.5, 0.0, expectedTransforms, rectMap)
  }

  @Test
  fun testOverlappingRects2() {
    val rectMap = mapOf(
      ROOT to Rectangle(0, 0, 100, 100),
      VIEW1 to Rectangle(0, 0, 100, 100),
      VIEW2 to Rectangle(0, 0, 10, 10),
      VIEW3 to Rectangle(0, 0, 100, 100),
      VIEW4 to Rectangle(40, 40, 20, 20))

    val model = model {
      view(ROOT, rectMap[ROOT]) {
        view(VIEW1, rectMap[VIEW1]) {
          view(VIEW2, rectMap[VIEW2]) {
            image()
          }
        }
        view(VIEW3, rectMap[VIEW3]) {
          view(VIEW4, rectMap[VIEW4])
        }
      }
    }

    val expectedTransforms = mapOf(
      ROOT to AffineTransform(0.866, 0.0, 0.0, 1.0, -193.301, -50.0),
      VIEW1 to AffineTransform(0.866, 0.0, 0.0, 1.0, -118.301, -50.0),
      VIEW2 to AffineTransform(0.866, 0.0, 0.0, 1.0, -43.301, -50.0),
      VIEW3 to AffineTransform(0.866, 0.0, 0.0, 1.0, 31.699, -50.0),
      VIEW4 to AffineTransform(0.866, 0.0, 0.0, 1.0, 106.699, -50.0)
    )

    checkModel(model, 0.5, 0.0, expectedTransforms, rectMap)
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
    val treeSettings = FakeTreeSettings()
    treeSettings.hideSystemNodes = false
    val panelModel = RenderModel(model, treeSettings)
    panelModel.rotate(0.1, 0.2)
    assertEqualAffineTransform(AffineTransform(0.995, -0.010, -0.010, 0.980, -63.734, -127.468), panelModel.hitRects[0].transform)

    panelModel.resetRotation()
    assertEqualAffineTransform(AffineTransform(1.0, 0.0, 0.0, 1.0, -50.0, -100.0), panelModel.hitRects[0].transform)
  }

  @Test
  fun testRootBoundsUpdate() {
    val model = model {
      view(ROOT, Rectangle(0, 0, 100, 200)) {
        view(VIEW1, Rectangle(10, -10, 50, 100)) {
          image()
        }
        view(VIEW3, 20, 20, 10, 10)
      }
    }
    val window1 = window(ROOT2, ROOT2, -10, 0, 10, 10) {
      view(VIEW2, Rectangle(-10, 0, 10, 10)) {
        image()
      }
    }
    model.update(window1, listOf(ROOT, ROOT2), 0)

    val treeSettings = FakeTreeSettings()
    treeSettings.hideSystemNodes = false
    val panelModel = RenderModel(model, treeSettings)
    panelModel.rotate(0.1, 0.2)
    // Only the bounds of the roots themselves should be taken into account.
    assertThat(model.root.layoutBounds).isEqualTo(Rectangle(-10, 0, 110, 200))
    // ensure that nothing changes when we rotate more
    panelModel.rotate(0.1, 0.2)
    assertThat(model.root.layoutBounds).isEqualTo(Rectangle(-10, 0, 110, 200))
    // Show only a subtree and verify the bounds reduce
    model.showOnlySubtree(model[VIEW3]!!)
    assertThat(model.root.layoutBounds).isEqualTo(Rectangle(20, 20, 10, 10))
  }

  @Test
  fun testSwitchDevices() {
    val model = model {
      view(ROOT)
    }
    val treeSettings = FakeTreeSettings()
    val capabilities = mutableSetOf(InspectorClient.Capability.SUPPORTS_SKP)
    val client: InspectorClient = mock()
    whenever(client.capabilities).thenReturn(capabilities)

    val panelModel = RenderModel(model, treeSettings) { client }
    panelModel.rotate(0.1, 0.2)
    assertThat(panelModel.isRotated).isTrue()

    // Switch to a new model
    val model2 = view(VIEW3)

    model.update(FakeAndroidWindow(model2, VIEW3), listOf(VIEW3), 0)
    panelModel.refresh()

    assertThat(panelModel.isRotated).isTrue()

    capabilities.clear()
    // Update the view so the listener is fired
    val legacyModel = view(VIEW2)

    model.update(FakeAndroidWindow(legacyModel, VIEW2), listOf(VIEW2), 0)
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
    val treeSettings = FakeTreeSettings()
    var panelModel = RenderModel(model, treeSettings)
    // Note that coordinates are transformed to center the view, so (-45, -45) below corresponds to (5, 5)
    assertThat(panelModel.findViewsAt(-45.0, -45.0).map { it.drawId }.toList()).containsExactly(VIEW2, VIEW1, ROOT)
    assertThat(panelModel.findViewsAt(-1.0, -1.0).map { it.drawId }.toList()).containsExactly(ROOT)
    assertThat(panelModel.findViewsAt(10.0, 10.0).map { it.drawId }.toList()).containsExactly(VIEW3, ROOT)

    model = model {
      view(ROOT, 0, 0, 100, 100) {
        view(VIEW1, 0, 0, 100, 100) {
          view(VIEW2, 0, 0, 100, 100)
        }
        view(VIEW3, 0, 0, 100, 100)
      }
    }
    panelModel = RenderModel(model, treeSettings)
    assertThat(panelModel.findViewsAt(0.0, 0.0).map { it.drawId }.toList()).containsExactly(VIEW3, VIEW2, VIEW1, ROOT)
  }

  @Test
  fun testAllNodesInvisible() {
    val model = model {
      view(ROOT, Rectangle(0, 0, 100, 200)) {
        view(VIEW1, Rectangle(10, 10, 50, 100)) {
          view(VIEW2, 10, 10, 10, 10)
        }
        view(VIEW3, 50, 50, 20, 20)
      }
    }
    val treeSettings = FakeTreeSettings()
    val panelModel = RenderModel(model, treeSettings)
    panelModel.layerSpacing = 0
    model.showOnlySubtree(model[VIEW1]!!)
    model.hideSubtree(model[VIEW1]!!)
    panelModel.refresh()

    assertThat(model.root.layoutBounds.x).isEqualTo(0)
    assertThat(model.root.layoutBounds.y).isEqualTo(0)
    assertThat(model.root.layoutBounds.width).isEqualTo(0)
    assertThat(model.root.layoutBounds.height).isEqualTo(0)
    assertThat(panelModel.maxWidth)
  }

  @Test
  fun testFastIntersect() {
    val r1 = Rectangle(0, 0, 100, 200)
    val r2 = Rectangle(50, 50, 100, 200)
    val r3 = Rectangle(100, 200, 100, 200)
    val p1 = Polygon(intArrayOf(-5, 5, 80, 80), intArrayOf(5, -5, 80, 120), 4)
    val p2 = Polygon(intArrayOf(80, 120, 5, -5), intArrayOf(-5, 5, 20, 10), 4)
    val p3 = Polygon(intArrayOf(-5, 5, 80, 80), intArrayOf(200, 180, 380, 420), 4)
    val model = RenderModel(model {}, FakeTreeSettings())
    assertThat(model.testOverlap(r1, r2)).isTrue()
    assertThat(model.testOverlap(r1, r3)).isFalse()
    assertThat(model.testOverlap(p1, r1)).isTrue()
    assertThat(model.testOverlap(r1, p1)).isTrue()
    assertThat(model.testOverlap(p1, r3)).isFalse()
    assertThat(model.testOverlap(r3, p1)).isFalse()
    assertThat(model.testOverlap(p1, p2)).isTrue()
    assertThat(model.testOverlap(p1, p3)).isFalse()
  }

  private fun checkRects(expectedTransforms: Map<Long, AffineTransform>, xOff: Double, yOff: Double, hideSystemNodes: Boolean = false) {
    val rectMap = mapOf(
      ROOT to Rectangle(0, 0, 100, 200),
      VIEW1 to Rectangle(0, 0, 50, 60),
      VIEW2 to Rectangle(60, 70, 10, 10),
      VIEW3 to Rectangle(10, 20, 30, 40))

    @Suppress("MapGetWithNotNullAssertionOperator")
    val model = model {
      view(ROOT, rectMap[ROOT]!!, imageType = AndroidWindow.ImageType.SKP, layout = null) {
        view(VIEW1, rectMap[VIEW1]!!, layout = null) {
          view(VIEW3, rectMap[VIEW3]!!, layout = activityMain) {
            image()
          }
        }
        view(VIEW2, rectMap[VIEW2]!!, layout = activityMain)
      }
    }

    checkModel(model, xOff, yOff, expectedTransforms, rectMap, hideSystemNodes)
  }

  private fun checkModel(
    model: InspectorModel,
    xOff: Double,
    yOff: Double,
    expectedTransforms: Map<Long, AffineTransform>,
    rectMap: Map<Long, Rectangle>,
    hideSystemNodes: Boolean = false
  ) {
    val treeSettings = FakeTreeSettings()
    treeSettings.hideSystemNodes = hideSystemNodes
    val panelModel = RenderModel(model, treeSettings)
    panelModel.rotate(xOff, yOff)

    val actualTransforms = panelModel.hitRects.associate { it.node.findFilteredOwner(treeSettings)?.drawId to it.transform }
    assertThat(expectedTransforms.keys).containsExactlyElementsIn(expectedTransforms.keys)
    expectedTransforms.keys.forEach { assertEqualAffineTransform(expectedTransforms[it]!!, actualTransforms[it]!!) }

    panelModel.hitRects.associateBy { it.node.findFilteredOwner(treeSettings)?.drawId }.forEach { (drawId, info) ->
      assertPathEqual(actualTransforms[drawId]?.createTransformedShape(rectMap[drawId])!!, info.bounds)
    }
  }

  private fun assertPathEqual(expected: Shape, actual: Shape) {
    val expectedVals = DoubleArray(2)
    val actualVals = DoubleArray(2)
    val expectedIter = expected.getPathIterator(AffineTransform())
    val actualIter = actual.getPathIterator(AffineTransform())
    while (!expectedIter.isDone && !actualIter.isDone) {
      assertThat(actualIter.currentSegment(actualVals)).isEqualTo(expectedIter.currentSegment(expectedVals))
      assertThat(actualVals).usingTolerance(EPSILON).containsExactly(expectedVals)
      expectedIter.next()
      actualIter.next()
    }
    assertThat(expectedIter.isDone).isTrue()
    assertThat(actualIter.isDone).isTrue()
  }

  private fun assertEqualAffineTransform(expected: AffineTransform, actual: AffineTransform) {
    assertThat(actual.translateX).isWithin(EPSILON).of(expected.translateX)
    assertThat(actual.translateY).isWithin(EPSILON).of(expected.translateY)
    assertThat(actual.shearX).isWithin(EPSILON).of(expected.shearX)
    assertThat(actual.shearY).isWithin(EPSILON).of(expected.shearY)
    assertThat(actual.scaleX).isWithin(EPSILON).of(expected.scaleX)
    assertThat(actual.scaleX).isWithin(EPSILON).of(expected.scaleX)
  }
}
