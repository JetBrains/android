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
package com.android.screenshottest.util

import com.android.screenshottest.ui.PreviewDetails
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import java.io.File
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Path

/** A simple data class to pass information from the UI layer to the file management layer. */
data class ImageData(val previewData: PreviewDetails, val loadedImagePaths: Map<String, String>)

private val LOG = Logger.getInstance("com.android.screenshottest.util.ReferenceImageManager")

private fun File.toCanonicalPathOrNull(): Path? =
  try {
    canonicalFile.toPath()
  } catch (_: IOException) {
    null
  } catch (_: InvalidPathException) {
    null
  }

/**
 * Validates the security containment and directory mapping constraints for screenshot reference copying.
 *
 * This method ensures path traversal protection by canonically resolving all files and verifying:
 * 1. Both source and destination reside within appropriate safe boundaries.
 * 2. The destination file resides inside the designated module structure (under `/screenshotTest` and `/reference/` directories,
 *    sequentially) and within the project's base directory.
 * 3. The source file resides canonically within either the project's base directory or the system temporary directory.
 *
 * @param sourceFile The temporary source screenshot image file.
 * @param destinationFile The target reference screenshot image file to be updated.
 * @param basePath The canonical Path of the project's base directory.
 * @param tmpPath The canonical Path of the system temporary directory.
 * @return True if both paths satisfy all security and structural constraints; false otherwise.
 */
private fun isValidSourceAndDestination(sourceFile: File, destinationFile: File, basePath: Path, tmpPath: Path?): Boolean {
  val sourcePath = sourceFile.toCanonicalPathOrNull() ?: return false
  val destPath = destinationFile.toCanonicalPathOrNull() ?: return false

  // 1. Destination must live under the project's base directory
  if (!destPath.startsWith(basePath)) return false

  val relativeDest = basePath.relativize(destPath)
  val destSegments = relativeDest.map { it.toString() }

  // 2. Destination must live under a module's screenshotTest**/reference/ directory
  val hasValidReferenceSequence =
    (0 until destSegments.size - 1).any { i ->
      destSegments[i].startsWith("screenshottest", ignoreCase = true) && destSegments[i + 1].equals("reference", ignoreCase = true)
    }
  if (!hasValidReferenceSequence) {
    return false
  }

  // 3. Source must live canonically inside the project's base directory OR under system temporary directory
  val isUnderProjectBase = sourcePath.startsWith(basePath)
  val isUnderSystemTemp = tmpPath?.let { sourcePath.startsWith(it) } ?: false

  return isUnderProjectBase || isUnderSystemTemp
}

/**
 * Copies the images from the provided data objects to the appropriate reference image directory. This method should be called from a
 * background thread.
 *
 * @param imagesToCopy The list of data objects representing the previews to be copied.
 * @param projectBasePath The base path of the current project, used for containment validation.
 * @return A list of data objects that failed to copy.
 */
fun copyReferenceImages(imagesToCopy: List<ImageData>, projectBasePath: String): List<ImageData> {
  if (projectBasePath.isBlank()) {
    throw IllegalArgumentException("Project base path must not be empty or blank")
  }
  val basePath = File(projectBasePath).toCanonicalPathOrNull() ?: return imagesToCopy
  val tmpPath = File(System.getProperty("java.io.tmpdir")).toCanonicalPathOrNull()

  val failures = mutableListOf<ImageData>()
  val refreshRoots = mutableSetOf<File>()
  try {
    imagesToCopy.forEach { imageData ->
      val destinationPath = imageData.previewData.destImagePath
      if (destinationPath == null) {
        LOG.error("Failed to copy screenshot reference image because the destination path is not available for: ${imageData.previewData}")
        failures.add(imageData)
        return@forEach // Continue to the next item in the loop
      }

      try {
        for ((imagePath, _) in imageData.loadedImagePaths) {
          val sourceFile = File(imagePath)
          val destinationFile = File(destinationPath)
          if (!isValidSourceAndDestination(sourceFile, destinationFile, basePath, tmpPath)) {
            LOG.error(
              "Skipped copying reference image due to path security violation. " + "Source: $imagePath, Destination: $destinationPath"
            )
            failures.add(imageData)
            break
          }

          val allowedExtensions = listOf("png", "jpg", "jpeg", "webp")
          val extension = destinationFile.extension.lowercase()
          if (extension !in allowedExtensions) {
            LOG.error("Reference image destination must be a supported image file (png, jpg, jpeg, webp): $destinationFile")
            failures.add(imageData)
            break
          }

          // Identify the highest existing parent directory before creating any nested folders
          var refreshTarget = destinationFile.parentFile
          while (refreshTarget != null && !refreshTarget.exists()) {
            refreshTarget = refreshTarget.parentFile
          }

          destinationFile.parentFile?.mkdirs()
          sourceFile.copyTo(destinationFile, overwrite = true)
          LOG.info("Copied ${sourceFile.path} to ${destinationFile.path}")
          refreshTarget?.let { refreshRoots.add(it) }
        }
      } catch (e: IOException) {
        LOG.error("Failed to copy screenshot reference image due to an I/O error for: ${imageData.previewData}", e)
        failures.add(imageData)
      }
    }

    if (refreshRoots.isNotEmpty()) {
      RefreshQueue.getInstance().refreshPaths(true, true, null, refreshRoots.map { it.toPath() })
    }
  } catch (e: IllegalStateException) {
    LOG.error("Failed to copy screenshot reference images during setup due to invalid project state or configuration.", e)
    // If setup fails, all items are considered failures.
    return imagesToCopy
  }
  return failures
}
