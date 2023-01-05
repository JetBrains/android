/*
 * Copyright (C) 2015 The Android Open Source Project
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

/**
 * Utility methods helpful for working with and generating Android assets.
 */
@file:JvmName("AssetStudioUtils")

package com.android.tools.idea.npw.assetstudio

import com.android.ide.common.util.AssetUtil
import com.android.tools.adtui.ImageUtils
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.google.common.base.CaseFormat
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import java.io.File
import java.io.IOException
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val LOG: Logger
  get() = Logger.getInstance("#com.android.tools.idea.npw.assetstudio.AssetStudioUtils")

/**
 * Scales the given rectangle by the given scale factor.
 *
 * @param rect the rectangle to scale
 * @param scaleFactor the factor to scale by
 * @return the scaled rectangle
 */
fun scaleRectangle(rect: Rectangle, scaleFactor: Double): Rectangle = Rectangle(
  (rect.x * scaleFactor).roundToInt(),
  (rect.y * scaleFactor).roundToInt(),
  (rect.width * scaleFactor).roundToInt(),
  (rect.height * scaleFactor).roundToInt())

/**
 * Scales the given rectangle by the given scale factor preserving the location of its center.
 *
 * @param rect the rectangle to scale
 * @param scaleFactor the factor to scale by
 * @return the scaled rectangle
 */
fun scaleRectangleAroundCenter(rect: Rectangle, scaleFactor: Double): Rectangle {
  val width = (rect.width * scaleFactor).roundToInt()
  val height = (rect.height * scaleFactor).roundToInt()
  return Rectangle(
    (rect.x * scaleFactor - (width - rect.width) / 2.0).roundToInt(),
    (rect.y * scaleFactor - (height - rect.height) / 2.0).roundToInt(),
    width,
    height)
}

/**
 * Scales the given [Dimension] vector by the given scale factor.
 *
 * @param dim the vector to scale
 * @param scaleFactor the factor to scale by
 * @return the scaled vector
 */
fun scaleDimension(dim: Dimension, scaleFactor: Double) =
  Dimension((dim.width * scaleFactor).roundToInt(), (dim.height * scaleFactor).roundToInt())

/**
 * Exposes Kotlin's roundToInt to Java.
 *
 * @deprecated
 */
fun Double.roundToInt(): Int = roundToInt()

/**
 * Create a tiny sample image, so that we can always return a not null result if an image we were looking for isn't found.
 *
 */
@Suppress("UndesirableClassUsage") // we intentionally avoid UiUtil.createImage (for retina) because we just want a small image
fun createPlaceholderImage(): BufferedImage = BufferedImage(1, 1, TYPE_INT_ARGB)

/**
 * Remove any surrounding padding from the image.
 */
fun trim(image: BufferedImage): BufferedImage = ImageUtils.cropBlank(image, null, TYPE_INT_ARGB) ?: image

/**
 * Pad the image with extra space. The padding percent works by taking the largest side of the current image,
 * multiplying that with the percent value, and adding that portion to each side of the image.
 *
 * So for example, an image that's 100x100, with 50% padding percent, ends up resized to
 * (50+100+50)x(50+100+50), or 200x200. The 100x100 portion is then centered, taking up what
 * looks like 50% of the final image. The same 100x100 image, with 100% padding, ends up at
 * 300x300, looking in the final image like it takes up ~33% of the space.
 *
 * Padding can also be negative, which eats into the space of the original asset, causing a zoom in effect.
 */
fun pad(image: BufferedImage, paddingPercent: Int): BufferedImage {
  if (image.width <= 1 || image.height <= 1) {
    // If we're handling a sample image, just abort now before AssetUtil.paddedImage throws an exception.
    return image
  }

  val largerSide = max(image.width, image.height)
  val smallerSide = min(image.width, image.height)
  // Don't let padding get so negative that it would totally wipe out one of the dimensions.
  // And  since padding is added to all sides, negative padding should be at most half of the smallest side.
  // Example: if the smaller side is 100px, min padding is -49px
  val padding = (largerSide * paddingPercent.coerceAtMost(100) / 100).coerceAtLeast(-(smallerSide / 2 - 1))

  return AssetUtil.paddedImage(image, padding)
}

/**
 * Returns the name of an enum value as a lower camel case string.
 */
fun toLowerCamelCase(enumValue: Enum<*>): String = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, enumValue.name)

/**
 * Returns the name of an enum value as an upper camel case string.
 */
fun toUpperCamelCase(enumValue: Enum<*>): String = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, enumValue.name)

/**
 * Returns a file pointing to a resource inside template.
 */
fun getBundledImage(dir: String, fileName: String): File {
  val homePath = Paths.get(PathManager.getHomePath())
  val releaseImagesDir = homePath.resolve("plugins/android/resources/images/$dir")
  val devImagesDir = homePath.resolve("../../../../../../tools/adt/idea/android/resources/images/$dir")
  val releaseImage = releaseImagesDir.resolve(fileName)
  val devImage = devImagesDir.resolve(fileName)

  val root = listOf(releaseImage, devImage, releaseImagesDir, devImagesDir, homePath).firstOrNull {
    it.exists()
  }?.toFile() ?: throw IOException("Studio root dir '$homePath' is not readable")

  if (root.isDirectory) {
    LOG.error(
      "Bundled image file $fileName is not found neither in $releaseImagesDir not $devImagesDir"
    )
  }

  return root
}

/**
 * Return a list of [NamedModuleTemplate]s sorted by alphabetical order, but starting
 * with "main", "debug" and "release" if those are present in the input.
 */
fun orderTemplates(templates: List<NamedModuleTemplate>): List<NamedModuleTemplate> {
  var main: NamedModuleTemplate? = null
  var debug: NamedModuleTemplate? = null
  var release: NamedModuleTemplate? = null
  val orderedList: MutableList<NamedModuleTemplate> = mutableListOf()
  for (template in templates) {
    when (template.name) {
      "main" -> main = template
      "debug" -> debug = template
      "release" -> release = template
      else -> orderedList.add(template)
    }
  }
  orderedList.sortWith(Comparator.comparing(NamedModuleTemplate::name))
  release?.let { orderedList.add(0, it) }
  debug?.let { orderedList.add(0, it) }
  main?.let { orderedList.add(0, it) }
  return orderedList
}
