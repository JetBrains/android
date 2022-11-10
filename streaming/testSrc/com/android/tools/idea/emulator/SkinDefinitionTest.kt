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

import com.android.io.readImage
import com.android.testutils.ImageDiffUtil
import com.android.testutils.TestUtils
import com.android.tools.adtui.webp.WebpMetadata
import com.android.tools.idea.avdmanager.SkinLayoutDefinition
import com.android.tools.idea.emulator.FakeEmulator.Companion.getRootSkinFolder
import com.android.tools.idea.emulator.FakeEmulator.Companion.getSkinFolder
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Before
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
    assertThat(skin.getRotatedFrameSize(0)).isEqualTo(Dimension(1623, 3322))
    assertThat(skin.getRotatedFrameSize(1)).isEqualTo(Dimension(3322, 1623))
    assertThat(skin.getRotatedFrameSize(2)).isEqualTo(Dimension(1623, 3322))
    assertThat(skin.getRotatedFrameSize(3)).isEqualTo(Dimension(3322, 1623))

    // Check the createScaledLayout method without rotation or scaling.
    var layout = skin.layout
    assertThat(layout.displaySize).isEqualTo(Dimension(1440, 2880))
    assertThat(layout.frameRectangle).isEqualTo(Rectangle(-86, -242, 1623, 3322))
    assertThat(layout.frameImages).hasSize(8)
    assertThat(layout.maskImages).hasSize(4) // Four round corners.

    // Check the createScaledLayout method with scaling.
    layout = skin.createScaledLayout(325, 650, 0)
    assertThat(layout.displaySize).isEqualTo(Dimension(325, 650))
    assertThat(layout.frameRectangle).isEqualTo(Rectangle(-19, -55, 366, 750))
    assertSkinAppearance(layout, "pixel_2_xl")

    // Check the createScaledLayout method with 90-degree rotation and scaling.
    layout = skin.createScaledLayout(650, 325, 1)
    assertThat(layout.displaySize).isEqualTo(Dimension(650, 325))
    assertThat(layout.frameRectangle).isEqualTo(Rectangle(-55, -22, 750, 366))
    assertSkinAppearance(layout, "pixel_2_xl_90")

    // Check the createScaledLayout method with 180-degree rotation and scaling.
    layout = skin.createScaledLayout(325, 650, 2)
    assertThat(layout.displaySize).isEqualTo(Dimension(325, 650))
    assertThat(layout.frameRectangle).isEqualTo(Rectangle(-22, -45, 366, 750))
    assertSkinAppearance(layout, "pixel_2_xl_180")

    // Check the createScaledLayout method with 270-degree rotation and scaling.
    layout = skin.createScaledLayout(650, 325, 3)
    assertThat(layout.displaySize).isEqualTo(Dimension(650, 325))
    assertThat(layout.frameRectangle).isEqualTo(Rectangle(-45, -19, 750, 366))
    assertSkinAppearance(layout, "pixel_2_xl_270")
  }

  @Test
  fun testPixel_3_XL() {
    val folder = getSkinFolder("pixel_3_xl")
    val skin = SkinDefinition.create(folder) ?: throw AssertionError("Expected non-null SkinDefinition")

    // Check the getRotatedFrameSize method without scaling.
    assertThat(skin.getRotatedFrameSize(0)).isEqualTo(Dimension(1584, 3245))
    assertThat(skin.getRotatedFrameSize(1)).isEqualTo(Dimension(3245, 1584))
    assertThat(skin.getRotatedFrameSize(2)).isEqualTo(Dimension(1584, 3245))
    assertThat(skin.getRotatedFrameSize(3)).isEqualTo(Dimension(3245, 1584))

    // Check the getRotatedFrameSize method with scaling.
    assertThat(skin.getRotatedFrameSize(0, Dimension(1452, 2984))).isEqualTo(Dimension(1598, 3272))
    assertThat(skin.getRotatedFrameSize(1, Dimension(1452, 2984))).isEqualTo(Dimension(3272, 1598))
    assertThat(skin.getRotatedFrameSize(2, Dimension(1452, 2984))).isEqualTo(Dimension(1598, 3272))
    assertThat(skin.getRotatedFrameSize(3, Dimension(1452, 2984))).isEqualTo(Dimension(3272, 1598))

    // Check the createScaledLayout method without rotation or scaling.
    var layout = skin.layout
    assertThat(layout.displaySize).isEqualTo(Dimension(1440, 2960))
    assertThat(layout.frameRectangle).isEqualTo(Rectangle(-65, -72, 1584, 3245))
    assertThat(layout.frameImages).hasSize(8)
    assertThat(layout.maskImages).hasSize(5) // Four round corners and the cutout.

    // Check the createScaledLayout method with scaling.
    layout = skin.createScaledLayout(341, 700, 0)
    assertThat(layout.displaySize).isEqualTo(Dimension(341, 700))
    assertThat(layout.frameRectangle).isEqualTo(Rectangle(-15, -17, 375, 767))
    assertSkinAppearance(layout, "pixel_3_xl")

    // Check the createScaledLayout method with 90-degree rotation and scaling.
    layout = skin.createScaledLayout(700, 341, 1)
    assertThat(layout.displaySize).isEqualTo(Dimension(700, 341))
    assertThat(layout.frameRectangle).isEqualTo(Rectangle(-17, -19, 767, 375))
    assertSkinAppearance(layout, "pixel_3_xl_90")

    // Check the createScaledLayout method with 180-degree rotation and scaling.
    layout = skin.createScaledLayout(341, 700, 2)
    assertThat(layout.displaySize).isEqualTo(Dimension(341, 700))
    assertThat(layout.frameRectangle).isEqualTo(Rectangle(-19, -50, 375, 767))
    assertSkinAppearance(layout, "pixel_3_xl_180")

    // Check the createScaledLayout method with 270-degree rotation and scaling.
    layout = skin.createScaledLayout(700, 341, 3)
    assertThat(layout.displaySize).isEqualTo(Dimension(700, 341))
    assertThat(layout.frameRectangle).isEqualTo(Rectangle(-50, -15, 767, 375))
    assertSkinAppearance(layout, "pixel_3_xl_270")
  }

  @Test
  fun testVeryTinyScale() {
    val folder = getSkinFolder("pixel_4_xl")
    val skin = SkinDefinition.create(folder) ?: throw AssertionError("Expected non-null SkinDefinition")

    // Check the createScaledLayout method with scaling.
    val layout = skin.createScaledLayout(8, 16, 0)
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
  fun testPixel_4_xl() {
    val folder = getSkinFolder("pixel_4_xl")
    val skin = SkinDefinition.create(folder) ?: throw AssertionError("Expected non-null SkinDefinition")

    // Check the skin layout and consistency of its images.
    val layout = skin.layout
    assertThat(layout.displaySize).isEqualTo(Dimension(1440, 3040))
    assertThat(layout.frameRectangle.size).isEqualTo(Dimension(1571, 3332))
    assertThat(layout.frameImages).hasSize(8)
    assertThat(layout.maskImages).hasSize(4) // Four round corners.
  }

  @Test
  fun testTwoDisplays() {
    val folder = TestUtils.resolveWorkspacePathUnchecked("${TEST_DATA_PATH}/skins/two_displays")
    val skin = SkinDefinition.create(folder) ?: throw AssertionError("Expected non-null SkinDefinition")
    // Check the skin layout.
    assertThat(skin.getRotatedFrameSize(0)).isEqualTo(Dimension(2348, 1080))
  }

  @Test
  fun testSkinConsistency() {
    // Old-style skins are not checked by this test. Please don't add any new skins to this list.
    val oldStyleSkins = listOf(
      "automotive_1024",
      "nexus_one",
      "nexus_s",
      "nexus_4",
      "nexus_5",
      "nexus_5x",
      "nexus_6",
      "nexus_6p",
      "nexus_7",
      "nexus_7_2013",
      "nexus_9",
      "nexus_10",
      "galaxy_nexus",
      "pixel",
      "pixel_xl",
      "pixel_c",
      "pixel_silver",
      "pixel_xl_silver",
      "pixel_2",
      "pixel_2_xl",
      "pixel_3",
      "pixel_3_xl",
      "pixel_3a",
      "pixel_3a_xl",
      "pixel_4",
      "pixel_4_xl",
      "tv_720p",
      "tv_1080p",
      "tv_4k",
      "wearos_large_round", // TODO: Remove exclusion when the skin is fixed.
      "wearos_small_round", // TODO: Remove exclusion when the skin is fixed.
      "wearos_square",
    )
    val skinProblems = mutableListOf<String>()
    val dir = getRootSkinFolder()
    Files.list(dir).use { stream ->
      stream.forEach { skinFolder ->
        if (Files.isDirectory(skinFolder) && !oldStyleSkins.contains(skinFolder.fileName.toString()) &&
            Files.exists(skinFolder.resolve("layout"))) {
          val skin = SkinDefinition.create(skinFolder)
          if (skin == null) {
            skinProblems.add("Unable to create skin \"${skinFolder.fileName}\"")
          }
          else {
            val layout = skin.layout
            val problems = validateLayout(layout, skinFolder)
            if (problems.isNotEmpty()) {
              skinProblems.add("Skin \"${skinFolder.fileName}\" is inconsistent:\n${problems.joinToString("\n")}")
            }
          }
        }
      }
    }
    if (skinProblems.isNotEmpty()) {
      fail("Invalid skins found:\n\n${skinProblems.joinToString("\n\n")}\n")
    }
  }

  private fun validateLayout(skinLayout: SkinLayout, skinFolder: Path): List<String> {
    val backgroundImageFile = getBackgroundImageFile(skinFolder) ?: return listOf("The skin doesn't define a background image")
    val backgroundImage = try {
      backgroundImageFile.readImage()
    }
    catch (e: NoSuchFileException) {
      return listOf("The background image \"${e.file}\" does not exist")
    }
    val center = Point(skinLayout.displaySize.width / 2 - skinLayout.frameRectangle.x,
                       skinLayout.displaySize.height / 2 - skinLayout.frameRectangle.y)
    if (!backgroundImage.isTransparent(center)) {
      return listOf("The background image is not transparent near the center of the display")
    }

    val problems = mutableListOf<String>()
    val image = skinLayout.draw()
    if (backgroundImage.width != image.width || backgroundImage.height != image.height) {
      problems.add("The ${backgroundImageFile.fileName} image can be cropped without loosing any information")
    }

    val transparentAreaBounds = findBoundsOfContiguousArea(image, center, image::isTransparent)
    if (transparentAreaBounds.width != skinLayout.displaySize.width) {
      problems.add("The width of the display area in the skin image (${transparentAreaBounds.width})" +
                     " doesn't match the layout file (${skinLayout.displaySize.width})")
    }
    if (transparentAreaBounds.height != skinLayout.displaySize.height) {
      problems.add("The height of the display area in the skin image (${transparentAreaBounds.height})" +
                     " doesn't match the layout file (${skinLayout.displaySize.height})")
    }
    val nonOpaqueAreaBounds = findBoundsOfContiguousArea(image, center, image::isNotOpaque)
    if (nonOpaqueAreaBounds.x != transparentAreaBounds.x) {
      problems.add("Partially transparent pixels near the left edge of the display area")
    }
    if (nonOpaqueAreaBounds.x + nonOpaqueAreaBounds.width != transparentAreaBounds.x + +transparentAreaBounds.width) {
      problems.add("Partially transparent pixels near the right edge of the display area")
    }
    if (nonOpaqueAreaBounds.y != transparentAreaBounds.y) {
      problems.add("Partially transparent pixels near the top edge of the display area")
    }
    if (nonOpaqueAreaBounds.y + nonOpaqueAreaBounds.height != transparentAreaBounds.y + +transparentAreaBounds.height) {
      problems.add("Partially transparent pixels near the bottom edge of the display area")
    }
    if (transparentAreaBounds.x != -skinLayout.frameRectangle.x || transparentAreaBounds.y != -skinLayout.frameRectangle.y) {
      problems.add("Display offset in the layout file (${-skinLayout.frameRectangle.x}, ${-skinLayout.frameRectangle.y})" +
                   " doesn't match the skin image (${transparentAreaBounds.x}, ${transparentAreaBounds.y})")
    }
    return problems
  }

  private fun getBackgroundImageFile(skinFolder: Path): Path? {
    val layoutFile = skinFolder.resolve("layout")
    val contents = Files.readAllBytes(layoutFile).toString(StandardCharsets.UTF_8)
    val layoutDefinition = SkinLayoutDefinition.parseString(contents)
    val backgroundFileName = layoutDefinition.getValue("parts.portrait.background.image") ?: return null
    return skinFolder.resolve(backgroundFileName)
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
    val image = BufferedImage(frameRectangle.width, frameRectangle.height, TYPE_INT_ARGB)
    val g = image.createGraphics()
    drawFrameAndMask(g, Rectangle(-frameRectangle.x, -frameRectangle.y, displaySize.width, displaySize.height))
    g.dispose()
    return image
  }

  private fun getGoldenFile(name: String): Path {
    return TestUtils.resolveWorkspacePathUnchecked("${TEST_DATA_PATH}/golden/${name}.png")
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

private const val TEST_DATA_PATH = "tools/adt/idea/streaming/testData/SkinDefinitionTest"
