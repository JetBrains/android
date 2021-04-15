/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.emulator

import com.android.emulator.control.Rotation.SkinRotation
import com.android.testutils.ImageDiffUtil
import com.android.testutils.TestUtils.getWorkspaceRoot
import com.android.tools.adtui.ImageUtils
import com.android.tools.adtui.webp.WebpMetadata
import com.android.tools.idea.emulator.FakeEmulator.Companion.getSkinFolder
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import java.nio.file.Path

/**
 * Tests for [SkinDefinition] and related classes.
 */
class SkinDefinitionTest {

  @Before
  fun setUp() {
    WebpMetadata.ensureWebpRegistered()
  }

  @Test
  fun testPixel_2_XL() {
    val folder = getSkinFolder("pixel_2_xl")
    val skin = SkinDefinition.create(folder) ?: throw AssertionError("Expected non-null SkinDefinition")

    // Check the getRotatedFrameSize method.
    assertThat(skin.getRotatedFrameSize(SkinRotation.PORTRAIT)).isEqualTo(Dimension(1623, 3322))
    assertThat(skin.getRotatedFrameSize(SkinRotation.LANDSCAPE)).isEqualTo(Dimension(3322, 1623))
    assertThat(skin.getRotatedFrameSize(SkinRotation.REVERSE_PORTRAIT)).isEqualTo(Dimension(1623, 3322))
    assertThat(skin.getRotatedFrameSize(SkinRotation.REVERSE_LANDSCAPE)).isEqualTo(Dimension(3322, 1623))

    // Check the createScaledLayout method without rotation or scaling.
    var layout = skin.layout
    assertThat(layout.displaySize).isEqualTo(Dimension(1440, 2880))
    assertThat(layout.frameRectangle).isEqualTo(Rectangle(-86, -242, 1623, 3322))
    assertThat(layout.frameImages).hasSize(8)
    assertThat(layout.maskImages).hasSize(4) // Four round corners.

    // Check the createScaledLayout method with scaling.
    layout = skin.createScaledLayout(325, 650, SkinRotation.PORTRAIT)
    assertThat(layout.displaySize).isEqualTo(Dimension(325, 650))
    assertThat(layout.frameRectangle).isEqualTo(Rectangle(-19, -55, 366, 750))
    assertSkinAppearance(layout, "pixel_2_xl")

    // Check the createScaledLayout method with 90 degree rotation and scaling.
    layout = skin.createScaledLayout(650, 325, SkinRotation.LANDSCAPE)
    assertThat(layout.displaySize).isEqualTo(Dimension(650, 325))
    assertThat(layout.frameRectangle).isEqualTo(Rectangle(-55, -22, 750, 366))
    assertSkinAppearance(layout, "pixel_2_xl_90")

    // Check the createScaledLayout method with 180 degree rotation and scaling.
    layout = skin.createScaledLayout(325, 650, SkinRotation.REVERSE_PORTRAIT)
    assertThat(layout.displaySize).isEqualTo(Dimension(325, 650))
    assertThat(layout.frameRectangle).isEqualTo(Rectangle(-22, -45, 366, 750))
    assertSkinAppearance(layout, "pixel_2_xl_180")

    // Check the createScaledLayout method with 270 degree rotation and scaling.
    layout = skin.createScaledLayout(650, 325, SkinRotation.REVERSE_LANDSCAPE)
    assertThat(layout.displaySize).isEqualTo(Dimension(650, 325))
    assertThat(layout.frameRectangle).isEqualTo(Rectangle(-45, -19, 750, 366))
    assertSkinAppearance(layout, "pixel_2_xl_270")
  }

