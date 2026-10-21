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
import com.intellij.openapi.project.Project
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LOG = Logger.getInstance(ScreenshotTestUtils::class.java)
const val NOT_APPLICABLE = "N/A"

/**
 * Represents the metadata of a screenshot.
 *
 * @param dimensions The dimensions of the screenshot.
 * @param size The size of the screenshot.
 * @param date The date the screenshot was taken.
 */
data class ImageMetadata(val dimensions: String = NOT_APPLICABLE, val size: String = NOT_APPLICABLE, val date: String = NOT_APPLICABLE)

object ScreenshotTestUtils {

  /**
   * Resolves a relative or absolute path to an absolute path based on the root project path of the given project.
   *
   * This method performs validation to prevent directory traversal and rejects network/UNC paths outright.
   *
   * @param project The IntelliJ project.
   * @param path The relative or absolute path to resolve.
   * @return The resolved absolute path if it is safely contained under the computed root path; otherwise, returns null.
   */
  @JvmStatic
  fun resolvePath(project: Project?, path: String?): String? {
    val contained = containUnderProjectRoot(project, path) ?: return null
    val ext = Paths.get(contained).fileName?.toString()?.substringAfterLast('.', "")?.lowercase()
    if (ext !in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")) return null
    return contained
  }

  @JvmStatic fun resolvePath(project: Project?, className: String?, path: String?): String? = resolvePath(project, path)

  /** Returns true if the given [path] starts with obvious network or Windows UNC prefixes, unless it is a WSL path. */
  @JvmStatic
  fun isNetworkPath(path: String?): Boolean {
    if (path.isNullOrEmpty()) return false
    val trimmed = path.trim().replace('/', '\\')
    if (trimmed.startsWith("\\\\wsl$\\", ignoreCase = true) || trimmed.startsWith("\\\\wsl.localhost\\", ignoreCase = true)) {
      return false
    }
    return trimmed.startsWith("\\\\")
  }

  /**
   * Normalises [raw] and returns it as an absolute path string only if the result is contained under the given [project]'s base directory;
   * otherwise returns null.
   *
   * Used to prevent externally-supplied path strings from escaping the project root or addressing network locations.
   */
  @JvmStatic
  fun containUnderProjectRoot(project: Project?, raw: String?): String? {
    if (raw.isNullOrEmpty()) return null
    val base = project?.basePath ?: return null
    val rootPathNio =
      try {
        File(base).canonicalFile.toPath()
      } catch (_: IOException) {
        return null
      } catch (_: InvalidPathException) {
        return null
      }
    if (isNetworkPath(raw)) return null
    return try {
      val resolvedPathNio = Paths.get(base).resolve(raw)
      val resolvedCanonical = resolvedPathNio.toFile().canonicalFile.toPath()
      if (resolvedCanonical.startsWith(rootPathNio)) {
        resolvedCanonical.toString()
      } else {
        null
      }
    } catch (_: IOException) {
      null
    } catch (_: InvalidPathException) {
      null
    }
  }

  /**
   * Calculates the match percentage from a difference ratio.
   *
   * This method takes a difference ratio as a Double? (e.g., 0.0123 for 1.23% difference), calculates the match percentage (100.0 -
   * (difference ratio * 100.0)), and returns it as a formatted string (e.g., "98.77%").
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
   * This function reads the image file to determine its dimensions, size, and last modified date. It performs file I/O operations on a
   * background thread.
   *
   * @param path The absolute path to the image file.
   * @return An [ImageMetadata] object containing the image's dimensions, size, and date. If the path is null, the file doesn't exist, or an
   *   error occurs, an [ImageMetadata] object with default "N/A" values is returned.
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
      } catch (e: Exception) {
        LOG.warn("Error loading image metadata from $path", e)
        ImageMetadata()
      }
    }
  }
}
