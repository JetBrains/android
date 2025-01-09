/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.backup

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.pathString

private const val FILE_HISTORY_PROPERTY = "Backup.File.History"

/** Manages the backup file history property */
internal class BackupFileHistory(private val project: Project) {

  fun getFileHistory(): List<String> {
    val value = PropertiesComponent.getInstance(project).getValue(FILE_HISTORY_PROPERTY)
    if (value.isNullOrEmpty()) {
      return emptyList()
    }
    val files = value.lines()
    val filtered = files.filterExisting().distinct()
    if (files.size != filtered.size) {
      setProperty(filtered)
    }
    return filtered
  }

  fun setFileHistory(history: List<String>) {
    setProperty(history)
  }

  private fun setProperty(value: List<String>) {
    PropertiesComponent.getInstance(project)
      .setValue(FILE_HISTORY_PROPERTY, value.joinToString("\n") { it })
  }

  private fun List<String>.filterExisting() =
    map { Path.of(it).absoluteInProject(project) }.filter { it.exists() }.map { it.pathString }
}
