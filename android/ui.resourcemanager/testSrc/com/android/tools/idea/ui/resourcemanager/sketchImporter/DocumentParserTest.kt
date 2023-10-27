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

import org.jetbrains.android.AndroidTestBase
import org.junit.Test
import java.awt.Color
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentParserTest {
  @Test
  fun checkParsedAssets() {
    val document: com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.document.SketchDocument = SketchTestUtils.parseDocument(
      AndroidTestBase.getTestDataPath() + "/sketch/document.json")

    val assets = document.assets
    val colors = assets.colors

    assertEquals(4, colors.size)
    assertEquals(Color(26, 115, 232, 255), colors[0])
    assertEquals(Color(217, 48, 37, 255), colors[1])
    assertEquals(Color(227, 116, 0, 255), colors[2])
    assertEquals(Color(30, 142, 62, 255), colors[3])

    val gradients = assets.gradients
    assertEquals(0, gradients.size)

    // TODO images when implemented in the class
  }

  @Test
  fun checkForeignLayerStyles() {
    val document: com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.document.SketchDocument = SketchTestUtils.parseDocument(
      AndroidTestBase.getTestDataPath() + "/sketch/document.json")

    val styles = document.foreignLayerStyles
    assertEquals(0, styles?.size)

    // TODO when we get a better test file
  }

  @Test
  fun checkForeignSymbols() {
    val document: com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.document.SketchDocument = SketchTestUtils.parseDocument(
      AndroidTestBase.getTestDataPath() + "/sketch/document.json")

    val symbols = document.foreignSymbols
    assertEquals(74, symbols?.size)

    val symbol = symbols?.get(0)
    assertEquals("A9567D7E-AEAA-40A8-8DFD-4B03978DD172", symbol?.libraryId)
    assertEquals("google-material-icons", symbol?.sourceLibraryName)

    val originalMaster = symbol?.originalMaster
    assertEquals("Styles/1. Color/1. Grey/700  ✔", originalMaster?.name)

    val originalShapeGroup = originalMaster?.layers?.get(0)
    assertTrue(originalShapeGroup is com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchShapeGroup)

    val originalRectangle = originalShapeGroup.layers[0]
    assertTrue(originalRectangle is com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchShapePath)
    assertEquals("rectangle", originalRectangle.classType)

    val symbolMaster = symbol.symbolMaster
    assertEquals("Styles/1. Color/1. Grey/700  ✔", symbolMaster.name)

    val shapeGroup = symbolMaster.layers[0]
    assertTrue(shapeGroup is com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchShapeGroup)

    val rectangle = shapeGroup.layers[0]
    assertTrue(rectangle is com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchShapePath)
    assertEquals("rectangle", rectangle.classType)
  }

  @Test
  fun checkForeignTextStyles() {
    val document: com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.document.SketchDocument = SketchTestUtils.parseDocument(
      AndroidTestBase.getTestDataPath() + "/sketch/document.json")

    val styles = document.foreignTextStyles
    assertEquals(0, styles?.size)

    // TODO when we get a better test file
  }

  @Test
  fun checkLayerStyles() {
    val document: com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.document.SketchDocument = SketchTestUtils.parseDocument(
      AndroidTestBase.getTestDataPath() + "/sketch/document.json")

    val styles = document.layerStyles
    assertEquals(173, styles.size)

    val style = styles[0]
    assertEquals("1. Light Theme/1. Color/3. Extended/4. Cyan/900", style.name)
    assertEquals(Color(1, 135, 116, 255), style.value.fills[0]!!.color)
  }

  @Test
  fun checkLayerSymbols() {
    val document: com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.document.SketchDocument = SketchTestUtils.parseDocument(
      AndroidTestBase.getTestDataPath() + "/sketch/document.json")

    val symbols = document.layerSymbols
    assertEquals(0, symbols.size)

    // TODO when we get a better test file
  }

  @Test
  fun checkLayerTextStyles() {
    val document: com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.document.SketchDocument = SketchTestUtils.parseDocument(
      AndroidTestBase.getTestDataPath() + "/sketch/document.json")

    val styles = document.layerTextStyles
    assertEquals(1439, styles.size)

    val style = styles[0]
    assertEquals("02 - Caption/2. Color/1. Brand/2. Red/1. Left/1. Regular", style.name)
    // TODO after creating TextStyle
  }
}