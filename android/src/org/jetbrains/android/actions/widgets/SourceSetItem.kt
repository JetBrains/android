/*
 * Copyright (C) 2020 The Android Open Source Project
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
package org.jetbrains.android.actions.widgets

import com.android.tools.idea.projectsystem.AndroidProjectRootUtil
import com.android.tools.idea.projectsystem.NamedIdeaSourceProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.PathUtil

/**
 * Represents an item intended for a SourceSet ComboBox.
 */
class SourceSetItem private constructor(
  val sourceSetName: String,
  val resDirUrl: String,
  val displayableResDir: String
) {
  companion object {
    /**
     * Helper function to safely create a [SourceSetItem] instance.
     */
    @JvmStatic
    fun create(sourceProvider: NamedIdeaSourceProvider, module: Module, resDirUrl: String): SourceSetItem {
      val resDirPath = PathUtil.toPresentableUrl(resDirUrl)
      val modulePath = AndroidProjectRootUtil.getModuleDirPath(module)
      val relativeResourceUrl =
        modulePath?.let {
          FileUtil.getRelativePath(modulePath, resDirPath, '/')?.replaceFirst("(\\.\\./)+".toRegex(), "")
        }
      val displayableResDir = StringUtil.last(
        relativeResourceUrl ?: resDirPath,
        30,
        true).toString()
      return SourceSetItem(sourceSetName = sourceProvider.name, resDirUrl = resDirUrl, displayableResDir = displayableResDir)
    }
  }
}