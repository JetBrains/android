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

    assertEquals("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:height=\"702.0dp\" android:width=\"702.0dp\" android:viewportHeight=\"702.0\" android:viewportWidth=\"702.0\"><path android:name=\"Star Copy\" android:pathData=\"M351.420983772751,461.25 L242.38681947249728,518.5726524565528 L263.2104918863755,397.1613262282764 L175.0,311.1773475434473 L296.9039016226241,293.4636737717236 L351.420983772751,183.0 L405.93806592287785,293.4636737717236 L527.841967545502,311.17734754344724 L439.6314756591265,397.1613262282763 L460.4551480730048,518.5726524565528 C460.4551480730048,518.5726524565528 351.420983772751,461.25 351.420983772751,461.25 \" android:strokeColor=\"#ff827901\" android:strokeWidth=\"1\" android:fillColor=\"#fff8e71c\"/></vector>",
                 lightVirtualFileList[0].content)
    assertEquals("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:height=\"958.0dp\" android:width=\"958.0dp\" android:viewportHeight=\"958.0\" android:viewportWidth=\"958.0\"><path android:name=\"Star\" android:pathData=\"M483.0,529.0 L427.74818628450754,558.0475974712451 L438.30034373412775,496.52379873562256 L393.60068746825556,452.952402528755 L455.37409314225374,443.9762012643775 L483.0,388.0 L510.62590685774626,443.9762012643775 L572.3993125317445,452.95240252875493 L527.6996562658722,496.5237987356225 L538.2518137154925,558.047597471245 C538.2518137154925,558.047597471245 483.0,529.0 483.0,529.0 \" android:strokeColor=\"#ff5c0000\" android:strokeWidth=\"1\" android:fillColor=\"#ffff0000\"/></vector>",
                 lightVirtualFileList[1].content)
    assertEquals("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:height=\"730.0dp\" android:width=\"730.0dp\" android:viewportHeight=\"730.0\" android:viewportWidth=\"730.0\"><path android:name=\"Combined Shape\" android:pathData=\"M379.83578934099535,269.99999999999994 L388.33868849755777,292.42909328541066 L408.40116867212964,279.28145437504986 L409.556935328766,303.2403300395658 L432.7003316910417,296.9358297032246 L426.3958313547006,320.0792260655004 L450.35470701921645,321.23499272213667 L437.2070681088557,341.2974728967086 L459.6361613942664,349.800372053271 L440.9323664114189,364.81808069713315 L459.6361613942664,379.83578934099535 L437.20706810885576,388.33868849755777 L450.3547070192165,408.40116867212964 L426.3958313547006,409.556935328766 L432.70033169104175,432.7003316910417 L409.55693532876603,426.3958313547006 L408.40116867212976,450.35470701921645 L388.33868849755777,437.2070681088557 L379.83578934099535,459.6361613942664 C379.83578934099535,459.6361613942664 364.8180806971332,440.9323664114189 364.8180806971332,440.9323664114189 L349.800372053271,459.6361613942664 L341.29747289670865,437.20706810885576 L321.2349927221367,450.35470701921645 L320.0792260655004,426.3958313547006 L296.9358297032246,432.7003316910417 L303.2403300395658,409.55693532876603 L279.28145437504986,408.40116867212964 L292.42909328541066,388.33868849755777 L270.0,379.83578934099535 L288.7037949828475,364.8180806971332 L269.99999999999994,349.800372053271 L292.42909328541066,341.29747289670865 L279.28145437504986,321.2349927221367 L303.2403300395658,320.0792260655004 L296.9358297032246,296.93582970322467 L296.93582970322484,296.93582970322467 L320.0792260655004,303.2403300395658 L321.23499272213667,279.28145437504986 L341.2974728967086,292.42909328541066 L349.800372053271,270.0 L364.81808069713315,288.7037949828475 L379.83578934099535,269.99999999999994 zM331.21015555131004,151.65931843145023 L312.0786324490447,202.12477832362427 L266.93805205625785,172.54259077531253 L264.33757707882614,226.45006102047327 L212.26493526370578,212.26493526370575 L212.26493526370567,212.26493526370575 L226.45006102047327,264.3375770788262 L172.5425907753125,266.93805205625785 L202.12477832362424,312.07863244904473 L151.6593184314502,331.21015555131015 L193.74285714285713,364.99999999999994 L151.65931843145023,398.78984444868985 L202.12477832362427,417.92136755095527 L172.54259077531253,463.06194794374204 L226.45006102047327,465.6624229211738 L212.26493526370575,517.735064736294 L212.26493526370615,517.735064736294 L264.3375770788262,503.54993897952664 L266.93805205625785,557.4574092246874 L312.07863244904473,527.8752216763758 L331.21015555131015,578.3406815685498 L364.99999999999994,536.2571428571428 C364.99999999999994,536.2571428571428 398.7898444486899,578.3406815685498 398.7898444486899,578.3406815685498 L417.92136755095527,527.8752216763758 L463.06194794374215,557.4574092246874 L465.6624229211738,503.54993897952664 L517.7350647362944,517.7350647362941 L503.54993897952664,465.6624229211738 L557.4574092246874,463.06194794374204 L527.8752216763758,417.92136755095515 L578.3406815685498,398.78984444868985 L536.2571428571428,364.99999999999994 L578.3406815685498,331.21015555131004 L527.8752216763758,312.0786324490447 L557.4574092246874,266.93805205625785 L503.54993897952664,264.33757707882614 L517.7350647362941,212.2649352637057 L517.735064736294,212.2649352637057 L465.6624229211738,226.45006102047324 L463.06194794374204,172.54259077531248 L417.92136755095515,202.12477832362424 L398.78984444868985,151.65931843145023 L364.99999999999994,193.74285714285713 L331.21015555131004,151.65931843145023 z\" android:fillColor=\"#ff7ed321\"/></vector>",
                 lightVirtualFileList[2].content)
  }


}