/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.editors.sqlite

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile

import javax.swing.*

object SqliteFileType : FileType {

  override fun getName(): String = "SQLite"

  override fun getDescription(): String = "Android SQLite database"

  override fun getDefaultExtension(): String = ""

  override fun getIcon(): Icon? = AllIcons.Providers.Sqlite

  override fun isBinary(): Boolean = true

  override fun isReadOnly(): Boolean = true

  override fun getCharset(file: VirtualFile, content: ByteArray): String? = null
}
