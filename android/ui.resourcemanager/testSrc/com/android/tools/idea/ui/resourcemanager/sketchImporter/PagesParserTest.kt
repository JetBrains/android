/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.sketchImporter

import com.intellij.testFramework.ProjectRule
import org.jetbrains.android.AndroidTestBase
import org.junit.Rule
import org.junit.Test
import java.awt.Color
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PagesParserTest {
  @get:Rule
  val projectRule = ProjectRule()

  @Test
  fun checkParsedPageData() {
    val page: com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchPage = SketchTestUtils.parsePage(
      AndroidTestBase.getTestDataPath() + "/sketch/simple.json")

    assertEquals("page", page.classType)
    assertEquals("4A20F10B-61D2-4A1B-8BF1-623ACF2E7637", page.objectId)
    assertEquals(-1, page.booleanOperation)

    assertEquals(0.0, page.frame.x)
    assertEquals(0.0, page.frame.y)
    assertEquals(0.0, page.frame.height)
    assertEquals(0.0, page.frame.width)

    assertEquals(false, page.isFlippedHorizontal)
    assertEquals(false, page.isFlippedVertical)
    assertEquals(true, page.isVisible)
    assertEquals("Page 1", page.name)
    assertEquals(0, page.rotation)
    assertEquals(false, page.shouldBreakMaskChain())

    assertEquals(10, page.style.miterLimit)
    assertEquals(1, page.style.windingRule)


    assertEquals(2, page.layers.size)
  }

  @Test
  fun checkParsedSliceData() {
    val page: com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchPage = SketchTestUtils.parsePage(
      AndroidTestBase.getTestDataPath() + "/sketch/simple.json")

    assertTrue(page.layers[0] is com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchSlice)
    val slice = page.layers[0] as com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchSlice

    assertEquals("slice", slice.classType)
    assertEquals("6B43960B-6A5B-421A-90DD-3112EEEF2DE5", slice.objectId)
    assertEquals(-1, slice.booleanOperation)

    assertEquals(82.0, slice.frame.height)
    assertEquals(290.0, slice.frame.width)
    assertEquals(139.0, slice.frame.x)
    assertEquals(190.0, slice.frame.y)

    assertEquals(false, slice.isFlippedHorizontal)
    assertEquals(false, slice.isFlippedVertical)
    assertEquals(true, slice.isVisible)
    assertEquals("Slice 1", slice.name)
    assertEquals(0, slice.rotation)
    assertEquals(false, slice.shouldBreakMaskChain())

    assertEquals(Color(255, 255, 255, 255), slice.backgroundColor)
    assertEquals(false, slice.hasBackgroundColor())

    assertEquals("ffffffff", Integer.toHexString(slice.backgroundColor.rgb))
  }

  @Test
  fun checkParsedShapeGroupData() {
    val page: com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchPage = SketchTestUtils.parsePage(
      AndroidTestBase.getTestDataPath() + "/sketch/simple.json")

    assertTrue(page.layers[1] is com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchShapeGroup)
    val shapeGroup = page.layers[1] as com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchShapeGroup

    assertEquals("shapeGroup", shapeGroup.classType)
    assertEquals("4C70C207-138B-4C9A-BE3F-00F33611627C", shapeGroup.objectId)
    assertEquals(-1, shapeGroup.booleanOperation)

    assertEquals(80.0, shapeGroup.frame.height)
    assertEquals(288.0, shapeGroup.frame.width)
    assertEquals(140.0, shapeGroup.frame.x)
    assertEquals(191.0, shapeGroup.frame.y)

    assertEquals(false, shapeGroup.isFlippedHorizontal)
    assertEquals(false, shapeGroup.isFlippedVertical)
    assertEquals(true, shapeGroup.isVisible)
    assertEquals("Line", shapeGroup.name)
    assertEquals(0, shapeGroup.rotation)
    assertEquals(false, shapeGroup.shouldBreakMaskChain())

    assertEquals(0, shapeGroup.clippingMaskMode)
    assertEquals(false, shapeGroup.hasClippingMask())
    assertEquals(1, shapeGroup.windingRule)
  }

  @Test
  fun checkStyleData() {
    val page: com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchPage = SketchTestUtils.parsePage(
      AndroidTestBase.getTestDataPath() + "/sketch/simple.json")

    assertTrue(page.layers[1] is com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchShapeGroup)
    val style = (page.layers[1] as com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchShapeGroup).style

    assertEquals(false, style.borderOptions?.isEnabled)
    assertEquals(2, style.borderOptions?.lineCapStyle)
    assertEquals(0, style.borderOptions?.lineJoinStyle)

    assertEquals(true, style.borders?.get(0)?.isEnabled)
    assertEquals(Color(151, 151, 151, 255), style.borders?.get(0)?.color)
    assertEquals(0, style.borders?.get(0)?.fillType)
    assertEquals(0, style.borders?.get(0)?.position)
    assertEquals(1, style.borders?.get(0)?.thickness)

    assertEquals(false, style.fills?.get(0)?.isEnabled)
    assertEquals(Color(216, 216, 216, 255), style.fills?.get(0)?.color)
    assertEquals(0, style.fills?.get(0)?.fillType)
  }

  @Test
  fun checkShapePathData() {
    val page: com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchPage = SketchTestUtils.parsePage(
      AndroidTestBase.getTestDataPath() + "/sketch/simple.json")

    assertTrue(page.layers[1] is com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchShapeGroup)
    assertTrue((page.layers[1] as com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchShapeGroup).layers[0] is com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchShapePath)
    val shapePath = (page.layers[1] as com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchShapeGroup).layers[0] as com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchShapePath

    assertEquals("shapePath", shapePath.classType)
    assertEquals("9CE12CC7-66A6-46DD-BA01-79B52634AF04", shapePath.objectId)
    assertEquals(-1, shapePath.booleanOperation)

    assertEquals(80.0, shapePath.frame.height)
    assertEquals(288.0, shapePath.frame.width)
    assertEquals(0.0, shapePath.frame.x)
    assertEquals(0.0, shapePath.frame.y)

    assertEquals(false, shapePath.isFlippedHorizontal)
    assertEquals(false, shapePath.isFlippedVertical)
    assertEquals(true, shapePath.isVisible)
    assertEquals("Path", shapePath.name)
    assertEquals(0, shapePath.rotation)
    assertEquals(false, shapePath.shouldBreakMaskChain())

    assertEquals(false, shapePath.isClosed)
  }

  @Test
  fun checkPointsData() {
    val page: com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchPage = SketchTestUtils.parsePage(
      AndroidTestBase.getTestDataPath() + "/sketch/simple.json")

    assertTrue(page.layers[1] is com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchShapeGroup)
    assertTrue((page.layers[1] as com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchShapeGroup).layers[0] is com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchShapePath)
    val points = ((page.layers[1] as com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchShapeGroup).layers[0] as com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchShapePath).points

    assertEquals(0, points[0].cornerRadius)
    assertEquals(com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchPoint2D(0.0034722222222222246, 1.0), points[0].curveFrom)
    assertEquals(1.0, points[0].curveFrom.y)
    assertEquals(1, points[0].curveMode)
    assertEquals(com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchPoint2D(0.0034722222222222246, 1.0), points[0].curveFrom)
    assertEquals(false, points[0].hasCurveFrom())
    assertEquals(false, points[0].hasCurveTo())
    assertEquals(
      com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchPoint2D(0.0017361111111111123, 0.99374999999999969), points[0].point)

    assertEquals(0, points[1].cornerRadius)
    assertEquals(
      com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchPoint2D(0.0069444444444444493, 1.0125000000000002), points[1].curveFrom)
    assertEquals(1, points[1].curveMode)
    assertEquals(
      com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchPoint2D(0.0069444444444444493, 1.0125000000000002), points[1].curveFrom)
    assertEquals(false, points[1].hasCurveFrom())
    assertEquals(false, points[1].hasCurveTo())
    assertEquals(
      com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchPoint2D(0.99826388888888884, 0.0062500000000000003), points[1].point)
  }
}