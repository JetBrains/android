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

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.INativeFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

/** The [com.intellij.openapi.fileTypes.FileType] used to identify SQLite files. */
object SqliteFileType : INativeFileType {
  override fun getDefaultExtension() = "db"

  override fun getIcon(): Icon = AllIcons.Providers.Sqlite

  override fun useNativeIcon() = false

  override fun getName() = "SQLite"

  override fun getDescription() = "Android SQLite database"

  override fun isBinary() = true

  override fun openFileInAssociatedApplication(project: Project, file: VirtualFile): Boolean {
    // open file in Database Inspector
    return true
  }
}
