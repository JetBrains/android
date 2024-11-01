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
package com.android.tools.idea.res

import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.Token
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.getTokenOrNull
import com.android.tools.idea.res.ModuleRClass.SourceSet
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module

interface ResourceClassToken<P : AndroidProjectSystem> : Token {
  fun getSourceSet(projectSystem: P, module: Module): SourceSet

  companion object {
    val EP_NAME =
      ExtensionPointName<ResourceClassToken<AndroidProjectSystem>>(
        "com.android.tools.idea.res.resourceClassToken"
      )

    /** Return the most appropriate [ModuleRClass.SourceSet] for this module. */
    @JvmStatic
    fun getSourceSet(module: Module): SourceSet {
      val projectSystem = module.project.getProjectSystem()
      return projectSystem.getTokenOrNull(EP_NAME)?.getSourceSet(projectSystem, module)
        ?: SourceSet.MAIN
    }
  }
}
