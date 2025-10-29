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
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import java.io.IOException

/**
 * A simple data class to pass information from the UI layer to the file management layer.
 */
data class ImageData(
  val previewData: PreviewDetails,
  val loadedImagePaths: Map<String, String>
)

private val LOG = Logger.getInstance("com.android.screenshottest.util.ReferenceImageManager")

/**
 * Copies the images from the provided data objects to the appropriate reference image directory.
 * This method should be called from a background thread.
 *
 * @param module The module where the screenshots belong.
 * @param imagesToCopy The list of data objects representing the previews to be copied.
 * @return A list of data objects that failed to copy.
 */
fun copyReferenceImages(imagesToCopy: List<ImageData>): List<ImageData> {
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
        imageData.loadedImagePaths.forEach { (imagePath, _) ->
          val sourceFile = File(imagePath)
          val destinationFile = File(destinationPath)
          destinationFile.parentFile.mkdirs()
          sourceFile.copyTo(destinationFile, overwrite = true)
          LOG.info("Copied ${sourceFile.path} to ${destinationFile.path}")
          destinationFile.parentFile?.let { refreshRoots.add(it) }
        }
      } catch (e: IOException) {
        LOG.error("Failed to copy screenshot reference image due to an I/O error for: ${imageData.previewData}", e)
        failures.add(imageData)
      }
    }

    if (refreshRoots.isNotEmpty()) {
      LocalFileSystem.getInstance().refreshIoFiles(refreshRoots, true, true, null)
    }
  } catch (e: IllegalStateException) {
    LOG.error("Failed to copy screenshot reference images during setup due to invalid project state or configuration.", e)
    // If setup fails, all items are considered failures.
    return imagesToCopy
  }
  return failures
}