/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.adtui.device

import com.android.io.readImage
import com.android.testutils.ImageDiffUtil
import com.android.test.testutils.TestUtils
import com.android.tools.adtui.ImageUtils
import com.android.tools.adtui.webp.WebpMetadata
import com.android.utils.HashCodes
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.function.Consumer
import java.util.function.Predicate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.test.fail

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
    val skin = SkinDefinition.createOrNull(folder) ?: fail("Expected non-null SkinDefinition")

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
    val skin = SkinDefinition.createOrNull(folder) ?: fail("Expected non-null SkinDefinition")

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
    val skin = SkinDefinition.createOrNull(folder) ?: fail("Expected non-null SkinDefinition")

    // Check the createScaledLayout method with scaling.
    val layout = skin.createScaledLayout(8, 16, 0)
    assertThat(layout.displaySize).isEqualTo(Dimension(8, 16))
    assertThat(layout.frameRectangle).isEqualTo(Rectangle(-0, -1, 8, 18))
    assertSkinAppearance(layout, "tiny_pixel_4_xl")
  }

  @Test
  fun testPixel_4() {
    val folder = getSkinFolder("pixel_4")
    val skin = SkinDefinition.createOrNull(folder) ?: fail("Expected non-null SkinDefinition")

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
    val skin = SkinDefinition.createOrNull(folder) ?: fail("Expected non-null SkinDefinition")

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
    val skin = SkinDefinition.createOrNull(folder) ?: fail("Expected non-null SkinDefinition")
    // Check the skin layout.
    assertThat(skin.getRotatedFrameSize(0)).isEqualTo(Dimension(2348, 1080))
  }

  @Test
  fun testSkinButtons() {
    val folder = getSkinFolder("nexus_one")
    val skin = SkinDefinition.createOrNull(folder) ?: fail("Expected non-null SkinDefinition")
    // Check the skin layout.
    val layout = skin.createScaledLayout(400, 240, 1)
    val buttons = layout.buttons
    assertThat(buttons).hasSize(7)
    assertThat(buttons[0].keyName).isEqualTo("GoBack")
    assertThat(buttons[0].image.size).isEqualTo(Dimension(24, 23))
    assertThat(buttons[0].image.anchorPoint).isEqualTo(AnchorPoint.BOTTOM_RIGHT)
    assertThat(buttons[0].image.offset).isEqualTo(Point(7, -41))
    assertThat(buttons[1].keyName).isEqualTo("Menu")
    assertThat(buttons[1].image.size).isEqualTo(Dimension(24, 23))
    assertThat(buttons[1].image.anchorPoint).isEqualTo(AnchorPoint.BOTTOM_RIGHT)
    assertThat(buttons[1].image.offset).isEqualTo(Point(7, -101))
    assertThat(buttons[1].image.image).isSameAs(buttons[0].image.image)
    assertThat(buttons[2].keyName).isEqualTo("GoHome")
    assertThat(buttons[2].image.size).isEqualTo(Dimension(24, 23))
    assertThat(buttons[2].image.anchorPoint).isEqualTo(AnchorPoint.TOP_RIGHT)
    assertThat(buttons[2].image.offset).isEqualTo(Point(7, 79))
    assertThat(buttons[2].image.image).isSameAs(buttons[0].image.image)
    assertThat(buttons[3].keyName).isEqualTo("Search")
    assertThat(buttons[3].image.size).isEqualTo(Dimension(24, 23))
    assertThat(buttons[3].image.anchorPoint).isEqualTo(AnchorPoint.TOP_RIGHT)
    assertThat(buttons[3].image.offset).isEqualTo(Point(7, 18))
    assertThat(buttons[3].image.image).isSameAs(buttons[0].image.image)
    assertThat(buttons[4].keyName).isEqualTo("AudioVolumeUp")
    assertThat(buttons[4].image.size).isEqualTo(Dimension(25, 34))
    assertThat(buttons[4].image.anchorPoint).isEqualTo(AnchorPoint.BOTTOM_LEFT)
    assertThat(buttons[4].image.offset).isEqualTo(Point(33, 17))
    assertThat(buttons[5].keyName).isEqualTo("AudioVolumeDown")
    assertThat(buttons[5].image.size).isEqualTo(Dimension(25, 34))
    assertThat(buttons[5].image.anchorPoint).isEqualTo(AnchorPoint.BOTTOM_LEFT)
    assertThat(buttons[5].image.offset).isEqualTo(Point(64, 17))
    assertThat(buttons[6].keyName).isEqualTo("Power")
    assertThat(buttons[6].image.size).isEqualTo(Dimension(29, 34))
    assertThat(buttons[6].image.anchorPoint).isEqualTo(AnchorPoint.BOTTOM_LEFT)
    assertThat(buttons[6].image.offset).isEqualTo(Point(-65, -45))
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
    Files.walk(dir, 3).use { stream ->
      stream.forEach { skinFolder ->
        if (Files.isDirectory(skinFolder) && !oldStyleSkins.contains(skinFolder.fileName.toString()) &&
            Files.exists(skinFolder.resolve("layout"))) {
          val skinName = skinFolder.subpath(dir.nameCount, skinFolder.nameCount)
          try {
            val skin = SkinDefinition.create(skinFolder)
            val layout = skin.layout
            val problems = validateLayout(layout, skinFolder)
            if (problems.isNotEmpty()) {
              skinProblems.add("Skin \"$skinName\" is inconsistent:\n${problems.joinToString("\n")}")
            }
          }
          catch (e: NoSuchFileException) {
            skinProblems.add("Unable to create skin \"$skinName\". File not found: ${e.file}")
          }
          catch (e: IOException) {
            val message = e.message?.let { "I/O error $it" } ?: "I/O error"
            skinProblems.add("Unable to create skin \"$skinName\". $message")
          }
          catch (e: InvalidSkinException) {
            skinProblems.add("Unable to create skin \"$skinName\". ${e.message}")
          }
        }
      }
    }
    if (skinProblems.isNotEmpty()) {
      fail("Invalid skins found:\n\n${skinProblems.joinToString("\n\n")}\n")
    }
  }

  private fun validateLayout(skinLayout: SkinLayout, skinFolder: Path): List<String> {
    val backgroundImageFile = SkinDefinition.getBackgroundImageFile(skinFolder) ?: return listOf("The skin doesn't define a background image")
    val backgroundImage = try {
      backgroundImageFile.readImage()
    }
    catch (e: NoSuchFileException) {
      return listOf("The background image \"${e.file}\" does not exist")
    }
    val displaySize = skinLayout.displaySize
    val center = Point(displaySize.width / 2 - skinLayout.frameRectangle.x,
                       displaySize.height / 2 - skinLayout.frameRectangle.y)
    if (!backgroundImage.isTransparentPixel(center)) {
      return listOf("The background image is not transparent near the center of the display")
    }

    val problems = mutableListOf<String>()
    val image = skinLayout.draw()
    if (backgroundImage.width != image.width || backgroundImage.height != image.height) {
      problems.add("The ${backgroundImageFile.fileName} image can be cropped without loosing any information")
    }

    val transparentAreaBounds = findBoundsOfContiguousArea(image, center, image::isTransparentPixel)
    if (transparentAreaBounds.width != displaySize.width) {
      problems.add("The width of the display area in the skin image (${transparentAreaBounds.width})" +
                     " doesn't match the layout file (${displaySize.width})")
    }
    if (transparentAreaBounds.height != displaySize.height) {
      problems.add("The height of the display area in the skin image (${transparentAreaBounds.height})" +
                     " doesn't match the layout file (${displaySize.height})")
    }
    val nonOpaqueAreaBounds = findBoundsOfContiguousArea(image, center, image::isNonOpaquePixel)
    if (nonOpaqueAreaBounds.x != transparentAreaBounds.x) {
      problems.add("Partially transparent pixels near the left edge of the display area")
    }
    if (nonOpaqueAreaBounds.x + nonOpaqueAreaBounds.width != transparentAreaBounds.x + transparentAreaBounds.width) {
      problems.add("Partially transparent pixels near the right edge of the display area")
    }
    if (nonOpaqueAreaBounds.y != transparentAreaBounds.y) {
      problems.add("Partially transparent pixels near the top edge of the display area")
    }
    if (nonOpaqueAreaBounds.y + nonOpaqueAreaBounds.height != transparentAreaBounds.y + transparentAreaBounds.height) {
      problems.add("Partially transparent pixels near the bottom edge of the display area")
    }
    if (transparentAreaBounds.x != -skinLayout.frameRectangle.x || transparentAreaBounds.y != -skinLayout.frameRectangle.y) {
      problems.add("Display offset in the layout file (${-skinLayout.frameRectangle.x}, ${-skinLayout.frameRectangle.y})" +
                   " doesn't match the skin image (${transparentAreaBounds.x}, ${transparentAreaBounds.y})")
    }

    // Check consistency between corners of the display and round corners of the frame.
    var maxExterior = -1
    val halfSize = min(displaySize.width, displaySize.height) / 2
    var minInterior = halfSize + 1
    for (iy in 0..1) {
      val yOffset = iy * (displaySize.height - 1) - skinLayout.frameRectangle.y // Y coordinate of a display corner.
      val yStep = 1 - 2 * iy // Direction of Y iteration.
      for (ix in 0..1) {
        val xOffset = ix * (displaySize.width - 1) - skinLayout.frameRectangle.x // X coordinate of a display corner.
        val xStep = 1 - 2 * ix // Direction of X iteration.
        var exterior = -1 // Distance between the display corner and outer boundary of the frame near the corner.
        var interior = halfSize + 1 // Distance between the display corner and inner boundary of the frame near the corner.
        for (d in 0..halfSize) {
          if (exterior < 0) {
            if (image.isOpaquePixel(Point(xOffset + d * xStep, yOffset + d * yStep))) {
              exterior = d
            }
          }
          else {
            if (image.isNonOpaquePixel(Point(xOffset + d * xStep, yOffset + d * yStep))) {
              interior = d
              break
            }
          }
        }
        maxExterior = max(maxExterior, exterior)
        minInterior = min(minInterior, interior)
      }
    }
    val f = 1 - 1 / sqrt(2.0)
    val maxR = minInterior / f
    val minR = maxExterior / f
    if (maxR > minR) {
      val r = skinLayout.displayCornerSize.width
      val recommendedR = ((minR + maxR) / 2).roundToInt()
      if (r < minR) {
        problems.add("Corners of the display are protruding beyond the frame. Can be fixed by setting corner_radius $recommendedR")
      }
      else if (r > maxR) {
        problems.add("There are gaps between the rounded corners of the display and the frame." +
                     " Can be fixed by setting corner_radius $recommendedR")
      }
    }
    else {
      problems.add("The skin inevitably leads to corners of the display protruding beyond the frame" +
                   " or to gaps between the rounded corners of the display and the frame.")
    }
    return problems
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

private fun BufferedImage.isTransparentPixel(point: Point): Boolean {
  return ImageUtils.isTransparentPixel(this, point.x, point.y)
}

private fun BufferedImage.isOpaquePixel(point: Point): Boolean {
  return ImageUtils.isOpaquePixel(this, point.x, point.y)
}

private fun BufferedImage.isNonOpaquePixel(point: Point): Boolean {
  return !isOpaquePixel(point)
}

/** The `hashCode` method is overloaded for efficiency. The [java.awt.Point.equals] method is ok. */
private class Point(x: Int, y: Int) : java.awt.Point(x, y) {

  override fun hashCode(): Int {
    return HashCodes.mix(x, y)
  }
}

private fun getSkinFolder(skinName: String): Path = getRootSkinFolder().resolve(skinName)

private fun getRootSkinFolder(): Path = TestUtils.resolveWorkspacePathUnchecked(DEVICE_ART_RESOURCES_DIR)

private const val DEVICE_ART_RESOURCES_DIR = "tools/adt/idea/artwork/resources/device-art-resources"

private val NEIGHBORS = listOf(Point(-1, -1), Point(-1, 0), Point(-1, 1), Point(0, 1),
                               Point(1, 1), Point(1, 0), Point(1, -1), Point(0, -1))

private const val TEST_DATA_PATH = "tools/adt/idea/adt-ui/testData/SkinDefinitionTest"
