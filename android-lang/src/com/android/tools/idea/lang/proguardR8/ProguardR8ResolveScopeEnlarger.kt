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
package com.android.tools.idea.lang.proguardR8

import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.projectsystem.isHolderModule
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.ResolveScopeEnlarger
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope

/**
 * Adds a main module to resolve scope of [ProguardR8FileType] files located in holder module.
 */
class ProguardR8ResolveScopeEnlarger : ResolveScopeEnlarger() {
  override fun getAdditionalResolveScope(file: VirtualFile, project: Project): SearchScope? {
    if (file.fileType == ProguardR8FileType.INSTANCE) {
      val module = ModuleUtil.findModuleForFile(file, project)?.takeIf { it.isHolderModule() } ?: return null

      return GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module.getMainModule())
    }

    return null
  }
}