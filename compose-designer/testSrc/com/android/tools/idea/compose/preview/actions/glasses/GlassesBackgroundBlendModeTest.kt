/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.actions.glasses

import com.android.testutils.ImageDiffUtil
import com.android.testutils.TestUtils
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.util.ui.JBUI
import java.awt.image.BufferedImage
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class GlassesBackgroundBlendModeTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  /** Generates a background with different rectangular colors to be used in tests. */
  private fun createTestBackground(width: Int, height: Int): BufferedImage {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g2d = image.createGraphics()
    g2d.color = java.awt.Color.RED
    g2d.fillRect(0, 0, width / 2, height)
    g2d.color = java.awt.Color.GREEN
    g2d.fillRect(width / 2, 0, width, height)
    g2d.dispose()
    return image
  }

  private fun createTestForeground(width: Int, height: Int): BufferedImage {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE)
    val g2d = image.createGraphics()
    g2d.color = java.awt.Color.BLUE
    g2d.fillRect(0, 0, width, height / 2)
    g2d.color = java.awt.Color.BLACK
    g2d.fillRect(0, height / 2, width, height)
    g2d.font = JBUI.Fonts.label(30.0F)
    val string = "Hello world"
    val stringWidth = g2d.fontMetrics.stringWidth(string)

    g2d.drawString("Hello world", width / 2 - stringWidth / 2, height / 2)
    g2d.dispose()
    return image
  }

  @Test
  fun testApplyBackground() {
    val renderedImage = createTestForeground(300, 300)
    val goldenImage =
      TestUtils.resolveWorkspacePathUnchecked(
        "tools/adt/idea/compose-designer/testData/glassesPreview/blend_mode_golden.png"
      )

    // Create a copy of the rendered image to apply the blend mode to
    val imageToBlend =
      BufferedImage(renderedImage.width, renderedImage.height, BufferedImage.TYPE_INT_ARGB_PRE)
    val g = imageToBlend.createGraphics()
    g.drawImage(renderedImage, 0, 0, null)
    g.dispose()

    val blendMode = GlassesBackgroundBlendMode.getInstanceForTest(createTestBackground(300, 300))
    blendMode.applyBackground(imageToBlend)
    ImageDiffUtil.assertImageSimilar(goldenImage, imageToBlend, 0.01)
  }

  @Test
  fun testGetInstance() {
    assertNull(GlassesBackgroundBlendMode.getInstance(GlassesBackground.NONE))
  }
}
