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
package com.android.tools.idea.manifest

import com.android.tools.idea.manifest.ManifestClassToken.Companion.projectSystem
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.Token
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.getTokenOrNull
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module

interface ManifestClassToken<P : AndroidProjectSystem> : Token {
  fun shouldGenerateManifestLightClasses(projectSystem: P, module: Module): Boolean

  companion object {
    private const val DEFAULT_GENERATE_MANIFEST_LIGHT_CLASS = true

    val EP_NAME =
      ExtensionPointName<ManifestClassToken<AndroidProjectSystem>>(
        "com.android.tools.idea.manifest.manifestClassToken"
      )

    @JvmStatic
    fun shouldGenerateManifestLightClasses(module: Module) =
      module.projectSystem.let {
        it.getTokenOrNull(EP_NAME)?.shouldGenerateManifestLightClasses(it, module)
      } ?: DEFAULT_GENERATE_MANIFEST_LIGHT_CLASS

    private inline val Module.projectSystem: AndroidProjectSystem
      get() = project.getProjectSystem()
  }
}
