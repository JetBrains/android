/*
 * Copyright (C) 2022 The Android Open Source Project
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
@file:JvmName("BuildOutputUtil")

package com.android.tools.idea.gradle.util

import com.android.ide.common.build.GenericBuiltArtifacts
import com.android.ide.common.build.GenericBuiltArtifactsLoader.loadFromFile
import com.android.tools.idea.gradle.model.IdeBuildTasksAndOutputInformation
import com.android.tools.idea.gradle.model.IdeVariantBuildInformation
import com.android.tools.idea.log.LogWrapper
import com.intellij.openapi.diagnostic.Logger
import java.io.File

/**
 * Utility methods to find APK/Bundle output file or folder.
 */

private val LOG: Logger get() = Logger.getInstance("BuildOutputUtil.kt")

enum class OutputType {
  Apk,
  ApkFromBundle,
  Bundle
}

fun getOutputFilesFromListingFile(listingFile: String): List<File> {
  val builtArtifacts = loadFromFile(File(listingFile), LogWrapper(LOG))
  if (builtArtifacts != null) {
    val items = builtArtifacts.elements.map { File(it.outputFile) }
    // NOTE: These strings come from com.android.build.api.artifact.ArtifactKind.DIRECTORY and alike.
    return if (builtArtifacts.elementType == null || builtArtifacts.elementType == "Directory") {
      items.flatMap { fileOrDirectory ->
        runCatching {
          if (fileOrDirectory.isDirectory) fileOrDirectory.listFiles()?.toList().orEmpty()
          else listOf(fileOrDirectory)
        }
          .getOrElse { e ->
            LOG.warn("Error reading list of APK files from build output directory '$fileOrDirectory'.", e)
            emptyList()
          }
      }
    }
    else {
      items
    }
  }
  // Couldn't read from build output listings file, this could be because a build hasn't yet been completed
  return emptyList()
}

fun Collection<IdeVariantBuildInformation>.variantOutputInformation(variantName: String): IdeBuildTasksAndOutputInformation? {
  return firstOrNull { it.variantName == variantName }?.buildInformation
}

fun IdeBuildTasksAndOutputInformation.getOutputListingFileOrLogError(outputType: OutputType): String? {
  return getOutputListingFile(outputType)
    .also {
      if (it == null) {
        LOG.error(Throwable("Output listing build file is not available for output type $outputType in $this"))
      }
    }
}

fun IdeBuildTasksAndOutputInformation.getOutputListingFile(outputType: OutputType) =
  when (outputType) {
    OutputType.Apk -> assembleTaskOutputListingFile
    OutputType.ApkFromBundle -> apkFromBundleTaskOutputListingFile
    else -> bundleTaskOutputListingFile
  }

fun loadBuildOutputListingFile(listingFile: String): GenericBuiltArtifacts? {
  val builtArtifacts = loadFromFile(File(listingFile), LogWrapper(LOG))
  if (builtArtifacts != null) {
    return builtArtifacts
  }

  // Couldn't read from build output listings file, this could be because a build hasn't yet been completed
  return null
}

data class GenericBuiltArtifactsWithTimestamp(val genericBuiltArtifacts: GenericBuiltArtifacts?, val timeStamp: Long) {
  companion object {
    @JvmStatic
    fun mostRecentNotNull(vararg items: GenericBuiltArtifactsWithTimestamp?): GenericBuiltArtifactsWithTimestamp? =
      items.filterNotNull().maxByOrNull { it.timeStamp }
  }
}
