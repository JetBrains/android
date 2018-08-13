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
package com.android.tools.idea.resourceExplorer.sketchImporter;

import com.android.tools.idea.resourceExplorer.sketchImporter.model.ImportOptions
import com.android.tools.idea.resourceExplorer.sketchImporter.model.PageOptions
import com.android.tools.idea.resourceExplorer.sketchImporter.presenter.SketchParser
import org.jetbrains.android.AndroidTestBase
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelTest {
  @Test
  fun defaultOptions() {
    val sketchFile = SketchParser.read(AndroidTestBase.getTestDataPath() + "/sketch/palette.sketch")!!

    val options = ImportOptions(sketchFile)

    // Symbols page
    val pageOptions1 = options.getPageOptions("CD4A49FD-0A18-4059-B493-5C2DC9F8F386")
    assertEquals("Symbols", sketchFile.findLayer("CD4A49FD-0A18-4059-B493-5C2DC9F8F386")?.name)
    assertEquals(PageOptions.PageType.SYMBOLS, pageOptions1?.pageType)

    // Default-type page
    val pageOptions2 = options.getPageOptions("11B6C0F9-CE36-4365-8D66-AEF88B697CCD")
    assertEquals("New Palette", sketchFile.findLayer("11B6C0F9-CE36-4365-8D66-AEF88B697CCD")?.name)
    assertEquals(PageOptions.DEFAULT_PAGE_TYPE, pageOptions2?.pageType)

    // Artboard (Icon)
    val iconOptions = options.getIconOptions("E107408D-96BD-4B27-A124-6A84069917FB")
    assertTrue(iconOptions?.isExportable ?: false)

    // Layer that's in the file but is not a page/artboard
    assertNull(options.getIconOptions("7D779FEF-7EA8-45AF-AA97-04E803E773F7"))
    assertNull(options.getPageOptions("7D779FEF-7EA8-45AF-AA97-04E803E773F7"))
  }
}
