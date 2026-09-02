/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.profilers

import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.Token
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.getTokenOrNull
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresReadLock
import java.nio.file.Path

interface ProfilerR8MappingToken<P : AndroidProjectSystem> : Token {
  @RequiresReadLock fun getR8Mappings(projectSystem: P): List<R8Mapping>

  data class R8Mapping(
    val text: Path,
    val applicationId: String? = null,
    val isSelected: Boolean = false,
  )

  companion object {
    val EP_NAME =
      ExtensionPointName<ProfilerR8MappingToken<AndroidProjectSystem>>("com.android.tools.idea.profilers.profilerR8MappingToken")

    /** Return list of R8 mappings for the given project. Some files may not exist if user did not build corresponding variant. */
    @RequiresReadLock
    @JvmStatic
    fun getR8Mappings(project: Project): List<R8Mapping> {
      if (project.isDisposed) {
        return emptyList()
      }
      val projectSystem = project.getProjectSystem()
      return projectSystem.getTokenOrNull(EP_NAME)?.getR8Mappings(projectSystem) ?: emptyList()
    }
  }
}
