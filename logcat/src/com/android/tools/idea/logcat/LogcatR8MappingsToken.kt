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
package com.android.tools.idea.logcat

import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.Token
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.getTokenOrNull
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import java.nio.file.Path

interface LogcatR8MappingsToken<P : AndroidProjectSystem> : Token {
  fun getR8Mappings(projectSystem: P): List<R8Mappings>

  data class R8Mappings(val text: Path, val partitioned: Path?)

  companion object {
    val EP_NAME =
      ExtensionPointName<LogcatR8MappingsToken<AndroidProjectSystem>>(
        "com.android.tools.idea.logcat.logcatR8MappingToken"
      )

    /**
     * Return list of R8 mappings. Some files may not exist if user did not build corresponding
     * variant.
     */
    @JvmStatic
    fun getR8Mappings(project: Project): List<R8Mappings> {
      val projectSystem = project.getProjectSystem()
      return projectSystem.getTokenOrNull(EP_NAME)?.getR8Mappings(projectSystem) ?: listOf()
    }
  }
}
