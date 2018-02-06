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

import com.android.tools.idea.editors.layoutInspector.getTestFile
import com.google.common.truth.Truth
import org.jetbrains.android.AndroidTestCase
import org.junit.Test

/**
 * Test Pixel Pefect features in layout inspector
 */
class PixelPerfectTest : AndroidTestCase() {

  private lateinit var myContext: com.android.tools.idea.editors.layoutInspector.LayoutInspectorContext
  private lateinit var myPanel: com.android.tools.idea.editors.layoutInspector.ui.LayoutInspectorPanel
  private lateinit var myPreview: com.android.tools.idea.editors.layoutInspector.ui.ViewNodeActiveDisplay
  private lateinit var myTestData: com.android.tools.idea.editors.layoutInspector.LayoutFileData

  override fun setUp() {
    super.setUp()
    val file = getTestFile()
    myTestData = com.android.tools.idea.editors.layoutInspector.LayoutFileData(file)

    myContext = com.android.tools.idea.editors.layoutInspector.LayoutInspectorContext(myTestData, project)
    myPanel = com.android.tools.idea.editors.layoutInspector.ui.LayoutInspectorPanel(myContext)
    myPanel.setSize(800, 800)
    myPreview = myPanel.preview
  }

  @Test
  fun testSetAndCancelOverlay() {
    Truth.assertThat(!myPreview.hasOverlay())

    myPreview.setOverLay(myTestData.bufferedImage, "example.png")

    Truth.assertThat(myPreview.hasOverlay())
    Truth.assertThat(myPreview.overlayFileName.equals("example.png"))

    myPreview.setOverLay(null, null)

    Truth.assertThat(!myPreview.hasOverlay())
  }

  @Test
  fun testSetAlpha() {
    Truth.assertThat(myPreview.overlayAlpha.equals(com.android.tools.idea.editors.layoutInspector.ui.ViewNodeActiveDisplay.DEFAULT_OVERLAY_ALPHA))

    myPreview.overlayAlpha = 0.1f

    Truth.assertThat(myPreview.overlayAlpha.equals(0.1f))

    // test alpha is reset on selecting image
    myPreview.setOverLay(myTestData.bufferedImage, "example.png")

    Truth.assertThat(myPreview.overlayAlpha.equals(com.android.tools.idea.editors.layoutInspector.ui.ViewNodeActiveDisplay.DEFAULT_OVERLAY_ALPHA))
  }
}
