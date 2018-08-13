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
package com.android.tools.idea.resourceExplorer.sketchImporter

import com.android.tools.idea.resourceExplorer.sketchImporter.presenter.SketchParser
import com.android.tools.idea.resourceExplorer.sketchImporter.presenter.SketchParser.generateFiles
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.*
import com.intellij.testFramework.ProjectRule
import org.jetbrains.android.AndroidTestBase
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParserTest {

  @get:Rule
  val projectRule = ProjectRule()

  @Test
  fun findLayers() {
    val sketchFile = SketchParser.read(AndroidTestBase.getTestDataPath() + "/sketch/palette.sketch")!!

    // First page
    assertEquals(sketchFile.findLayer("7D779FEF-7EA8-45AF-AA97-04E803E773F7")?.classType, "rectangle")
    assertEquals(sketchFile.findLayer("CD4A49FD-0A18-4059-B493-5C2DC9F8F386")?.classType, "page")

    // Second page
    assertEquals(sketchFile.findLayer("E107408D-96BD-4B27-A124-6A84069917FB")?.classType, "artboard")
    assertEquals(sketchFile.findLayer("11B6C0F9-CE36-4365-8D66-AEF88B697CCD")?.classType, "page")
  }

  @Test
  fun findSymbols() {
    val sketchFile = SketchParser.read(AndroidTestBase.getTestDataPath() + "/sketch/palette.sketch")!!

    // First page
    assertEquals(sketchFile.findSymbol("3BDBDFC1-CDA3-4C7A-B70A-990DFAF1290C")?.frame?.height, 12.0)
    assertEquals(sketchFile.findSymbol("3BDBDFC1-CDA3-4C7A-B70A-990DFAF1290C")?.frame?.width, 14.0)

    // Second page
    assertEquals(sketchFile.findSymbol("E052FD96-0724-47EA-B608-D4491709F803")?.name, "text_dark")
    assertEquals(sketchFile.findSymbol("E052FD96-0724-47EA-B608-D4491709F803")?.classType, "symbolMaster")
  }

  @Test
  fun parseSketchFiles() {
    val sketchFile = SketchParser.read(AndroidTestBase.getTestDataPath() + "/sketch/palette.sketch")!!

    assertEquals("New Palette", sketchFile.pages[0]?.name)
    assertEquals("Symbols", sketchFile.pages[1]?.name)
  }

  @Test
  fun checkParsedPageData() {
    val page: SketchPage = SketchParser.parsePage(AndroidTestBase.getTestDataPath() + "/sketch/simple.json")!!

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
    val page: SketchPage = SketchParser.parsePage(AndroidTestBase.getTestDataPath() + "/sketch/simple.json")!!

    assertTrue(page.layers[0] is SketchSlice)
    val slice = page.layers[0] as SketchSlice

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

    assertEquals(255, slice.backgroundColor.alpha)
    assertEquals(255, slice.backgroundColor.blue)
    assertEquals(255, slice.backgroundColor.green)
    assertEquals(255, slice.backgroundColor.red)
    assertEquals(false, slice.hasBackgroundColor())

    assertEquals("ffffffff", Integer.toHexString(slice.backgroundColor.rgb))
  }

  @Test
  fun checkParsedShapeGroupData() {
    val page: SketchPage = SketchParser.parsePage(AndroidTestBase.getTestDataPath() + "/sketch/simple.json")!!

    assertTrue(page.layers[1] is SketchShapeGroup)
    val shapeGroup = page.layers[1] as SketchShapeGroup

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
    val page: SketchPage = SketchParser.parsePage(AndroidTestBase.getTestDataPath() + "/sketch/simple.json")!!

    assertTrue(page.layers[1] is SketchShapeGroup)
    val style = (page.layers[1] as SketchShapeGroup).style

    assertEquals(false, style.borderOptions?.isEnabled)
    assertEquals(2, style.borderOptions?.lineCapStyle)
    assertEquals(0, style.borderOptions?.lineJoinStyle)

    assertEquals(true, style.borders?.get(0)?.isEnabled)
    assertEquals(255, style.borders?.get(0)?.color?.alpha)
    assertEquals(151, style.borders?.get(0)?.color?.blue)
    assertEquals(151, style.borders?.get(0)?.color?.green)
    assertEquals(151, style.borders?.get(0)?.color?.red)
    assertEquals(0, style.borders?.get(0)?.fillType)
    assertEquals(0, style.borders?.get(0)?.position)
    assertEquals(1, style.borders?.get(0)?.thickness)

    assertEquals(false, style.fills?.get(0)?.isEnabled)
    assertEquals(255, style.fills?.get(0)?.color?.alpha)
    assertEquals(216, style.fills?.get(0)?.color?.blue)
    assertEquals(216, style.fills?.get(0)?.color?.green)
    assertEquals(216, style.fills?.get(0)?.color?.red)
    assertEquals(0, style.fills?.get(0)?.fillType)
  }

  @Test
  fun checkShapePathData() {
    val page: SketchPage = SketchParser.parsePage(AndroidTestBase.getTestDataPath() + "/sketch/simple.json")!!

    assertTrue(page.layers[1] is SketchShapeGroup)
    assertTrue((page.layers[1] as SketchShapeGroup).layers[0] is SketchShapePath)
    val shapePath = (page.layers[1] as SketchShapeGroup).layers[0] as SketchShapePath

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
    val page: SketchPage = SketchParser.parsePage(AndroidTestBase.getTestDataPath() + "/sketch/simple.json")!!

    assertTrue(page.layers[1] is SketchShapeGroup)
    assertTrue((page.layers[1] as SketchShapeGroup).layers[0] is SketchShapePath)
    val points = ((page.layers[1] as SketchShapeGroup).layers[0] as SketchShapePath).points

    assertEquals(0, points[0].cornerRadius)
    assertEquals(SketchPoint2D(0.0034722222222222246, 1.0), points[0].curveFrom)
    assertEquals(1.0, points[0].curveFrom.y)
    assertEquals(1, points[0].curveMode)
    assertEquals(SketchPoint2D(0.0034722222222222246, 1.0), points[0].curveFrom)
    assertEquals(false, points[0].hasCurveFrom())
    assertEquals(false, points[0].hasCurveTo())
    assertEquals(SketchPoint2D(0.0017361111111111123, 0.99374999999999969), points[0].point)

    assertEquals(0, points[1].cornerRadius)
    assertEquals(SketchPoint2D(0.0069444444444444493, 1.0125000000000002), points[1].curveFrom)
    assertEquals(1, points[1].curveMode)
    assertEquals(SketchPoint2D(0.0069444444444444493, 1.0125000000000002), points[1].curveFrom)
    assertEquals(false, points[1].hasCurveFrom())
    assertEquals(false, points[1].hasCurveTo())
    assertEquals(SketchPoint2D(0.99826388888888884, 0.0062500000000000003), points[1].point)
  }

  @Test
  fun generateFilesTest(){
    val sketchFile = SketchParser.read(AndroidTestBase.getTestDataPath() + "/sketch/parser_generateFiles.sketch")!!
    val lightVirtualFileList = generateFiles(sketchFile, projectRule.project)

    assertEquals("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:height=\"702.0dp\" android:width=\"702.0dp\" android:viewportHeight=\"702.0\" android:viewportWidth=\"702.0\"><path android:name=\"Star Copy\" android:pathData=\"M351.42,461.25 L242.39,518.57 L263.21,397.16 L175,311.18 L296.9,293.46 L351.42,183 L405.94,293.46 L527.84,311.18 L439.63,397.16 L460.46,518.57 C460.46,518.57 351.42,461.25 351.42,461.25 \" android:strokeColor=\"#ff827901\" android:strokeWidth=\"1\" android:fillColor=\"#fff8e71c\"/></vector>",
                 lightVirtualFileList[0].content)
    assertEquals("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:height=\"958.0dp\" android:width=\"958.0dp\" android:viewportHeight=\"958.0\" android:viewportWidth=\"958.0\"><path android:name=\"Star\" android:pathData=\"M483,529 L427.75,558.05 L438.3,496.52 L393.6,452.95 L455.37,443.98 L483,388 L510.63,443.98 L572.4,452.95 L527.7,496.52 L538.25,558.05 C538.25,558.05 483,529 483,529 \" android:strokeColor=\"#ff5c0000\" android:strokeWidth=\"1\" android:fillColor=\"#ffff0000\"/></vector>",
                 lightVirtualFileList[1].content)
    assertEquals("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:height=\"730.0dp\" android:width=\"730.0dp\" android:viewportHeight=\"730.0\" android:viewportWidth=\"730.0\"><path android:name=\"Combined Shape\" android:pathData=\"M379.84,270 L388.34,292.43 L408.4,279.28 L409.56,303.24 L432.7,296.94 L426.4,320.08 L450.35,321.23 L437.21,341.3 L459.64,349.8 L440.93,364.82 L459.64,379.84 L437.21,388.34 L450.35,408.4 L426.4,409.56 L432.7,432.7 L409.56,426.4 L408.4,450.35 L388.34,437.21 L379.84,459.64 C379.84,459.64 364.82,440.93 364.82,440.93 L349.8,459.64 L341.3,437.21 L321.23,450.35 L320.08,426.4 L296.94,432.7 L303.24,409.56 L279.28,408.4 L292.43,388.34 L270,379.84 L288.7,364.82 L270,349.8 L292.43,341.3 L279.28,321.23 L303.24,320.08 L296.94,296.94 L296.94,296.94 L320.08,303.24 L321.23,279.28 L341.3,292.43 L349.8,270 L364.82,288.7 L379.84,270 zM331.21,151.66 L312.08,202.12 L266.94,172.54 L264.34,226.45 L212.26,212.26 L212.26,212.26 L226.45,264.34 L172.54,266.94 L202.12,312.08 L151.66,331.21 L193.74,365 L151.66,398.79 L202.12,417.92 L172.54,463.06 L226.45,465.66 L212.26,517.74 L212.26,517.74 L264.34,503.55 L266.94,557.46 L312.08,527.88 L331.21,578.34 L365,536.26 C365,536.26 398.79,578.34 398.79,578.34 L417.92,527.88 L463.06,557.46 L465.66,503.55 L517.74,517.74 L503.55,465.66 L557.46,463.06 L527.88,417.92 L578.34,398.79 L536.26,365 L578.34,331.21 L527.88,312.08 L557.46,266.94 L503.55,264.34 L517.74,212.26 L517.74,212.26 L465.66,226.45 L463.06,172.54 L417.92,202.12 L398.79,151.66 L365,193.74 L331.21,151.66 z\" android:fillColor=\"#ff7ed321\"/></vector>",
                 lightVirtualFileList[2].content)
  }


}