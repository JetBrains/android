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

import com.android.tools.idea.resourceExplorer.sketchImporter.parser.SketchParser
import org.jetbrains.android.AndroidTestBase
import org.junit.Test
import kotlin.test.assertEquals

class ModelTest {
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
}