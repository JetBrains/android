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

// TODO merge
//import com.android.screenshottest.ScreenshotTestBuildSystemAdapter
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.Locale

/**
 * A simple data class to pass information from the UI layer to the file management layer.
 */
data class ImageData(
  val previewData: PreviewDetails,
  val loadedImagePaths: Map<String, String>
)

private val LOG = Logger.getInstance("com.android.screenshottest.util.ReferenceImageManager")
private const val SCREENSHOT_TEST_ROOT = "src/screenshotTest"
private const val REFERENCE_SUBDIRECTORY = "reference"

/**
 * Copies the images from the provided data objects to the appropriate reference image directory.
 * This method should be called from a background thread.
 *
 * @param module The module where the screenshots belong.
 * @param imagesToCopy The list of data objects representing the previews to be copied.
 * @return A list of data objects that failed to copy.
 */
fun copyReferenceImages(module: Module, imagesToCopy: List<ImageData>): List<ImageData> {
  // TODO merge
  return TODO()
  //val failures = mutableListOf<ImageData>()
  //try {
  //  val projectSystem = ScreenshotTestBuildSystemAdapter.EP_NAME.extensionList.firstOrNull()
  //    ?: throw IllegalStateException("ScreenshotTestBuildSystemAdapter extension not found.")
  //  val variantName = projectSystem.getSelectedVariantName(module)
  //    ?: throw IllegalStateException("Variant name not found")
  //  val modulePathStr = projectSystem.getLinkedExternalProjectPath(module)
  //    ?: throw IllegalStateException("Could not determine module project path.")
  //
  //  val referenceRoot = getReferenceImageRoot(File(modulePathStr), variantName)
  //
  //  imagesToCopy.forEach { imageData ->
  //    try {
  //      imageData.loadedImagePaths.forEach { (imagePath, simpleClassName) ->
  //        val sourceFile = File(imagePath)
  //        val destinationFile = referenceRoot.resolve(simpleClassName).resolve(sourceFile.name).toFile()
  //        destinationFile.parentFile.mkdirs()
  //        sourceFile.copyTo(destinationFile, overwrite = true)
  //        LOG.info("Copied ${sourceFile.path} to ${destinationFile.path}")
  //      }
  //    } catch (e: IOException) {
  //      LOG.error("Failed to copy screenshot reference image due to an I/O error for: ${imageData.previewData}", e)
  //      failures.add(imageData)
  //    }
  //  }
  //
  //  LocalFileSystem.getInstance().refreshIoFiles(listOf(referenceRoot.toFile()), true, true, null)
  //} catch (e: IllegalStateException) {
  //  LOG.error("Failed to copy screenshot reference images during setup due to invalid project state or configuration.", e)
  //  // If setup fails, all items are considered failures.
  //  return imagesToCopy
  //}
  //return failures
}

/**
 * Constructs the root path for reference images based on the module and variant.
 * e.g., .../app/src/screenshotTestDebug/reference
 */
private fun getReferenceImageRoot(modulePath: File, variantName: String): Path {
  val capitalizedVariant = variantName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
  return modulePath.toPath().resolve("$SCREENSHOT_TEST_ROOT$capitalizedVariant/$REFERENCE_SUBDIRECTORY")
}