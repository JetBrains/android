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
import com.android.io.readImage
import com.android.testutils.ImageDiffUtil
import com.android.testutils.TestUtils.getWorkspaceRoot
import com.android.tools.adtui.ImageUtils
import com.android.tools.adtui.webp.WebpMetadata
import com.android.tools.idea.avdmanager.SkinLayoutDefinition
import com.android.tools.idea.emulator.FakeEmulator.Companion.getSkinFolder
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.function.Consumer
import java.util.function.Predicate

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
    validateLayoutConsistency(layout, folder)
    assertThat(layout.displaySize).isEqualTo(Dimension(1080, 2340))
    assertThat(layout.frameRectangle.size).isEqualTo(Dimension(1204, 2491))
    assertThat(layout.frameImages).hasSize(8)
    assertThat(layout.maskImages).hasSize(4) // Four round corners, one with a camera hole.
  }

  @Test
  fun testPixel_5() {
    val folder = getSkinFolder("pixel_5")
    val skin = SkinDefinition.create(folder) ?: throw AssertionError("Expected non-null SkinDefinition")

    // Check the skin layout and consistency of its images.
    val layout = skin.layout
    validateLayoutConsistency(layout, folder)
    assertThat(layout.displaySize).isEqualTo(Dimension(1080, 2340))
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

  private fun validateLayoutConsistency(skinLayout: SkinLayout, skinFolder: Path) {
    val image = skinLayout.draw()
    val backgroundImage = readBackgroundImage(skinFolder)
    val problems = mutableListOf<String>()
    if (backgroundImage != null && (backgroundImage.width != image.width || backgroundImage.height != image.height)) {
      problems.add("The background image can be cropped without loosing any information")
    }

    val start = Point(skinLayout.displaySize.width / 2 - skinLayout.frameRectangle.x,
                      skinLayout.displaySize.height / 2 - skinLayout.frameRectangle.y)
    if (!image.isTransparent(start)) {
      fail("The skin image is not transparent near the center of the display")
    }
    val transparentAreaBounds = findBoundsOfContiguousArea(image, start, image::isTransparent)
    if (transparentAreaBounds.width != skinLayout.displaySize.width) {
      problems.add("The width of the display area in the skin image (${transparentAreaBounds.width})" +
                   " doesn't match the layout file (${skinLayout.displaySize.width})")
    }
    if (transparentAreaBounds.height != skinLayout.displaySize.height) {
      problems.add("The height of the display area in the skin image (${transparentAreaBounds.height})" +
                   " doesn't match the layout file (${skinLayout.displaySize.height})")
    }
    val nonOpaqueAreaBounds = findBoundsOfContiguousArea(image, start, image::isNotOpaque)
    if (nonOpaqueAreaBounds.x != transparentAreaBounds.x) {
      problems.add("Partially transparent pixels near the left edge of the display area")
    }
    if (nonOpaqueAreaBounds.x + nonOpaqueAreaBounds.width != transparentAreaBounds.x +  + transparentAreaBounds.width) {
      problems.add("Partially transparent pixels near the right edge of the display area")
    }
    if (nonOpaqueAreaBounds.y != transparentAreaBounds.y) {
      problems.add("Partially transparent pixels near the top edge of the display area")
    }
    if (nonOpaqueAreaBounds.y + nonOpaqueAreaBounds.height != transparentAreaBounds.y +  + transparentAreaBounds.height) {
      problems.add("Partially transparent pixels near the bottom edge of the display area")
    }
    if (transparentAreaBounds.x != -skinLayout.frameRectangle.x || transparentAreaBounds.y != -skinLayout.frameRectangle.y) {
      problems.add("Display offset in the layout file (${-skinLayout.frameRectangle.x}, ${-skinLayout.frameRectangle.y})" +
                   " doesn't match the skin image (${transparentAreaBounds.x}, ${transparentAreaBounds.y})")
    }
    if (problems.isNotEmpty()) {
      fail(problems.joinToString("\n"))
    }
  }

  private fun readBackgroundImage(skinFolder: Path): BufferedImage? {
    val layoutFile = skinFolder.resolve("layout")
    try {
      val contents = Files.readAllBytes(layoutFile).toString(StandardCharsets.UTF_8)
      val layoutDefinition = SkinLayoutDefinition.parseString(contents)
      val backroundFileName = layoutDefinition.getValue("parts.portrait.background.image") ?: return null
      val backgroundFile = skinFolder.resolve(backroundFileName)
      return backgroundFile.readImage()
    }
    catch (e: NoSuchFileException) {
      return null
    }
  }

  private fun findBoundsOfContiguousArea(image: BufferedImage, start: Point, predicate: Predicate<Point>): Rectangle {
    var minX = start.x
    var maxX = start.x
    var minY = start.y
    var maxY = start.y

    visitContiguousArea(image, start, predicate) { point ->
      minX = minX.coerceAtMost(point.x)
      maxX = maxX.coerceAtLeast(point.x)
      minY = minY.coerceAtMost(point.y)
      maxY = maxY.coerceAtLeast(point.y)
    }
    return Rectangle(minX, minY, maxX + 1 - minX, maxY + 1 - minY)
  }

  /**
   * Calls [visitor] for every point of the contiguous area of the [image] where every pixel satisfies
   * the [predicate] and containing the [start] point.
   */
  private fun visitContiguousArea(image: BufferedImage, start: Point, predicate: Predicate<Point>, visitor: Consumer<Point>) {
    if (!predicate.test(start)) {
      return
    }
    visitor.accept(start)
    val width = image.width
    val height = image.height
    // Use the flood fill algorithm to explore the contiguous area.
    var front = mutableSetOf(start)
    var previousFront = mutableSetOf<Point>()
    while (front.isNotEmpty()) {
      var newFront = mutableSetOf<Point>()
      for (p1 in front) {
        for (neighbor in NEIGHBORS) {
          val p2 = Point(p1.x + neighbor.x, p1.y + neighbor.y)
          if (p2.x in 0 until width && p2.y in 0 until height &&
              !newFront.contains(p2) && !front.contains(p2) && !previousFront.contains(p2) && predicate.test(p2)) {
            visitor.accept(p2)
            newFront.add(p2)
          }
        }
      }
      val temp = previousFront
      previousFront = front
      front = newFront
      newFront = temp
      newFront.clear()
    }
  }

  private fun assertSkinAppearance(skinLayout: SkinLayout, goldenImageName: String) {
    val image = skinLayout.draw()
    ImageDiffUtil.assertImageSimilar(getGoldenFile(goldenImageName), image, 0.0)
  }

  private fun SkinLayout.draw(): BufferedImage {
    val image = ImageUtils.createDipImage(frameRectangle.width, frameRectangle.height, TYPE_INT_ARGB)
    val g = image.createGraphics()
    drawFrameAndMask(g, Rectangle(-frameRectangle.x, -frameRectangle.y, displaySize.width, displaySize.height))
    g.dispose()
    return image
  }

  private fun getGoldenFile(name: String): Path {
    return getWorkspaceRoot().resolve("${GOLDEN_FILE_PATH}/${name}.png")
  }
}

private fun BufferedImage.isTransparent(point: Point): Boolean {
  return getRGB(point.x, point.y) and ALPHA_MASK == 0
}

private fun BufferedImage.isOpaque(point: Point): Boolean {
  return getRGB(point.x, point.y) and ALPHA_MASK == ALPHA_MASK
}

private fun BufferedImage.isNotOpaque(point: Point): Boolean {
  return !isOpaque(point)
}

data class Point(val x: Int, val y: Int) // Unlike java.awt.Point this class has an efficient hashCode method.

private val NEIGHBORS = listOf(Point(-1, -1), Point(-1, 0), Point(-1, 1), Point(0, 1),
                               Point(1, 1), Point(1, 0), Point(1, -1), Point(0, -1))

private const val ALPHA_MASK = 0xFF shl 24

private const val GOLDEN_FILE_PATH = "tools/adt/idea/emulator/testData/SkinDefinitionTest/golden"
