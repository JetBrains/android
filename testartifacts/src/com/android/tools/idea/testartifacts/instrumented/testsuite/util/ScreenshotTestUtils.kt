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
package com.android.tools.idea.testartifacts.instrumented.testsuite.util

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.imageio.ImageIO

private val LOG = Logger.getInstance(ScreenshotTestUtils::class.java)
const val NOT_APPLICABLE = "N/A"

/**
 * Represents the metadata of a screenshot.
 *
 * @param dimensions The dimensions of the screenshot.
 * @param size The size of the screenshot.
 * @param date The date the screenshot was taken.
 */
data class ImageMetadata(
  val dimensions: String = NOT_APPLICABLE,
  val size: String = NOT_APPLICABLE,
  val date: String = NOT_APPLICABLE,
)

object ScreenshotTestUtils {
  /**
   * Calculates the match percentage from a difference ratio.
   *
   * This method takes a difference ratio as a Double? (e.g., 0.0123 for 1.23% difference),
   * calculates the match percentage (100.0 - (difference ratio * 100.0)), and returns it as a
   * formatted string (e.g., "98.77%").
   *
   * @param diffPercent The difference ratio as a Double?, typically between 0.0 and 1.0.
   * @return The match percentage as a formatted string, or null if the input is null.
   */
  fun calculateMatchPercentage(diffPercent: Double?): String? {
    return diffPercent?.let {
      val match = 100.0 - (it * 100.0)
      "%.2f%%".format(Locale.US, match)
    }
  }

  /**
   * Asynchronously loads metadata for a given image file path.
   *
   * This function reads the image file to determine its dimensions, size, and last modified date.
   * It performs file I/O operations on a background thread.
   *
   * @param path The absolute path to the image file.
   * @return An [ImageMetadata] object containing the image's dimensions, size, and date.
   *         If the path is null, the file doesn't exist, or an error occurs, an [ImageMetadata]
   *         object with default "N/A" values is returned.
   */
  suspend fun loadImageMetadata(path: String?): ImageMetadata {
    if (path == null) return ImageMetadata()
    return withContext(Dispatchers.IO) {
      try {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return@withContext ImageMetadata()

        val size = Files.size(file.toPath())
        val attrs = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
        val lastModifiedTime = attrs.lastModifiedTime().toMillis()

        val img: BufferedImage? = ImageIO.read(file)
        val dimensions = img?.let { "${it.width}x${it.height}" } ?: NOT_APPLICABLE

        ImageMetadata(
          dimensions = dimensions,
          size = "${size / 1024} KB",
          date = SimpleDateFormat("MMM. d, yyyy", Locale.US).format(Date(lastModifiedTime)),
        )
      }
      catch (e: Exception) {
        LOG.warn("Error loading image metadata from $path", e)
        ImageMetadata()
      }
    }
  }
}