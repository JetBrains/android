/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.editors.layoutInspector.ui

import com.android.tools.idea.editors.layoutInspector.LayoutFileData
import com.android.tools.idea.editors.layoutInspector.LayoutInspectorContext
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.android.AndroidTestCase
import org.junit.Test
import java.nio.file.Paths

/**
 * Test Pixel Pefect features in layout inspector
 */
class PixelPerfectTest : AndroidTestCase() {

  private lateinit var myContext: LayoutInspectorContext
  private lateinit var myPanel: LayoutInspectorPanel
  private lateinit var myPreview: ViewNodeActiveDisplay
  private lateinit var myTestData: LayoutFileData

  override fun setUp() {
    super.setUp()
    val testFile = Paths.get(AndroidTestBase.getTestDataPath(), "editors/layoutInspector/LayoutCapture.li").toFile()
    val layoutFile = LocalFileSystem.getInstance().findFileByIoFile(testFile)
    myTestData = LayoutFileData(layoutFile!!)

    myContext = LayoutInspectorContext(myTestData, project)
    myPanel = LayoutInspectorPanel(myContext)
    myPanel.setSize(800, 800)
    myPreview = myPanel.preview
  }

  @Test
  fun testSetAndCancelOverlay() {
    assertFalse(myPreview.hasOverlay())

    myPreview.setOverLay(myTestData.bufferedImage, "example.png")

    assertTrue(myPreview.hasOverlay())
    assertEquals(myPreview.overlayFileName, "example.png")

    myPreview.setOverLay(null, null)

    assertFalse(myPreview.hasOverlay())
  }

  @Test
  fun testSetAlpha() {
    assertEquals(myPreview.overlayAlpha, ViewNodeActiveDisplay.DEFAULT_OVERLAY_ALPHA)

    myPreview.overlayAlpha = 0.1f

    assertEquals(myPreview.overlayAlpha, 0.1f)

    // test alpha is reset on selecting image
    myPreview.setOverLay(myTestData.bufferedImage, "example.png")

    assertEquals(myPreview.overlayAlpha, ViewNodeActiveDisplay.DEFAULT_OVERLAY_ALPHA)
  }
}
