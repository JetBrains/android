/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.sqlite.fileType

import com.android.tools.idea.sqlite.DatabaseInspectorFlagController
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.util.io.ByteSequence
import com.intellij.openapi.vfs.VirtualFile

/**
 * Implementation of [FileTypeRegistry.FileTypeDetector] for Sqlite files.
 *
 * If starting by sequence of bytes of a file is the recognized Sqlite header
 * [SQLITE3_FORMAT_HEADER], the implementation returns the [SqliteFileType] file type.
 */
class SqliteFileTypeDetector : FileTypeRegistry.FileTypeDetector {
  private val SQLITE3_FORMAT_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.UTF_8)

  override fun detect(
    file: VirtualFile,
    firstBytes: ByteSequence,
    firstCharsIfText: CharSequence?
  ): FileType? {
    return when {
      !DatabaseInspectorFlagController.isOpenFileEnabled -> null
      firstBytes.length() < SQLITE3_FORMAT_HEADER.size -> null
      firstBytes.subSequence(0, SQLITE3_FORMAT_HEADER.size).toBytes() contentEquals
        SQLITE3_FORMAT_HEADER -> SqliteFileType
      else -> null
    }
  }

  override fun getVersion(): Int = 1
}
