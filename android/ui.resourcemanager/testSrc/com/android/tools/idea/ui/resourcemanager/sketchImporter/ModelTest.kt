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
import kotlin.test.assertEquals

class ModelTest {
  @Test
  fun findLayers() {
    val sketchFile = com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.SketchParser.read(AndroidTestBase.getTestDataPath() + "/sketch/palette.sketch")!!

    // First page
    assertEquals(sketchFile.findLayer("7D779FEF-7EA8-45AF-AA97-04E803E773F7")?.classType, "rectangle")
    assertEquals(sketchFile.findLayer("CD4A49FD-0A18-4059-B493-5C2DC9F8F386")?.classType, "page")

    // Second page
    assertEquals(sketchFile.findLayer("E107408D-96BD-4B27-A124-6A84069917FB")?.classType, "artboard")
    assertEquals(sketchFile.findLayer("11B6C0F9-CE36-4365-8D66-AEF88B697CCD")?.classType, "page")
  }

  @Test
  fun findSymbols() {
    val sketchFile = com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.SketchParser.read(AndroidTestBase.getTestDataPath() + "/sketch/palette.sketch")!!

    // First page
    assertEquals(sketchFile.findSymbol("3BDBDFC1-CDA3-4C7A-B70A-990DFAF1290C")?.frame?.height, 12.0)
    assertEquals(sketchFile.findSymbol("3BDBDFC1-CDA3-4C7A-B70A-990DFAF1290C")?.frame?.width, 14.0)

    // Second page
    assertEquals(sketchFile.findSymbol("E052FD96-0724-47EA-B608-D4491709F803")?.name, "text_dark")
    assertEquals(sketchFile.findSymbol("E052FD96-0724-47EA-B608-D4491709F803")?.classType, "symbolMaster")
  }
}

/**
 * Recursively search through all pages in the file for the layer with the corresponding `objectId`.
 *
 * @return the found layer or `null` if no layer was found
 */
private fun com.android.tools.idea.ui.resourcemanager.sketchImporter.ui.SketchFile.findLayer(objectId: String): com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces.SketchLayer? {
  for (page in pages) {
    val foundLayer = findLayer(objectId, page)
    if (foundLayer != null) {
      return foundLayer
    }
  }

  return null
}

/**
 * Recursively search through all pages in the file for the symbol with the corresponding `symbolId`.
 *
 * @return the found symbol or `null` if no layer was found
 */
private fun com.android.tools.idea.ui.resourcemanager.sketchImporter.ui.SketchFile.findSymbol(symbolId: String): com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces.SketchSymbol? {
  for (page in pages) {
    val foundSymbol = findSymbol(symbolId, page)
    if (foundSymbol != null) {
      return foundSymbol
    }
  }

  return null
}

/**
 * Recursively search for the layer with the corresponding `objectId` starting at `currentLayer`.
 *
 * @return the found layer or `null` if no layer was found
 */
private fun findLayer(objectId: String, currentLayer: com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces.SketchLayer): com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces.SketchLayer? {
  if (currentLayer.objectId == objectId) {
    return currentLayer
  }

  if (currentLayer is com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces.SketchLayerable) {
    for (layer in (currentLayer as com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces.SketchLayerable).layers) {
      val foundLayer = findLayer(objectId, layer)
      if (foundLayer != null) {
        return foundLayer
      }
    }
  }

  return null
}

/**
 * Recursively search for the symbol with the corresponding `symbolId` starting at `currentLayer`.
 *
 * @return the found symbol or `null` if no layer was found
 */
private fun findSymbol(symbolId: String, currentLayer: com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces.SketchLayer): com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces.SketchSymbol? {
  if (currentLayer is com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces.SketchSymbol) {
    if (currentLayer.symbolId == symbolId) {
      return currentLayer
    }
  }

  if (currentLayer is com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces.SketchLayerable) {
    for (layer in (currentLayer as com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces.SketchLayerable).layers) {
      val foundSymbol = findSymbol(symbolId, layer)
      if (foundSymbol != null) {
        return foundSymbol
      }
    }
  }

  return null
}