  @Test
  fun testPixel_3_XL() {
    val folder = getSkinFolder("pixel_3_xl")
    val skin = SkinDefinition.create(folder) ?: throw AssertionError("Expected non-null SkinDefinition")

    // Check the getRotatedFrameSize method without scaling.
    assertThat(skin.getRotatedFrameSize(SkinRotation.PORTRAIT)).isEqualTo(Dimension(1584, 3245))
    assertThat(skin.getRotatedFrameSize(SkinRotation.LANDSCAPE)).isEqualTo(Dimension(3245, 1584))
    assertThat(skin.getRotatedFrameSize(SkinRotation.REVERSE_PORTRAIT)).isEqualTo(Dimension(1584, 3245))
    assertThat(skin.getRotatedFrameSize(SkinRotation.REVERSE_LANDSCAPE)).isEqualTo(Dimension(3245, 1584))

    // Check the getRotatedFrameSize method with scaling.
    assertThat(skin.getRotatedFrameSize(SkinRotation.PORTRAIT, Dimension(1452, 2984))).isEqualTo(Dimension(1598, 3272))
    assertThat(skin.getRotatedFrameSize(SkinRotation.LANDSCAPE, Dimension(1452, 2984))).isEqualTo(Dimension(3272, 1598))
    assertThat(skin.getRotatedFrameSize(SkinRotation.REVERSE_PORTRAIT, Dimension(1452, 2984))).isEqualTo(Dimension(1598, 3272))
    assertThat(skin.getRotatedFrameSize(SkinRotation.REVERSE_LANDSCAPE, Dimension(1452, 2984))).isEqualTo(Dimension(3272, 1598))

    // Check the createScaledLayout method without rotation or scaling.
    var layout = skin.layout
    assertThat(layout.displaySize).isEqualTo(Dimension(1440, 2960))
    assertThat(layout.frameRectangle).isEqualTo(Rectangle(-65, -72, 1584, 3245))
    assertThat(layout.frameImages).hasSize(8)
    assertThat(layout.maskImages).hasSize(5) // Four round corners and the cutout.

    // Check the createScaledLayout method with scaling.
    layout = skin.createScaledLayout(341, 700, SkinRotation.PORTRAIT)
    assertThat(layout.displaySize).isEqualTo(Dimension(341, 700))
    assertThat(layout.frameRectangle).isEqualTo(Rectangle(-15, -17, 375, 767))
    assertSkinAppearance(layout, "pixel_3_xl")

    // Check the createScaledLayout method with 90 degree rotation and scaling.
    layout = skin.createScaledLayout(700, 341, SkinRotation.LANDSCAPE)
    assertThat(layout.displaySize).isEqualTo(Dimension(700, 341))
    assertThat(layout.frameRectangle).isEqualTo(Rectangle(-17, -19, 767, 375))
    assertSkinAppearance(layout, "pixel_3_xl_90")

    // Check the createScaledLayout method with 180 degree rotation and scaling.
    layout = skin.createScaledLayout(341, 700, SkinRotation.REVERSE_PORTRAIT)
    assertThat(layout.displaySize).isEqualTo(Dimension(341, 700))
    assertThat(layout.frameRectangle).isEqualTo(Rectangle(-19, -50, 375, 767))
    assertSkinAppearance(layout, "pixel_3_xl_180")

    // Check the createScaledLayout method with 270 degree rotation and scaling.
    layout = skin.createScaledLayout(700, 341, SkinRotation.REVERSE_LANDSCAPE)
    assertThat(layout.displaySize).isEqualTo(Dimension(700, 341))
    assertThat(layout.frameRectangle).isEqualTo(Rectangle(-50, -15, 767, 375))
    assertSkinAppearance(layout, "pixel_3_xl_270")
  }

  @Test
  fun testVeryTinyScale() {
    val folder = getSkinFolder("pixel_4_xl")
    val skin = SkinDefinition.create(folder) ?: throw AssertionError("Expected non-null SkinDefinition")

    // Check the createScaledLayout method with scaling.
    val layout = skin.createScaledLayout(8, 16, SkinRotation.PORTRAIT)
    assertThat(layout.displaySize).isEqualTo(Dimension(8, 16))
    assertThat(layout.frameRectangle).isEqualTo(Rectangle(-0, -1, 8, 18))
    assertSkinAppearance(layout, "tiny_pixel_4_xl")
  }

  @Test
  fun testPixel_4() {
    val folder = getSkinFolder("pixel_4")
    val skin = SkinDefinition.create(folder) ?: throw AssertionError("Expected non-null SkinDefinition")

    // Check the skin layout and consistency of its images.
    val layout = skin.layout
    assertThat(layout.displaySize).isEqualTo(Dimension(1080, 2280))
    assertThat(layout.frameRectangle.size).isEqualTo(Dimension(1178, 2498))
    assertThat(layout.frameImages).hasSize(8)
    assertThat(layout.maskImages).hasSize(4) // Four round corners.
  }

