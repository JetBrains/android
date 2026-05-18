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
package com.android.tools.idea.util

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Represents a file to be included in the zip archive.
 *
 * @property path The full system path to the source file.
 * @property name The desired name of the file within the zip archive.
 */
data class ZipData(val path: String, val name: String)

private val LOG = logger<ZipData>()

/**
 * Compresses the given files into a zip archive at the specified destination.
 *
 * @param files An array of [ZipData] objects, each representing a file to include in the zip.
 * @param destination The full system path where the resulting zip file will be created.
 * @param progressCallback An optional callback function invoked for each file after it's added to the zip, receiving the [ZipData] of the
 *   file. This can be used to report progress.
 */
fun zipFiles(files: List<ZipData>, destination: String, progressCallback: ((ZipData) -> Unit)? = null) {
  FileOutputStream(destination).use { output ->
    ZipOutputStream(output).use { zip ->
      for (zipData in files) {
        try {
          zip.putNextEntry(ZipEntry(zipData.name))
          Files.copy(Paths.get(zipData.path), zip)
        } catch (e: IOException) {
          LOG.warn("Failed to add file to zip: ${zipData.path}", e)
        } finally {
          zip.closeEntry()
        }
        progressCallback?.invoke(zipData)
      }
    }
  }
}

/**
 * A background task for compressing files into a zip archive, with progress indication.
 *
 * @param project The project associated with this task.
 * @param title The title of the background task visible to the user.
 * @property zipData A list of [ZipData] objects to be compressed.
 * @property path The destination [Path] for the output zip file.
 */
open class CompressFilesTask(project: Project?, title: String, val zipData: List<ZipData>, val path: Path) :
  Task.Backgroundable(project, title, true) {
  override fun run(indicator: ProgressIndicator) {
    indicator.fraction = 0.0
    indicator.isIndeterminate = false

    var processed = 0
    zipFiles(zipData, path.toString()) { currentFile ->
      indicator.checkCanceled()
      processed++
      indicator.fraction = processed.toDouble() / zipData.size
      indicator.text2 = "Compressing: ${currentFile.name}"
    }

    indicator.text = "Finished compressing files."
  }
}
