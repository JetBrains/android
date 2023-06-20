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

import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ZipData(val path: String, val name: String)

fun zipFiles(files: Array<ZipData>, destination: String) {
  FileOutputStream(destination).use { output ->
    ZipOutputStream(output).use { zip ->
      for (zipData in files) {
        zip.putNextEntry(ZipEntry(zipData.name))
        try {
          Files.copy(Paths.get(zipData.path), zip)
        }
        catch (_: IOException) {
        }
        zip.closeEntry()
      }
    }
  }
}