  @Test
  fun testPixel_4_XL() {
    val folder = getSkinFolder("pixel_4_xl")
    val skin = SkinDefinition.create(folder) ?: throw AssertionError("Expected non-null SkinDefinition")

    // Check the skin layout and consistency of its images.
    val layout = skin.layout
    assertThat(layout.displaySize).isEqualTo(Dimension(1440, 3040))
    assertThat(layout.frameRectangle.size).isEqualTo(Dimension(1571, 3332))
    assertThat(layout.frameImages).hasSize(8)
    assertThat(layout.maskImages).hasSize(4) // Four round corners.
  }

  @Ignore("Enable when the pixel_4a skin is fixed (b/171274996)")
  @Test
  fun testPixel_4a() {
    val folder = getSkinFolder("pixel_4a")
    val skin = SkinDefinition.create(folder) ?: throw AssertionError("Expected non-null SkinDefinition")

    // Check the skin layout and consistency of its images.
    val layout = skin.layout
    assertThat(layout.displaySize).isEqualTo(Dimension(1080, 2340))
    assertThat(layout.frameRectangle.size).isEqualTo(Dimension(1195, 2473))
    assertThat(layout.frameImages).hasSize(8)
    assertThat(layout.maskImages).hasSize(4) // Four round corners, one with a camera hole.
  }

  @Ignore("Enable when the pixel_5 skin is fixed (b/171274996)")
  @Test
  fun testPixel_5() {
    val folder = getSkinFolder("pixel_5")
    val skin = SkinDefinition.create(folder) ?: throw AssertionError("Expected non-null SkinDefinition")

    // Check the skin layout and consistency of its images.
    val layout = skin.layout
    assertThat(layout.displaySize).isEqualTo(Dimension(1080, 2340))
    assertThat(layout.frameRectangle.size).isEqualTo(Dimension(1196, 2441))
    assertThat(layout.frameImages).hasSize(8)
    assertThat(layout.maskImages).hasSize(4) // Four round corners, one with a camera hole.
  }

  @Test
  fun testWearRound() {
    val folder = getSkinFolder("wear_round")
    val skin = SkinDefinition.create(folder) ?: throw AssertionError("Expected non-null SkinDefinition")

    // Check the getRotatedFrameSize method.
    assertThat(skin.getRotatedFrameSize(SkinRotation.PORTRAIT)).isEqualTo(Dimension(380, 380))
    assertThat(skin.getRotatedFrameSize(SkinRotation.LANDSCAPE)).isEqualTo(Dimension(380, 380))
    assertThat(skin.getRotatedFrameSize(SkinRotation.REVERSE_PORTRAIT)).isEqualTo(Dimension(380, 380))
    assertThat(skin.getRotatedFrameSize(SkinRotation.REVERSE_LANDSCAPE)).isEqualTo(Dimension(380, 380))

    // Check the createScaledLayout method without rotation or scaling.
    val layout = skin.layout
    assertThat(layout.displaySize).isEqualTo(Dimension(320, 320))
    assertThat(layout.frameRectangle).isEqualTo(Rectangle(-30, -30, 380, 380))
    assertThat(layout.frameImages).hasSize(8)
    assertThat(layout.maskImages).hasSize(1)
    assertSkinAppearance(layout, "wear_round")
  }

  private fun assertSkinAppearance(layout: SkinLayout, goldenImageName: String) {
    val frameRectangle = layout.frameRectangle
    val image = ImageUtils.createDipImage(frameRectangle.width, frameRectangle.height, TYPE_INT_ARGB)
    val g = image.createGraphics()
    layout.drawFrameAndMask(g, Rectangle(-frameRectangle.x, -frameRectangle.y, layout.displaySize.width, layout.displaySize.height))
    g.dispose()
    ImageDiffUtil.assertImageSimilar(getGoldenFile(goldenImageName), image, 0.0)
  }

  private fun getGoldenFile(name: String): Path {
    return getWorkspaceRoot().resolve("${GOLDEN_FILE_PATH}/${name}.png")
  }
}

private const val GOLDEN_FILE_PATH = "tools/adt/idea/emulator/testData/SkinDefinitionTest/golden"
